package com.laker.admin.infrastructure.web.mvc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class PageRequestArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int DEFAULT_PAGE = 1;
    private static final int MIN_SIZE = 1;
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final Map<Class<? extends PageQueryField>, Map<String, PageQueryField>> allowedFieldCache = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory)
            throws Exception {
        PageQuery pageQuery = parameter.getParameterAnnotation(PageQuery.class);
        if (pageQuery == null) {
            throw validationFailure("PageRequest 参数必须声明 @PageQuery 字段白名单");
        }
        validatePageQuery(pageQuery);
        Map<String, PageQueryField> allowedFields = allowedFields(pageQuery);
        PageRequest.PageRequestBuilder builder = PageRequest.builder();
        int page = parsePage(webRequest.getParameter("page"));
        int size = parseSize(webRequest.getParameter("size"), pageQuery.defaultSize(), pageQuery.maxSize());
        String filter = webRequest.getParameter("filter");
        String sort = webRequest.getParameter("sort");
        builder.page(page).size(size);
        QueryWrapper<?> queryWrapper = new QueryWrapper<>();
        applyFilters(queryWrapper, filter, allowedFields);
        applySorts(queryWrapper, sort, allowedFields);
        builder.queryWrapper(queryWrapper);
        return builder.build();
    }

    private void applyFilters(QueryWrapper<?> queryWrapper, String filter, Map<String, PageQueryField> allowedFields) {
        if (!StringUtils.hasText(filter)) {
            return;
        }
        for (String filterParam : filter.split(",")) {
            if (!StringUtils.hasText(filterParam)) {
                continue;
            }
            String[] parts = filterParam.split("\\|", 3);
            if (parts.length != 3 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
                throw validationFailure("筛选条件格式错误，正确格式为 field|op|value");
            }
            PageQueryField field = allowedFields.get(parts[0].trim());
            if (field == null || !field.filterable()) {
                throw validationFailure("不支持的筛选字段：" + parts[0].trim());
            }
            PageQueryOperator operator = PageQueryOperator.fromValue(parts[1])
                    .orElseThrow(() -> validationFailure("不支持的筛选操作：" + parts[1].trim()));
            if (!field.allowedOperators().contains(operator)) {
                throw validationFailure("字段 " + field.paramName() + " 不支持 " + operator.value() + " 操作");
            }
            operator.apply(queryWrapper, field.columnName(), parts[2].trim());
        }
    }

    private void applySorts(QueryWrapper<?> queryWrapper, String sort, Map<String, PageQueryField> allowedFields) {
        if (!StringUtils.hasText(sort)) {
            return;
        }
        for (String sortParam : sort.split(",")) {
            if (!StringUtils.hasText(sortParam)) {
                continue;
            }
            String[] parts = sortParam.split("\\|", 2);
            if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                throw validationFailure("排序条件格式错误，正确格式为 field|asc|desc");
            }
            PageQueryField field = allowedFields.get(parts[0].trim());
            if (field == null || !field.sortable()) {
                throw validationFailure("不支持的排序字段：" + parts[0].trim());
            }
            String direction = parts[1].trim();
            if ("asc".equalsIgnoreCase(direction)) {
                queryWrapper.orderByAsc(field.columnName());
            } else if ("desc".equalsIgnoreCase(direction)) {
                queryWrapper.orderByDesc(field.columnName());
            } else {
                throw validationFailure("不支持的排序方向：" + direction);
            }
        }
    }

    private int parsePage(String value) {
        int page = parseInt(value, DEFAULT_PAGE, "页码必须是数字");
        if (page < DEFAULT_PAGE) {
            throw validationFailure("页码必须大于等于 1");
        }
        return page;
    }

    private int parseSize(String value, int defaultSize, int maxSize) {
        int size = parseInt(value, defaultSize, "每页条数必须是数字");
        if (size < MIN_SIZE) {
            throw validationFailure("每页条数必须大于等于 1");
        }
        if (size > maxSize) {
            throw validationFailure("每页条数不能超过 " + maxSize);
        }
        return size;
    }

    private int parseInt(String value, int defaultValue, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw validationFailure(errorMessage);
        }
    }

    private void validatePageQuery(PageQuery pageQuery) {
        if (pageQuery.defaultSize() < MIN_SIZE) {
            throw validationFailure("@PageQuery defaultSize 必须大于等于 1");
        }
        if (pageQuery.maxSize() < pageQuery.defaultSize()) {
            throw validationFailure("@PageQuery maxSize 必须大于等于 defaultSize");
        }
    }

    private Map<String, PageQueryField> allowedFields(PageQuery pageQuery) {
        return allowedFieldCache.computeIfAbsent(pageQuery.fields(), this::buildAllowedFields);
    }

    private Map<String, PageQueryField> buildAllowedFields(Class<? extends PageQueryField> fieldClass) {
        if (!fieldClass.isEnum()) {
            throw validationFailure("@PageQuery fields 必须是实现 PageQueryField 的枚举");
        }
        PageQueryField[] fields = fieldClass.getEnumConstants();
        if (fields == null || fields.length == 0) {
            throw validationFailure("@PageQuery fields 不能为空");
        }
        Map<String, PageQueryField> fieldMap = new LinkedHashMap<>();
        Arrays.stream(fields).forEach(field -> {
            validateField(field);
            PageQueryField previous = fieldMap.put(field.paramName(), field);
            if (previous != null) {
                throw validationFailure("@PageQuery 字段 paramName 重复：" + field.paramName());
            }
        });
        return Collections.unmodifiableMap(fieldMap);
    }

    private void validateField(PageQueryField field) {
        if (!StringUtils.hasText(field.paramName())) {
            throw validationFailure("@PageQuery 字段 paramName 不能为空");
        }
        if (!StringUtils.hasText(field.columnName()) || !SAFE_COLUMN_PATTERN.matcher(field.columnName()).matches()) {
            throw validationFailure("@PageQuery 字段 " + field.paramName() + " 的 columnName 不安全");
        }
    }

    private BusinessException validationFailure(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
