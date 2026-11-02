package com.example.hapjugakapi.config;

import com.example.hapjugakapi.dto.ErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.core.converter.ModelConverters;
import org.springdoc.core.customizers.OperationCustomizer;
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
                                - `429` 요청이 너무 잦을 때. `Retry-After` 헤더에 몇 초 뒤에 되는지 담깁니다

                                오류 응답은 상태코드와 무관하게 모두 같은 모양입니다 — `timestamp` · `status` · `message`.
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

    /**
     * 모든 4xx·5xx 응답의 본문을 ErrorResponse 로 맞춘다.
     *
     * springdoc 은 @ApiResponse 에 본문 스키마를 안 적으면
     * **그 메서드의 성공 반환 타입**을 가져다 쓴다.
     * 그래서 `POST /auth/login` 의 401·429 가 "accessToken 을 돌려준다"고 적혀 있었다.
     * 문서를 자동 생성하기로 해놓고 오류 쪽이 거짓말을 하고 있었던 셈이다.
     *
     * 컨트롤러마다 content 를 적는 대신 여기서 한 번에 바꾼다.
     * 앞으로 추가되는 엔드포인트도 저절로 맞는다 - 손으로 적는 곳을 늘리지 않는 게 요점이다.
     */
    @Bean
    public OperationCustomizer 오류응답_스키마_통일() {
        var 해석됨 = ModelConverters.getInstance().readAllAsResolvedSchema(ErrorResponse.class);
        Content 오류본문 = new Content().addMediaType("application/json",
                new MediaType().schema(해석됨 == null ? null : 해석됨.schema));

        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) {
                return operation;
            }
            operation.getResponses().forEach((code, response) -> {
                if (code.length() == 3 && (code.charAt(0) == '4' || code.charAt(0) == '5')) {
                    response.setContent(오류본문);
                }
            });
            return operation;
        };
    }
}
