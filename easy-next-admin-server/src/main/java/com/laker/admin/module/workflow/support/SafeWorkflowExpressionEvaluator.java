package com.laker.admin.module.workflow.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SafeWorkflowExpressionEvaluator implements WorkflowExpressionEvaluator {
    private static final Pattern VARIABLE = Pattern.compile("^[A-Za-z_][\\w.:-]*$");
    private static final Pattern BINARY_EXPRESSION = Pattern.compile("^\\s*([A-Za-z_][\\w.:-]*)\\s*(==|!=|>=|<=|>|<|contains)\\s*(.+?)\\s*$");
    private static final Pattern PLACEHOLDER_VARIABLE = Pattern.compile("\\$\\{\\s*([A-Za-z_][\\w.:-]*)\\s*}");

    /**
     * 只支持白名单表达式，避免把流程条件做成脚本引擎。
     * 典型格式：amount > 5000、businessType == 'expense'、title contains '报销'。
     */
    @Override
    public boolean evaluate(String expression, Map<String, Object> variables) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        String normalizedExpression = normalizeExpression(expression);
        Matcher matcher = BINARY_EXPRESSION.matcher(normalizedExpression);
        if (!matcher.matches()) {
            Object value = lookup(variables, normalizeVariableName(normalizedExpression));
            return truthy(value);
        }
        Object left = lookup(variables, normalizeVariableName(matcher.group(1)));
        String operator = matcher.group(2);
        Object right = parseLiteral(matcher.group(3), variables);
        if (left == null) {
            return false;
        }
        return compare(left, operator, right);
    }

    @Override
    public void validate(String expression) {
        if (!StringUtils.hasText(expression)) {
            return;
        }
        String normalizedExpression = normalizeExpression(expression);
        if (VARIABLE.matcher(normalizedExpression).matches()) {
            return;
        }
        Matcher matcher = BINARY_EXPRESSION.matcher(normalizedExpression);
        if (!matcher.matches() || !supportedRightOperand(matcher.group(3))) {
            throw new IllegalArgumentException("不支持的条件表达式：" + expression);
        }
    }

    private boolean compare(Object left, String operator, Object right) {
        BigDecimal leftNumber = decimal(left);
        BigDecimal rightNumber = decimal(right);
        if (leftNumber != null && rightNumber != null) {
            int result = leftNumber.compareTo(rightNumber);
            return switch (operator) {
                case "==" -> result == 0;
                case "!=" -> result != 0;
                case ">" -> result > 0;
                case ">=" -> result >= 0;
                case "<" -> result < 0;
                case "<=" -> result <= 0;
                default -> false;
            };
        }
        String leftText = String.valueOf(left);
        String rightText = right == null ? "" : String.valueOf(right);
        return switch (operator) {
            case "==" -> Objects.equals(leftText, rightText);
            case "!=" -> !Objects.equals(leftText, rightText);
            case "contains" -> leftText.contains(rightText);
            default -> false;
        };
    }

    private Object parseLiteral(String raw, Map<String, Object> variables) {
        String text = raw == null ? "" : raw.trim();
        if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
            return text.substring(1, text.length() - 1);
        }
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        Object variableValue = lookup(variables, normalizeVariableName(text));
        if (variableValue != null) {
            return variableValue;
        }
        BigDecimal number = decimal(text);
        return number == null ? text : number;
    }

    private boolean supportedRightOperand(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if ((text.startsWith("'") && text.endsWith("'") && text.length() >= 2)
                || (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2)) {
            return true;
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return true;
        }
        if (decimal(text) != null) {
            return true;
        }
        return VARIABLE.matcher(normalizeVariableName(text)).matches();
    }

    @SuppressWarnings("unchecked")
    private Object lookup(Map<String, Object> variables, String key) {
        if (variables == null || !StringUtils.hasText(key)) {
            return null;
        }
        if (variables.containsKey(key)) {
            return variables.get(key);
        }
        Object cursor = variables;
        for (String segment : key.split("\\.")) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = ((Map<String, Object>) map).get(segment);
        }
        return cursor;
    }

    private String normalizeVariableName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String normalizeExpression(String raw) {
        return PLACEHOLDER_VARIABLE.matcher(raw.trim()).replaceAll("$1");
    }

    private boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
