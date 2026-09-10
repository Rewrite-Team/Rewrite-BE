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
     * 카카오가 callback으로 준 인가코드 authorizationCode를 이용하여 카카오 사용자 정보를 조회하고 KakaoUser 로 변환
     * @param authorizationCode 카카오가 callback으로 준 인가코드
     * @return KakaoUser
     */
    @Override
    public KakaoUser getUser(String authorizationCode) {
        try {
            // authorizationCode 를 accessToken 으로 교환
            String kakaoAccessToken = exchangeToken(authorizationCode);
            KakaoUserResponse response = restClient
                    .get()
                    .uri(properties.kakao().userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)    // Authorization: Bearer {카카오-access-token}
                    .retrieve()     // 앞에서 구상한 GET 요청을 실행하고 응답 처리를 시작
                    .body(KakaoUserResponse.class); // JSON 응답을 KakaoUserResponse 객체로 역직렬화
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
        form.add("redirect_uri", properties.kakao().redirectUri()); // 인가 코드가 전달된 리다이렉트 URI
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
