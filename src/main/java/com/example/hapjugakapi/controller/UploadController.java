package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.PresignRequest;
import com.example.hapjugakapi.dto.PresignResponse;
import com.example.hapjugakapi.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이미지 업로드", description = "S3 에 직접 올리기 위한 서명을 받습니다")
@RestController
@RequestMapping("/uploads")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    /** 로그인한 사용자만 서명을 받을 수 있다 (SecurityConfig의 anyRequest().authenticated()). */
    @Operation(summary = "업로드 서명 발급", description = "파일은 서버를 거치지 않고 브라우저에서 S3 로 바로 올라갑니다. 서버는 서명만 내주고, 올라간 파일은 저장하는 시점에 크기와 형식을 다시 확인합니다.")
    @PostMapping("/presign")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        return storageService.presign(request);
    }
}