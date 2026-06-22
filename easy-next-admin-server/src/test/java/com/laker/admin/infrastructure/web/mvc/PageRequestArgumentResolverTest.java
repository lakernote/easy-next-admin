package com.laker.admin.infrastructure.web.mvc;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestArgumentResolverTest {

    private final PageRequestArgumentResolver resolver = new PageRequestArgumentResolver();

    @Test
    void shouldBuildQueryWrapperWithAllowedFieldMapping() throws Exception {
        ServletWebRequest request = request(
                "page", "2",
                "size", "20",
                "filter", "username|like|admin,status|eq|1",
                "sort", "createdAt|desc"
        );

        PageRequest pageRequest = (PageRequest) resolver.resolveArgument(parameter("safePage"), null, request, null);

        assertThat(pageRequest.getPage()).isEqualTo(2);
        assertThat(pageRequest.getSize()).isEqualTo(20);
        assertThat(pageRequest.toPage().getCurrent()).isEqualTo(2);
        assertThat(pageRequest.toPage().getSize()).isEqualTo(20);
        assertThat(pageRequest.getQueryWrapper().getSqlSegment())
                .contains("username LIKE")
                .contains("status =")
                .contains("ORDER BY created_at DESC");
    }

    @Test
    void shouldRejectUnknownFilterField() throws Exception {
        ServletWebRequest request = request("filter", "password|eq|secret");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("safePage"), null, request, null), "不支持的筛选字段");
    }

    @Test
    void shouldRejectOversizedPageSize() throws Exception {
        ServletWebRequest request = request("size", "1000");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("safePage"), null, request, null), "每页条数不能超过");
    }

    @Test
    void shouldRejectPageRequestWithoutPageQueryAnnotation() throws Exception {
        ServletWebRequest request = request("page", "1");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("unsafePage"), null, request, null), "必须声明 @PageQuery");
    }

    @Test
    void shouldRejectDisallowedOperatorForField() throws Exception {
        ServletWebRequest request = request("filter", "status|like|1");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("safePage"), null, request, null), "不支持 like 操作");
    }

    @Test
    void shouldRejectNonSortableField() throws Exception {
        ServletWebRequest request = request("sort", "displayOrder|asc");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("safePage"), null, request, null), "不支持的排序字段");
    }

    @Test
    void shouldRejectUnsafeColumnNameFromFieldEnum() throws Exception {
        ServletWebRequest request = request("filter", "name|eq|admin");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("unsafeColumnPage"), null, request, null), "columnName 不安全");
    }

    @Test
    void shouldRejectDuplicateParamNameFromFieldEnum() throws Exception {
        ServletWebRequest request = request("filter", "name|eq|admin");

        assertValidationFailure(() -> resolver.resolveArgument(parameter("duplicateFieldPage"), null, request, null), "paramName 重复");
    }

    private void assertValidationFailure(ThrowingAction action, String messagePart) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getMsg()).contains(messagePart);
                });
    }

    private ServletWebRequest request(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int i = 0; i < params.length; i += 2) {
            request.setParameter(params[i], params[i + 1]);
        }
        return new ServletWebRequest(request);
    }

    private MethodParameter parameter(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName, PageRequest.class);
        return new MethodParameter(method, 0);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void execute() throws Exception;
    }

    private enum UserQueryField implements PageQueryField {
        USERNAME("username", "username"),
        STATUS("status", "status") {
            @Override
            public Set<PageQueryOperator> allowedOperators() {
                return Set.of(PageQueryOperator.EQ);
            }
        },
        CREATED_AT("createdAt", "created_at"),
        DISPLAY_ORDER("displayOrder", "display_order") {
            @Override
            public boolean sortable() {
                return false;
            }
        };

        private final String paramName;
        private final String columnName;

        UserQueryField(String paramName, String columnName) {
            this.paramName = paramName;
            this.columnName = columnName;
        }

        @Override
        public String paramName() {
            return paramName;
        }

        @Override
        public String columnName() {
            return columnName;
        }
    }

    private enum UnsafeColumnField implements PageQueryField {
        NAME("name", "name desc");

        private final String paramName;
        private final String columnName;

        UnsafeColumnField(String paramName, String columnName) {
            this.paramName = paramName;
            this.columnName = columnName;
        }

        @Override
        public String paramName() {
            return paramName;
        }

        @Override
        public String columnName() {
            return columnName;
        }
    }

    private enum DuplicateField implements PageQueryField {
        NAME("name", "name"),
        NICKNAME("name", "nickname");

        private final String paramName;
        private final String columnName;

        DuplicateField(String paramName, String columnName) {
            this.paramName = paramName;
            this.columnName = columnName;
        }

        @Override
        public String paramName() {
            return paramName;
        }

        @Override
        public String columnName() {
            return columnName;
        }
    }

    private static class TestController {
        @SuppressWarnings("unused")
        void safePage(@PageQuery(fields = UserQueryField.class, maxSize = 50) PageRequest pageRequest) {
        }

        @SuppressWarnings("unused")
        void unsafePage(PageRequest pageRequest) {
        }

        @SuppressWarnings("unused")
        void unsafeColumnPage(@PageQuery(fields = UnsafeColumnField.class) PageRequest pageRequest) {
        }

        @SuppressWarnings("unused")
        void duplicateFieldPage(@PageQuery(fields = DuplicateField.class) PageRequest pageRequest) {
        }
    }
}
