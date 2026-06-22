package com.laker.admin.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.config.jackson.EasyJacksonCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseContractTest {
    private final ObjectMapper objectMapper = projectObjectMapper();

    @Test
    void successShouldUseStableZeroCode() {
        Response<String> response = Response.ok("done");

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("done");
        assertThat(response.getDetails()).isNull();
    }

    @Test
    void errorShouldUseBusinessCodeAndKeepDataEmpty() {
        Response<Void> response = Response.error(ErrorCode.BUSINESS_ERROR, "流程状态已变化，请刷新后重试");

        assertThat(response.getCode()).isEqualTo(400100);
        assertThat(response.getMessage()).isEqualTo("流程状态已变化，请刷新后重试");
        assertThat(response.getData()).isNull();
    }

    @Test
    void validationErrorShouldExposeDetailsOutsideBusinessData() {
        Response<Void> response = Response.error(
                ErrorCode.VALIDATION_FAILED,
                "参数校验失败",
                List.of(ApiErrorDetail.of("userName", "用户名不能为空"))
        );

        assertThat(response.getCode()).isEqualTo(400004);
        assertThat(response.getData()).isNull();
        assertThat(response.getDetails()).containsExactly(ApiErrorDetail.of("userName", "用户名不能为空"));
    }

    @Test
    void pageResponseShouldUsePageDataPayload() {
        PageResponse<String> response = PageResponse.ok(List.of("a", "b"), 21);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().list()).containsExactly("a", "b");
        assertThat(response.getData().total()).isEqualTo(21);
    }

    @Test
    void pageResponseShouldSerializeOnlyDataListAndTotal() throws Exception {
        String json = objectMapper.writeValueAsString(PageResponse.ok(List.of("a", "b"), 21));

        assertThat(json).contains("\"data\"");
        assertThat(json).contains("\"list\":[\"a\",\"b\"]");
        assertThat(json).contains("\"total\":21");
        assertThat(json).doesNotContain("\"details\"");
        assertThat(json).doesNotContain("\"pageSize\"");
        assertThat(json).doesNotContain("\"pages\"");
    }

    @Test
    void validationErrorShouldSerializeDetailsWhenPresent() throws Exception {
        String json = objectMapper.writeValueAsString(Response.error(
                ErrorCode.VALIDATION_FAILED,
                "参数校验失败",
                List.of(ApiErrorDetail.of("userName", "用户名不能为空"))
        ));

        assertThat(json).contains("\"details\"");
        assertThat(json).contains("\"field\":\"userName\"");
        assertThat(json).contains("\"message\":\"用户名不能为空\"");
    }

    private static ObjectMapper projectObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new EasyJacksonCustomizer().customize(builder);
        return builder.build();
    }
}
