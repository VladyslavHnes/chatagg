package com.chatagg.model;

import java.time.Instant;

public class Quote {

    private long id;
    private Long bookId;
    private String textContent;
    private String sourceType;
    private long telegramMessageId;
    private Instant telegramMessageDate;
    private Float ocrConfidence;
    private Long photoId;
    private String reviewStatus = "approved";
    private Instant createdAt;

    public Quote() {
    }

    public Quote(long id, Long bookId, String textContent, String sourceType,
                 long telegramMessageId, Instant telegramMessageDate, Float ocrConfidence,
                 Long photoId, String reviewStatus, Instant createdAt) {
        this.id = id;
        this.bookId = bookId;
        this.textContent = textContent;
        this.sourceType = sourceType;
        this.telegramMessageId = telegramMessageId;
        this.telegramMessageDate = telegramMessageDate;
        this.ocrConfidence = ocrConfidence;
        this.photoId = photoId;
        this.reviewStatus = reviewStatus;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public long getTelegramMessageId() {
        return telegramMessageId;
    }

    public void setTelegramMessageId(long telegramMessageId) {
        this.telegramMessageId = telegramMessageId;
    }

    public Instant getTelegramMessageDate() {
        return telegramMessageDate;
    }

    public void setTelegramMessageDate(Instant telegramMessageDate) {
        this.telegramMessageDate = telegramMessageDate;
    }

    public Float getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(Float ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
