package com.example.hapjugakapi.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 소셜 제공자에게 말을 걸 때 쓰는 HTTP 클라이언트.
 *
 * 기본 RestClient.create() 를 쓰지 않는 이유는 시간 제한 하나 때문이다.
 * 기본값에는 제한이 없어서, 제공자 쪽이 응답하지 않으면 그 요청을 받은 스레드가
 * 끝없이 기다린다. 톰캣 스레드는 한정돼 있으므로, 제공자 한 곳이 느려지면
 * 우리 서비스 전체가 같이 멈춘다. 남의 사정에 우리가 끌려가지 않게 끊는다.
 *
 * 로그인은 사람이 버튼을 누르고 기다리는 일이라 짧게 잡는다.
 * 5초 안에 답이 없으면 그 로그인은 이미 실패한 것이나 같다.
 */
final class SocialHttp {

    private SocialHttp() {
    }

    static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
