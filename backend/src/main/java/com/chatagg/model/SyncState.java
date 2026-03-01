package com.chatagg.model;

import java.time.Instant;

public class SyncState {

    private long id;
    private long channelChatId;
    private Long lastMessageId;
    private Instant lastSyncAt;
    private long totalMessagesProcessed;

    public SyncState() {
    }

    public SyncState(long id, long channelChatId, Long lastMessageId, Instant lastSyncAt,
                     long totalMessagesProcessed) {
        this.id = id;
        this.channelChatId = channelChatId;
        this.lastMessageId = lastMessageId;
        this.lastSyncAt = lastSyncAt;
        this.totalMessagesProcessed = totalMessagesProcessed;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getChannelChatId() {
        return channelChatId;
    }

    public void setChannelChatId(long channelChatId) {
        this.channelChatId = channelChatId;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(Long lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public long getTotalMessagesProcessed() {
        return totalMessagesProcessed;
    }

    public void setTotalMessagesProcessed(long totalMessagesProcessed) {
        this.totalMessagesProcessed = totalMessagesProcessed;
    }
}
