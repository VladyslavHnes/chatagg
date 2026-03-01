package com.chatagg.model;

import java.time.Instant;

public class Book {

    private long id;
    private String title;
    private String reviewNote;
    private String impression;
    private String genre;
    private Instant announcementDate;
    private long telegramMessageId;
    private String coverPhotoPath;
    private String metadataSource;
    private Instant createdAt;
    private Instant updatedAt;

    public Book() {
    }

    public Book(long id, String title, String reviewNote, String genre,
                Instant announcementDate,
                long telegramMessageId, String coverPhotoPath, String metadataSource,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.reviewNote = reviewNote;
        this.genre = genre;
        this.announcementDate = announcementDate;
        this.telegramMessageId = telegramMessageId;
        this.coverPhotoPath = coverPhotoPath;
        this.metadataSource = metadataSource;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getImpression() {
        return impression;
    }

    public void setImpression(String impression) {
        this.impression = impression;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Instant getAnnouncementDate() {
        return announcementDate;
    }

    public void setAnnouncementDate(Instant announcementDate) {
        this.announcementDate = announcementDate;
    }

    public long getTelegramMessageId() {
        return telegramMessageId;
    }

    public void setTelegramMessageId(long telegramMessageId) {
        this.telegramMessageId = telegramMessageId;
    }

    public String getCoverPhotoPath() {
        return coverPhotoPath;
    }

    public void setCoverPhotoPath(String coverPhotoPath) {
        this.coverPhotoPath = coverPhotoPath;
    }

    public String getMetadataSource() {
        return metadataSource;
    }

    public void setMetadataSource(String metadataSource) {
        this.metadataSource = metadataSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
