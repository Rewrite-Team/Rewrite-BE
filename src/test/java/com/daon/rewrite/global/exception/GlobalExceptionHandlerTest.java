package com.daon.rewrite.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void businessExceptionReturnsSpecErrorShape() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0));
    }

    @Test
    void validationExceptionReturnsDetails() throws Exception {
        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.error.details[0].field").value("title"))
                .andExpect(jsonPath("$.error.details[0].reason").value("제목은 필수입니다."));
    }

    @Test
    void unexpectedExceptionReturnsInternalErrorWithoutRawMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0));
    }

    @Test
    void typeMismatchExceptionReturnsValidationError() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details.length()").value(0));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        @PostMapping("/test/validation-error")
        void validationError(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("raw internal message");
        }

        @GetMapping("/test/type-mismatch")
        void typeMismatch(TestStatus status) {
        }
    }

    record TestRequest(
            @NotBlank(message = "제목은 필수입니다.")
            String title
    ) {
    }

    enum TestStatus {
        ACTIVE
    }
}
