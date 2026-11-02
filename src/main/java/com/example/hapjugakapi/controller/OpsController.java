package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.service.OpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "운영", description = "관리자가 설정이 살아 있는지 확인하는 자리")
@RestController
@RequestMapping("/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    /**
     * 201 이 아니라 200 인 이유: 새로 생긴 자원이 없다. 보낸 행위의 결과만 돌려준다.
     * 받는 주소는 받지 않는다 - 로그인한 관리자 본인에게만 간다.
     */
    @Operation(summary = "메일 발송 점검",
            description = "로그인한 관리자 본인의 메일 주소로 예시 메일을 보냅니다. "
                    + "받는 주소는 입력받지 않습니다. SES 설정·IAM 권한·템플릿이 모두 살아 있는지 한 번에 확인합니다.")
    @ApiResponse(responseCode = "200", description = "보냈습니다")
    @ApiResponse(responseCode = "403", description = "관리자가 아닙니다")
    @PostMapping("/mail-test")
    public Map<String, String> mailTest(@AuthenticationPrincipal Long memberId) {
        String sentTo = opsService.sendTestMail(memberId);
        return Map.of(
                "sentTo", sentTo,
                "message", "메일을 보냈습니다. 받은메일함을 확인해주세요."
        );
    }
}
