package com.daon.rewrite.auth.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@code auth-real}의 {@code RestKakaoClient}가 토큰 교환·사용자 조회에 사용할 HTTP 클라이언트를 구성한다.
 * 각 요청의 연결 대기는 3초, 응답 읽기 대기는 5초로 제한한다.
 */
@Configuration
@Profile("auth-real")
public class KakaoClientConfig {

    @Bean
    RestClient kakaoRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
