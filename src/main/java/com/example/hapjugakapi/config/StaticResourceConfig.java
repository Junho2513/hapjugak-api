package com.example.hapjugakapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * 글꼴 파일에 캐시 기한을 붙인다.
 *
 * 이 서비스가 쓰는 Pretendard 가변 글꼴은 2MB 다. 한글 음절 11,172자가
 * 전부 들어 있어서 그렇고, 화면에서 굵기를 여덟 가지(400·500·600·650·700·800·900·45)
 * 쓰기 때문에 가변 글꼴을 쓰는 것 자체는 맞는 선택이다. 굵기마다 따로 받으면 더 크다.
 *
 * 문제는 크기가 아니라 <b>매번 받으러 간다</b>는 점이었다.
 * 스프링은 정적 파일에 Cache-Control 을 기본으로 붙이지 않는다. Last-Modified 는
 * 붙여주므로 두 번째 방문부터 304 로 끝나기는 한다. 하지만 304 를 받으려면
 * 서버에 물어보기는 해야 하고, 이 서비스의 서버는 시드니에 있다.
 * 한국에서 한 번 왕복하는 데 300ms 쯤 걸린다. 화면을 열 때마다 글자가
 * 그 시간만큼 늦게 자리를 잡았다.
 *
 * 기한을 1년으로 못박으면 두 번째 방문부터는 서버에 묻지도 않는다.
 * 글꼴 파일은 내용이 바뀌지 않으므로 이렇게 해도 어긋날 일이 없다.
 * 혹시 글꼴을 갈아야 하면 파일 이름을 바꾸면 된다.
 *
 * 범위를 /fonts/** 로만 좁힌 이유: 정적 파일 전체에 1년을 걸면 index.html 까지
 * 1년 동안 캐시된다. 그러면 배포를 해도 사람들 화면이 안 바뀐다.
 * (바깥에서 보이는 주소는 context-path 가 붙어 /api/fonts/** 다.)
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}
