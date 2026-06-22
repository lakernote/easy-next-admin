package com.laker.admin.infrastructure.web.mvc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.Locale;
import java.util.Optional;

public enum PageQueryOperator {
    EQ("eq") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.eq(columnName, value);
        }
    },
    LIKE("like") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.like(columnName, value);
        }
    },
    GT("gt") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.gt(columnName, value);
        }
    },
    LT("lt") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.lt(columnName, value);
        }
    },
    GTE("gte") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.ge(columnName, value);
        }
    },
    LTE("lte") {
        @Override
        void apply(QueryWrapper<?> wrapper, String columnName, String value) {
            wrapper.le(columnName, value);
        }
    };

    private final String value;

    PageQueryOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    abstract void apply(QueryWrapper<?> wrapper, String columnName, String value);

    public static Optional<PageQueryOperator> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PageQueryOperator operator : values()) {
            if (operator.value.equals(normalized)) {
                return Optional.of(operator);
            }
        }
        return Optional.empty();
    }
}
