package com.example.hapjugakapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 자격 증명을 코드에 쓰지 않는다.
 *
 * SDK 기본 체인이 환경 변수 → EC2 인스턴스 역할 순으로 알아서 찾는다.
 * 그래서 개발 PC에서는 환경 변수를, 운영 서버에서는 IAM 역할을 쓰는데도
 * 코드는 한 글자도 달라지지 않는다. 서버에 액세스 키 파일이 남지 않는다.
 */
@Configuration
public class S3Config {

    @Value("${app.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}