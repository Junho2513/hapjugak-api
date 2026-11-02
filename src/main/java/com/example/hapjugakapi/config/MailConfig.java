package com.example.hapjugakapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * 메일 발송(Amazon SES) 설정.
 *
 * S3와 같은 이유로 자격 증명을 코드에 쓰지 않는다.
 * SDK 기본 체인이 환경 변수 → EC2 인스턴스 역할 순으로 찾으므로
 * 개발 PC와 운영 서버가 같은 코드로 돈다.
 *
 * 서버에 붙은 IAM 역할(hapjugak-ses-send)은 ses:SendEmail 만 허용하고,
 * 그마저도 발신 주소가 noreply@hapjugak.com 일 때만 통과한다.
 * 서버가 뚫려도 다른 주소로 사칭 메일을 보낼 수 없다.
 *
 * S3Config와 나눠 둔 이유: 서비스가 다르고, 나중에 메일만 다른 리전으로
 * 옮기게 될 수도 있다. 지금 합쳐두면 그때 둘 다 건드려야 한다.
 */
@Configuration
public class MailConfig {

    @Bean
    public SesV2Client sesV2Client(@Value("${app.mail.region}") String region) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .build();
    }
}
