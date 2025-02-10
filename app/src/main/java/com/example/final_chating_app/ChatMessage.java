package com.example.final_chating_app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import android.Manifest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String TAG = "ChatActivity";
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private EditText messageEditText;
    private Button sendButton;
    private Button exitButton;
    private DatabaseReference databaseReference;
    private String nickname;
    private Button videoButton;
    private static final int REQUEST_VIDEO_PICK = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_chat);

            nickname = getIntent().getStringExtra("NICKNAME");
            if (nickname == null || nickname.isEmpty()) {
                Log.e(TAG, "Nickname is null or empty");
                Toast.makeText(this, "닉네임 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            recyclerView = findViewById(R.id.recyclerView);
            messageEditText = findViewById(R.id.messageEditText);
            sendButton = findViewById(R.id.sendButton);
            exitButton = findViewById(R.id.exitButton);
            videoButton = findViewById(R.id.videoButton);

            if (recyclerView == null || messageEditText == null || sendButton == null || exitButton == null) {
                Log.e(TAG, "One or more views are null");
                Toast.makeText(this, "UI 요소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            chatMessages = new ArrayList<>();
            chatAdapter = new ChatAdapter(chatMessages);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(chatAdapter);

            try {
                databaseReference = FirebaseDatabase.getInstance().getReference("messages");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase", e);
                Toast.makeText(this, "Firebase 초기화 오류", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            sendButton.setOnClickListener(v -> sendMessage());
            exitButton.setOnClickListener(v -> finish());
            videoButton.setOnClickListener(v -> openVideoPicker());

            databaseReference.addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    try {
                        ChatMessage message = dataSnapshot.getValue(ChatMessage.class);
                        if (message != null) {
                            chatMessages.add(message);
                            chatAdapter.notifyDataSetChanged();
                            recyclerView.scrollToPosition(chatMessages.size() - 1);

                            // 유지할 메시지 수
                            if (chatMessages.size() > 100) {
                                String oldestMessageKey = dataSnapshot.getKey();
                                if (oldestMessageKey != null) {
                                    databaseReference.child(oldestMessageKey).removeValue();
                                    chatMessages.remove(0);
                                    chatAdapter.notifyItemRemoved(0);
                                }
                            }
                        } else {
                            Log.w(TAG, "Message is null for dataSnapshot: " + dataSnapshot.getKey());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing new message", e);
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {}

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Database error: " + databaseError.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "앱 초기화 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한이 승인되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "권한이 거부되었습니다. 동영상을 선택할 수 없습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void sendMessage() {
        try {
            String messageText = messageEditText.getText().toString().trim();
            if (!messageText.isEmpty()) {
                ChatMessage chatMessage = new ChatMessage(nickname, messageText);
                databaseReference.push().setValue(chatMessage)
                        .addOnSuccessListener(aVoid -> {
                            messageEditText.setText("");
                            Log.d(TAG, "Message sent successfully");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to send message", e);
                            Toast.makeText(ChatActivity.this, "메시지 전송 실패", Toast.LENGTH_SHORT).show();
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            Toast.makeText(this, "메시지 전송 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    private void sendMessage(String messageText) {
        try {
            if (!messageText.isEmpty()) {
                ChatMessage chatMessage = new ChatMessage(nickname, messageText);
                databaseReference.push().setValue(chatMessage)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Message sent successfully");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to send message", e);
                            Toast.makeText(ChatActivity.this, "메시지 전송 실패", Toast.LENGTH_SHORT).show();
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            Toast.makeText(this, "메시지 전송 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openVideoPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(Intent.createChooser(intent, "비디오를 선택하세요"), REQUEST_VIDEO_PICK);
        } catch (Exception e) {
            Log.e(TAG, "Error opening video picker", e);
            Toast.makeText(this, "비디오 선택 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            Log.d(TAG, "onActivityResult - requestCode: " + requestCode +
                    ", resultCode: " + resultCode + ", data: " + data);

            if (requestCode == REQUEST_VIDEO_PICK) {
                if (resultCode == RESULT_OK && data != null) {
                    Uri selectedVideoUri = data.getData();
                    Log.d(TAG, "Selected Video URI: " + selectedVideoUri);

                    if (selectedVideoUri != null) {
                        String mimeType = getContentResolver().getType(selectedVideoUri);
                        Log.d(TAG, "Selected Video MimeType: " + mimeType);

                        if (mimeType != null && mimeType.startsWith("video/")) {
                            uploadVideoToServer(selectedVideoUri);
                        } else {
                            Toast.makeText(this, "올바른 비디오 파일을 선택해주세요.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Selected video URI is null");
                        Toast.makeText(this, "선택된 비디오 URI가 null입니다.",
                                Toast.LENGTH_SHORT).show();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "Video selection was canceled");
                    sendMessage("동영상 전송이 취소되었습니다.");  // 메시지 전송
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onActivityResult", e);
            Toast.makeText(this, "비디오 선택 후 처리 중 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadVideoToServer(Uri videoUri) {
        try {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("동영상 업로드 중...");
            progressDialog.show();

            InputStream inputStream = getContentResolver().openInputStream(videoUri);
            if (inputStream == null) {
                Toast.makeText(this, "비디오 파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                sendMessage("동영상 전송이 취소되었습니다.");  // 실패 메시지 전송
                progressDialog.dismiss();
                return;
            }

            byte[] videoData = getBytes(inputStream);

            RequestBody requestFile = RequestBody.create(
                    MediaType.parse("video/*"), videoData
            );

            MultipartBody.Part videoPart = MultipartBody.Part.createFormData(
                    "video", "test.mp4", requestFile
            );

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://192.168.45.94:80/") //변경필요!
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            VideoUploadService service = retrofit.create(VideoUploadService.class);
            Call<UploadResponse> call = service.uploadVideo(videoPart);

            call.enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    progressDialog.dismiss();
                    if (response.isSuccessful() && response.body() != null) {
                        handleUploadResult(response.body());
                    } else {
                        Toast.makeText(ChatActivity.this, "서버 오류 발생", Toast.LENGTH_SHORT).show();
                        sendMessage("동영상 전송이 취소되었습니다.");  // 서버 오류 시 메시지 전송
                    }
                }

                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    progressDialog.dismiss();
                    Toast.makeText(ChatActivity.this, "업로드 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    sendMessage("동영상 전송이 취소되었습니다.");  // 네트워크 실패 시 메시지 전송
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "동영상 업로드 중 오류 발생: " + e.getMessage(), Toast.LENGTH_LONG).show();
            sendMessage("동영상 전송이 취소되었습니다.");  // 예외 발생 시 메시지 전송
            e.printStackTrace();
        }
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }


    private void handleUploadResult(UploadResponse response) {
        try {
            Log.d(TAG, "Upload response: " + response.toString());

            if (response.isObjectDetected()) {
                sendMessage("유해물이 감지되어 동영상 전송이 취소되었습니다.");
            } else if (response.getVideoUrl() != null && !response.getVideoUrl().isEmpty()) {
                sendVideoToChat(response.getVideoUrl());
            } else {
                sendMessage("동영상 전송이 취소되었습니다.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling upload result", e);
            sendMessage("동영상 전송이 취소되었습니다.");
        }
    }




    private void sendVideoToChat(String videoUrl) {
        try {
            // 동영상 URL을 채팅방에 메시지로 전송
            ChatMessage videoMessage = new ChatMessage(nickname, "동영상 링크: " + videoUrl);
            databaseReference.push().setValue(videoMessage)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Video message sent successfully");
                        Toast.makeText(ChatActivity.this, "동영상이 채팅방에 전송되었습니다.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send video message", e);
                        Toast.makeText(ChatActivity.this, "비디오 메시지 전송 실패", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sending video message", e);
            Toast.makeText(this, "비디오 메시지 전송 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    private String getPathFromUri(Uri uri) {
        String path = null;
        String[] projection = { MediaStore.MediaColumns.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                path = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return path != null ? path : uri.getPath();
    }
}