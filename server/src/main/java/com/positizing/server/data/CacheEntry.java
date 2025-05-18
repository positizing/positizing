package com.positizing.server.data;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;

/**
 * CacheEntry:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 8:45 p.m.
 */
@Entity
@NamedQuery(name = "CacheEntry.findByPrompt", query = "FROM CacheEntry WHERE prompt = ?1")
public class CacheEntry extends PanacheEntity {

    @Column(unique = true, length = 1024)
    public String prompt;

    @Column(length = 4096)
    public String response;

    public CacheEntry() {}

    public CacheEntry(String prompt, String response) {
        this.prompt = prompt;
        this.response = response;
    }
}