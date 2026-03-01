package com.chatagg.model;

import java.time.Instant;

public class TelegramMessage {

    private long id;
    private long telegramMessageId;
    private long chatId;
    private Instant messageDate;
    private String messageType;
    private String rawText;
    private String processingStatus = "pending";
    private Instant processedAt;

    public TelegramMessage() {
    }

    public TelegramMessage(long id, long telegramMessageId, long chatId, Instant messageDate,
                           String messageType, String rawText, String processingStatus,
                           Instant processedAt) {
        this.id = id;
        this.telegramMessageId = telegramMessageId;
        this.chatId = chatId;
        this.messageDate = messageDate;
        this.messageType = messageType;
        this.rawText = rawText;
        this.processingStatus = processingStatus;
        this.processedAt = processedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTelegramMessageId() {
        return telegramMessageId;
    }

    public void setTelegramMessageId(long telegramMessageId) {
        this.telegramMessageId = telegramMessageId;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public Instant getMessageDate() {
        return messageDate;
    }

    public void setMessageDate(Instant messageDate) {
        this.messageDate = messageDate;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
