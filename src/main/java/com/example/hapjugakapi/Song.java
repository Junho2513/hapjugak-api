package com.example.hapjugakapi;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String artist;
    private int totalParts;
    private int filledParts;

    public Song() {}

    public Song(String title, String artist, int totalParts, int filledParts) {
        this.title = title;
        this.artist = artist;
        this.totalParts = totalParts;
        this.filledParts = filledParts;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getTotalParts() { return totalParts; }
    public int getFilledParts() { return filledParts; }

    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setTotalParts(int totalParts) { this.totalParts = totalParts; }
    public void setFilledParts(int filledParts) { this.filledParts = filledParts; }

    public boolean isDone() { return filledParts == totalParts; }
    public int getRemaining() { return totalParts - filledParts; }
}