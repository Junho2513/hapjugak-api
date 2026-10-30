package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.domain.Song;
import org.springframework.web.bind.annotation.*;
import com.example.hapjugakapi.repository.SongRepository;

import java.util.List;

@RestController
@RequestMapping("/songs")
public class SongController {

    private final SongRepository songRepository;

    public SongController(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @GetMapping
    public List<Song> getSongs() {
        return songRepository.findAll();
    }

    @GetMapping("/{id}")
    public Song getSong(@PathVariable Long id) {
        return songRepository.findById(id).orElseThrow();
    }

    @PostMapping
    public Song addSong(@RequestBody Song song) {
        return songRepository.save(song);
    }

    @DeleteMapping("/{id}")
    public void deleteSong(@PathVariable Long id) {
        songRepository.deleteById(id);
    }
}