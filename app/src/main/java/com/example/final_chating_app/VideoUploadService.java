package com.example.final_chating_app;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface VideoUploadService {
    @Multipart
    @POST("upload") //
    Call<UploadResponse> uploadVideo(@Part MultipartBody.Part video);
}