package com.example.hapjugakapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SongApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이 곡을 등록하면 401")
    void 비인증_등록_차단() throws Exception {
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "몰래 올리는 곡",
                                  "artist": "해커",
                                  "parts": ["VOCAL"]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    @DisplayName("조회는 토큰 없이도 된다")
    void 비인증_조회_허용() throws Exception {
        mockMvc.perform(get("/songs/candidates"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("내 정보는 토큰 없이 볼 수 없다")
    void 비인증_내정보_차단() throws Exception {
        mockMvc.perform(get("/members/me"))
                .andExpect(status().isUnauthorized());
    }
}