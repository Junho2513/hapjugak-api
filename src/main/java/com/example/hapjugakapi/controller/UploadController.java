package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.PresignRequest;
import com.example.hapjugakapi.dto.PresignResponse;
import com.example.hapjugakapi.service.StorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    /** 로그인한 사용자만 서명을 받을 수 있다 (SecurityConfig의 anyRequest().authenticated()). */
    @PostMapping("/presign")
    public PresignResponse presign(@RequestBody PresignRequest request) {
        return storageService.presign(request);
    }
}