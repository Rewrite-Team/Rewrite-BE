package com.daon.rewrite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.daon.rewrite.auth.client.KakaoClientException;
import com.daon.rewrite.auth.client.KakaoUser;
import com.daon.rewrite.auth.client.RestKakaoClient;
import com.daon.rewrite.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestKakaoClientTest {

    private MockRestServiceServer server;
    private RestKakaoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AuthProperties properties = new AuthProperties(
                "rewrite", "rewrite-web", "unused", "https://front", "https://front", "https://front/login",
                new AuthProperties.Kakao(
                        "client-id", "client-secret", "https://api/auth/kakao/callback",
                        "https://kauth.kakao.com/oauth/authorize",
                        "https://kauth.kakao.com/oauth/token",
                        "https://kapi.kakao.com/v2/user/me"
                )
        );
        client = new RestKakaoClient(builder.build(), properties);
    }

    @Test
    void exchangesCodeAndRetrievesKakaoUser() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("code=authorization-code")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("""
                        {
                          "id": 12345,
                          "kakao_account": {
                            "profile": {
                              "nickname": "홍길동",
                              "profile_image_url": "https://image.example.com/profile.png"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoUser user = client.getUser("authorization-code");

        assertThat(user.providerUserId()).isEqualTo("12345");
        assertThat(user.nickname()).isEqualTo("홍길동");
        assertThat(user.profileImageUrl()).isEqualTo("https://image.example.com/profile.png");
        server.verify();
    }

    @Test
    void rejectsMissingNickname() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("{\"id\":123,\"kakao_account\":{\"profile\":{}}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUser("code"))
                .isInstanceOf(KakaoClientException.class)
                .hasMessage("Kakao user response is invalid");
    }
}
