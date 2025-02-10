package com.example.final_chating_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.final_chating_app.ChatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText nicknameEditText;
    private Button enterChatButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nicknameEditText = findViewById(R.id.nicknameEditText);
        enterChatButton = findViewById(R.id.enterChatButton);

        enterChatButton.setOnClickListener(v -> {
            String nickname = nicknameEditText.getText().toString().trim();
            if (!nickname.isEmpty()) {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra("NICKNAME", nickname);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}