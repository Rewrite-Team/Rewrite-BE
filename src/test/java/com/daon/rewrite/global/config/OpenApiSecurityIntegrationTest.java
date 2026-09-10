package com.daon.rewrite.global.config;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rewrite-prod-profile-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.openai.api-key=test-key",
        "rewrite.internal-tools.username=rewrite-tools",
        "rewrite.internal-tools.password=test-password",
        "rewrite.auth.jwt-secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "rewrite.auth.frontend-origin=https://rewrite.example.com",
        "rewrite.auth.frontend-success-url=https://rewrite.example.com",
        "rewrite.auth.frontend-login-url=https://rewrite.example.com/login",
        "rewrite.auth.kakao.client-id=test-client",
        "rewrite.auth.kakao.client-secret=test-secret",
        "rewrite.auth.kakao.redirect-uri=https://api.rewrite.example.com/auth/kakao/callback"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
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
                .andExpect(jsonPath("$.paths['/auth/kakao/callback']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.type")
                        .value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.in")
                        .value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfToken.name")
                        .value("X-CSRF-Token"))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.security[0].csrfToken")
                        .isArray())
                .andExpect(jsonPath("$.paths['/auth/csrf-token'].get.security")
                        .doesNotExist());
    }

    @Test
    void productionProfileDocumentsAllActiveApisAndOAuthRedirects() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(httpBasic("rewrite-tools", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..operationId", hasSize(29)))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.operationId")
                        .value("API-001"))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['302'].description")
                        .value(org.hamcrest.Matchers.containsString("KAKAO_LOGIN_FAILED")))
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['500']")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/kakao/authorize'].get.responses['302'].headers.Location")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/kakao/callback'].get.responses['302'].description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("KAKAO_LOGIN_CANCELED"),
                                org.hamcrest.Matchers.containsString("KAKAO_LOGIN_FAILED")
                        )))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/SuccessResponse"))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['401'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['200'].headers['Set-Cookie']")
                        .exists())
                .andExpect(jsonPath("$.paths['/interviews/{interviewSessionId}/threads']")
                        .doesNotExist());
    }

    @Test
    void openApiDocumentProvidesRequestExamplesAndConstraints() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(httpBasic("rewrite-tools", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.title.example")
                        .value("2026 상반기 Rewrite 백엔드 개발자 자기소개서"))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.title.maxLength")
                        .value(50))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.companyName.maxLength")
                        .value(30))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.positionTitle.maxLength")
                        .value(30))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.jobPostingUrl.example")
                        .value("https://recruit.example.com/jobs/backend-developer"))
                .andExpect(jsonPath("$.components.schemas.SaveBasicInfoRequest.properties.jobPostingUrl.maxLength")
                        .value(500))
                .andExpect(jsonPath("$.components.schemas.SavePreferencesRequest.properties.preferences.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SavePreferencesRequest.properties.preferences.maxLength")
                        .value(3000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.question.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.question.maxLength")
                        .value(300))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.example")
                        .value(1000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.minimum")
                        .value(100))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.maxAnswerLength.maximum")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.originalAnswer.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.QuestionRequest.properties.originalAnswer.maxLength")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.RequestReReviewRequest.properties.requestInstruction.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.RequestReReviewRequest.properties.requestInstruction.maxLength")
                        .value(1000))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.required")
                        .value(containsInAnyOrder("questionResultId", "finalAnswer")))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.questionResultId.example")
                        .value("rvqr_123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.AnswerRequest.properties.finalAnswer.maxLength")
                        .value(5000))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.required[0]")
                        .value("content"))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.example")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.SendInterviewMessageRequest.properties.content.maxLength")
                        .value(2000));
    }

    @Test
    void internalToolsCredentialsDoNotAuthenticateApplicationApi() throws Exception {
        mockMvc.perform(get("/user/me")
                        .with(httpBasic("rewrite-tools", "test-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

}
