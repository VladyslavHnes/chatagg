package com.chatagg.model;

import java.time.Instant;

public class Author {

    private long id;
    private String name;
    private String country;
    private String wikidataId;
    private String openlibraryId;
    private Instant createdAt;

    public Author() {
    }

    public Author(long id, String name, String country, String wikidataId,
                  String openlibraryId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.wikidataId = wikidataId;
        this.openlibraryId = openlibraryId;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String wikidataId) {
        this.wikidataId = wikidataId;
    }

    public String getOpenlibraryId() {
        return openlibraryId;
    }

    public void setOpenlibraryId(String openlibraryId) {
        this.openlibraryId = openlibraryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
