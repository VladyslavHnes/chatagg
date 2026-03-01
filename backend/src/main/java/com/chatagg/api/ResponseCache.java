package com.chatagg.api;

import java.util.concurrent.ConcurrentHashMap;

public class ResponseCache {

    private record CacheEntry(Object value, long expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public ResponseCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            if (entry != null) cache.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(String key, Object value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMs));
    }

    public void invalidateAll() {
        cache.clear();
    }
}
