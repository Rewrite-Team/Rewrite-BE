package com.daon.rewrite.auth.client;

import com.daon.rewrite.auth.config.AuthProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 인가 코드를 카카오 access token으로 교환한 뒤 사용자 정보를 조회한다.
 * 카카오 응답 검증과 오류 변환을 담당하며, 카카오 access token은 사용자 조회에만 사용한다.
 */
@Component
@Profile("auth-real")
public class RestKakaoClient implements KakaoClient {

    private final RestClient restClient;
    private final AuthProperties properties;

    public RestKakaoClient(RestClient kakaoRestClient, AuthProperties properties) {
        this.restClient = kakaoRestClient;
        this.properties = properties;
    }



    /**
     * 통신·요청 오류를 KakaoClientException으로 변환해 로그인 서비스에 전달한다.
     * 토큰이나 사용자 식별자·닉네임이 없는 응답도 같은 예외로 거부하며 프로필 이미지는 없어도 허용한다.
     */
    @Override
    public KakaoUser getUser(String authorizationCode) {
        try {
            String kakaoAccessToken = exchangeToken(authorizationCode);
            KakaoUserResponse response = restClient
                    .get()
                    .uri(properties.kakao().userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            return toUser(response);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new KakaoClientException("Kakao login provider request failed", e);
        }
    }

    private String exchangeToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.kakao().clientId());
        form.add("client_secret", properties.kakao().clientSecret());
        form.add("redirect_uri", properties.kakao().redirectUri());
        form.add("code", authorizationCode);

        KakaoTokenResponse response = restClient
                .post()
                .uri(properties.kakao().tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new KakaoClientException("Kakao token response is invalid");
        }
        return response.accessToken();
    }

    private static KakaoUser toUser(KakaoUserResponse response) {
        if (response == null || response.id() == null
                || response.kakaoAccount() == null
                || response.kakaoAccount().profile() == null
                || response.kakaoAccount().profile().nickname() == null
                || response.kakaoAccount().profile().nickname().isBlank()) {
            throw new KakaoClientException("Kakao user response is invalid");
        }
        KakaoProfile profile = response.kakaoAccount().profile();
        return new KakaoUser(
                Objects.toString(response.id()),
                profile.nickname(),
                profile.profileImageUrl()
        );
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
    }

    private record KakaoAccount(KakaoProfile profile) {
    }

    private record KakaoProfile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {
    }
}
