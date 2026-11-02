package com.example.hapjugakapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서 설정.
 *
 * 문서를 손으로 쓰지 않는 이유는 단순하다. 손으로 쓴 문서는 코드와 어긋난다.
 * 여기서 만드는 문서는 컨트롤러와 DTO 에서 바로 뽑아내므로
 * 상태코드나 검증 규칙을 바꾸면 문서도 같이 바뀐다.
 *
 * 열람 주소
 *   화면      /api/docs
 *   원본 JSON /api/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI hapjugakOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("합주각 API")
                        .version("v1")
                        .description("""
                                밴드 합주를 열고, 곡을 추천하고, 파트별 자리를 잡는 서비스의 API 입니다.

                                **인증** — 로그인(`POST /auth/login` 또는 `POST /auth/google`)으로 받은 토큰을
                                오른쪽 위 **Authorize** 버튼에 넣으면, 이 화면에서 바로 호출해볼 수 있습니다.
                                `Bearer` 는 붙이지 않고 토큰만 넣습니다.

                                **상태코드**
                                - `201` 새 자원을 만들었을 때. `Location` 헤더에 만들어진 자원의 주소가 들어갑니다
                                - `204` 지웠을 때처럼 돌려줄 내용이 없을 때
                                - `400` 입력값이 규칙에 맞지 않을 때. 어떤 항목이 왜 틀렸는지 응답에 담깁니다
                                - `409` 이미 찬 자리를 잡으려 했을 때처럼, 지금 상태와 충돌할 때
                                """))
                // 모든 엔드포인트에 자물쇠를 달아둔다.
                // 실제로 누구나 부를 수 있는지는 SecurityConfig 가 정한다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답의 accessToken 값을 그대로 넣으세요.")));
    }
}
