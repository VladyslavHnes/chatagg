package com.chatagg.model;

import java.time.Instant;

public class Photo {

    private long id;
    private String telegramFileId;
    private String localPath;
    private String ocrText;
    private Float ocrConfidence;
    private Instant createdAt;

    public Photo() {
    }

    public Photo(long id, String telegramFileId, String localPath, String ocrText,
                 Float ocrConfidence, Instant createdAt) {
        this.id = id;
        this.telegramFileId = telegramFileId;
        this.localPath = localPath;
        this.ocrText = ocrText;
        this.ocrConfidence = ocrConfidence;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTelegramFileId() {
        return telegramFileId;
    }

    public void setTelegramFileId(String telegramFileId) {
        this.telegramFileId = telegramFileId;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public Float getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(Float ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
