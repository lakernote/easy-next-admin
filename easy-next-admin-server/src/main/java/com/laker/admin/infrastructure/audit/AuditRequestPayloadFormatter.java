package com.laker.admin.infrastructure.audit;

import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuditRequestPayloadFormatter {
    private final EasySensitiveDataMasker masker;
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditRequestPayloadFormatter(EasySensitiveDataMasker masker) {
        this.masker = masker;
    }

    public String format(Method method, Object[] args) {
        Object payload = payload(method, args);
        if (payload == null) {
            return null;
        }
        return masker.toSanitizedCompactJson(payload);
    }

    private Object payload(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (shouldSkipArgument(arg)) {
                continue;
            }
            String name = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            arguments.put(name, arg);
        }
        if (arguments.isEmpty()) {
            return null;
        }
        if (arguments.size() == 1) {
            Map.Entry<String, Object> entry = arguments.entrySet().iterator().next();
            Object value = entry.getValue();
            if (!isSimpleValue(value)) {
                return value;
            }
        }
        return arguments;
    }

    private boolean shouldSkipArgument(Object arg) {
        if (arg == null) {
            return true;
        }
        if (arg instanceof CharSequence text) {
            return !StringUtils.hasText(text);
        }
        return masker.isRequestInfrastructureValue(arg);
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>;
    }
}
