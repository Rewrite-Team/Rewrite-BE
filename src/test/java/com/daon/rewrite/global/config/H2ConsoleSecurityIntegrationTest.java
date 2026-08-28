package com.daon.rewrite.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.h2.console.enabled=true"
)
@ActiveProfiles({"auth-test", "devtools"})
class H2ConsoleSecurityIntegrationTest {

    private static final String BASIC_AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "rewrite-tools:test-password".getBytes(StandardCharsets.UTF_8)
    );

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void h2ConsoleRequiresInternalToolsCredentials() throws Exception {
        HttpResponse<String> response = send(get("/h2-console/"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .contains("Basic realm=\"rewrite-internal-tools\"");
    }

    @Test
    void h2ConsoleAllowsFramesAfterBasicAuthentication() throws Exception {
        HttpResponse<String> response = send(get("/h2-console/")
                .header("Authorization", BASIC_AUTH));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
    }

    @Test
    void h2ConsolePostIsNotRejectedByApplicationCsrfFilter() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/h2-console/login.do"))
                .header("Authorization", BASIC_AUTH)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return send(request.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
