package com.chatagg.model;

import java.time.Instant;

public class ReviewItem {

    private long id;
    private String type;
    private long telegramMessageId;
    private Instant messageDate;
    private String rawText;
    private String ocrText;
    private Float ocrConfidence;
    private String photoPath;
    private Long suggestedBookId;
    private String suggestedBookTitle;

    public ReviewItem() {
    }

    public ReviewItem(long id, String type, long telegramMessageId, Instant messageDate,
                      String rawText, String ocrText, Float ocrConfidence, String photoPath,
                      Long suggestedBookId, String suggestedBookTitle) {
        this.id = id;
        this.type = type;
        this.telegramMessageId = telegramMessageId;
        this.messageDate = messageDate;
        this.rawText = rawText;
        this.ocrText = ocrText;
        this.ocrConfidence = ocrConfidence;
        this.photoPath = photoPath;
        this.suggestedBookId = suggestedBookId;
        this.suggestedBookTitle = suggestedBookTitle;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTelegramMessageId() {
        return telegramMessageId;
    }

    public void setTelegramMessageId(long telegramMessageId) {
        this.telegramMessageId = telegramMessageId;
    }

    public Instant getMessageDate() {
        return messageDate;
    }

    public void setMessageDate(Instant messageDate) {
        this.messageDate = messageDate;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
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

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public Long getSuggestedBookId() {
        return suggestedBookId;
    }

    public void setSuggestedBookId(Long suggestedBookId) {
        this.suggestedBookId = suggestedBookId;
    }

    public String getSuggestedBookTitle() {
        return suggestedBookTitle;
    }

    public void setSuggestedBookTitle(String suggestedBookTitle) {
        this.suggestedBookTitle = suggestedBookTitle;
    }
}
