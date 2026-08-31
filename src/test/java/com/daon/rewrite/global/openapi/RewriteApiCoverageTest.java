package com.daon.rewrite.global.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.daon.rewrite.auth.controller.CsrfTokenController;
import com.daon.rewrite.auth.controller.KakaoAuthController;
import com.daon.rewrite.auth.controller.LogoutController;
import com.daon.rewrite.auth.controller.TokenRefreshController;
import com.daon.rewrite.auth.controller.UserController;
import com.daon.rewrite.coverletter.controller.CoverLetterController;
import com.daon.rewrite.interview.controller.InterviewController;
import com.daon.rewrite.interview.controller.InterviewMessageController;
import com.daon.rewrite.keywordanalysis.controller.KeywordAnalysisController;
import com.daon.rewrite.llmjob.controller.LlmJobController;
import com.daon.rewrite.reviewversion.controller.ReviewVersionController;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class RewriteApiCoverageTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            KakaoAuthController.class,
            CsrfTokenController.class,
            TokenRefreshController.class,
            UserController.class,
            LogoutController.class,
            CoverLetterController.class,
            LlmJobController.class,
            ReviewVersionController.class,
            KeywordAnalysisController.class,
            InterviewController.class,
            InterviewMessageController.class
    );

    private static final Set<String> CANONICAL_SCREENS = Set.of(
            "공통", "로그인", "자기소개서 목록", "자기소개서 등록",
            "첨삭 진행", "첨삭 결과", "키워드 분석", "AI 면접"
    );

    @Test
    void everyActiveControllerMappingHasTheExactApiIdMethodAndPath() {
        Map<String, Endpoint> actual = new LinkedHashMap<>();

        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                Endpoint endpoint = endpoint(controller, method);
                if (endpoint == null) {
                    continue;
                }
                RewriteApi api = method.getAnnotation(RewriteApi.class);
                assertThat(api)
                        .as("%s.%s @RewriteApi", controller.getSimpleName(), method.getName())
                        .isNotNull();
                assertThat(actual.put(api.id(), endpoint)).as("중복 API ID %s", api.id()).isNull();
                assertThat(api.id()).matches("API-\\d{3}");
                assertThat(List.of(api.screens())).allMatch(CANONICAL_SCREENS::contains);
            }
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expectedEndpoints());
        assertThat(actual).hasSize(29).doesNotContainKey("API-028");
    }

    @Test
    void csrfMetadataMatchesEveryStateChangingApi() {
        Set<String> csrfProtected = Set.of(
                "API-004", "API-006", "API-008", "API-009", "API-010", "API-011",
                "API-013", "API-014", "API-019", "API-020", "API-022", "API-023",
                "API-024", "API-027"
        );

        annotations().forEach((id, api) ->
                assertThat(api.csrfProtected()).as("%s CSRF", id).isEqualTo(csrfProtected.contains(id))
        );
    }

    @Test
    void oauthApisDescribeRedirectOutcomesWithoutJsonInternalError() throws Exception {
        RewriteApi authorize = KakaoAuthController.class.getDeclaredMethod("authorize")
                .getAnnotation(RewriteApi.class);
        RewriteApi callback = KakaoAuthController.class.getDeclaredMethod(
                "callback", String.class, String.class, String.class, String.class
        ).getAnnotation(RewriteApi.class);

        assertThat(authorize.successStatus()).isEqualTo(302);
        assertThat(authorize.includeInternalError()).isFalse();
        assertThat(authorize.redirectErrors()).extracting(ApiRedirectError::code)
                .containsExactly("KAKAO_LOGIN_FAILED");

        assertThat(callback.successStatus()).isEqualTo(302);
        assertThat(callback.includeInternalError()).isFalse();
        assertThat(callback.redirectErrors()).extracting(ApiRedirectError::code)
                .containsExactly("KAKAO_LOGIN_CANCELED", "KAKAO_LOGIN_FAILED");
    }

    private Map<String, RewriteApi> annotations() {
        Map<String, RewriteApi> annotations = new LinkedHashMap<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                RewriteApi api = method.getAnnotation(RewriteApi.class);
                if (api != null) {
                    annotations.put(api.id(), api);
                }
            }
        }
        return annotations;
    }

    private Endpoint endpoint(Class<?> controller, Method method) {
        RequestMapping requestMapping = controller.getAnnotation(RequestMapping.class);
        String prefix = requestMapping == null ? "" : requestMapping.value()[0];
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return new Endpoint("GET", prefix + mappingPath(get.value(), get.path()));
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return new Endpoint("POST", prefix + mappingPath(post.value(), post.path()));
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return new Endpoint("PUT", prefix + mappingPath(put.value(), put.path()));
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return new Endpoint("DELETE", prefix + mappingPath(delete.value(), delete.path()));
        }
        return null;
    }

    private String mappingPath(String[] value, String[] path) {
        return value.length > 0 ? value[0] : path[0];
    }

    private Map<String, Endpoint> expectedEndpoints() {
        return Map.ofEntries(
                entry("API-001", "GET", "/auth/kakao/authorize"), entry("API-002", "GET", "/auth/kakao/callback"),
                entry("API-003", "GET", "/auth/csrf-token"), entry("API-004", "POST", "/auth/refresh"),
                entry("API-005", "GET", "/user/me"), entry("API-006", "POST", "/auth/logout"),
                entry("API-007", "GET", "/cover-letters"), entry("API-008", "POST", "/cover-letters"),
                entry("API-009", "PUT", "/cover-letters/{coverLetterId}/basic-info"),
                entry("API-010", "PUT", "/cover-letters/{coverLetterId}/preferences"),
                entry("API-011", "PUT", "/cover-letters/{coverLetterId}/questions"),
                entry("API-012", "GET", "/cover-letters/{coverLetterId}"),
                entry("API-013", "DELETE", "/cover-letters/{coverLetterId}"),
                entry("API-014", "POST", "/cover-letters/{coverLetterId}/submit"),
                entry("API-015", "GET", "/llm-jobs/{jobId}"),
                entry("API-016", "GET", "/llm-jobs/{jobId}/stream"),
                entry("API-017", "GET", "/cover-letters/{coverLetterId}/review-versions"),
                entry("API-018", "GET", "/cover-letters/{coverLetterId}/review-versions/{versionId}"),
                entry("API-019", "PUT", "/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers"),
                entry("API-020", "POST", "/cover-letters/{coverLetterId}/keyword-analysis"),
                entry("API-021", "GET", "/cover-letters/{coverLetterId}/keyword-analysis/latest"),
                entry("API-022", "POST", "/cover-letters/{coverLetterId}/interviews"),
                entry("API-023", "POST", "/interview-threads/{threadId}/messages"),
                entry("API-024", "POST", "/cover-letters/{coverLetterId}/review-versions"),
                entry("API-025", "GET", "/cover-letters/{coverLetterId}/interview"),
                entry("API-026", "GET", "/interviews/{interviewSessionId}/questions"),
                entry("API-027", "POST", "/interviews/{interviewSessionId}/questions"),
                entry("API-029", "GET", "/interview-threads/{threadId}/messages"),
                entry("API-030", "GET", "/cover-letters/stream")
        );
    }

    private Map.Entry<String, Endpoint> entry(String id, String method, String path) {
        return Map.entry(id, new Endpoint(method, path));
    }

    private record Endpoint(String method, String path) {
    }
}
