package com.example.final_chating_app;

public class UploadResponse {
    private boolean objectDetected; // 객체 감지 여부
    private String videoUrl; // 업로드된 동영상의 URL

    // Getter 메서드
    public boolean isObjectDetected() {
        return objectDetected;
    }

    public String getVideoUrl() {
        return videoUrl;
    }
    @Override
    public String toString() {
        return "UploadResponse{" +
                "objectDetected=" + objectDetected +
                ", videoUrl='" + videoUrl + '\'' +
                '}';
    }

}