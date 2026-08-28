package com.daon.rewrite.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("auth-test")
class OpenApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiEndpointsRequireBasicAuthenticationOutsideLocalAndTestProfiles() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html"}) {
                    mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(
                            "WWW-Authenticate",
                            "Basic realm=\"rewrite-internal-tools\""
                    ));
        }
    }

    @Test
    void openApiDocumentAcceptsInternalToolsCredentials() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                .with(httpBasic("rewrite-tools", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Rewrite API"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize']").exists())
                .andExpect(jsonPath("$.paths['/auth/kakao/callback']").exists());
    }

    @Test
    void internalToolsCredentialsDoNotAuthenticateApplicationApi() throws Exception {
        mockMvc.perform(get("/user/me")
                        .with(httpBasic("rewrite-tools", "test-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

}
