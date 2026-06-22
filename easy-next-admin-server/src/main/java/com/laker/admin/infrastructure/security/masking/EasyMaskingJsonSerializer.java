package com.laker.admin.infrastructure.security.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * {@link EasyMask} 的 Jackson 字段序列化器。Jackson 自行创建序列化器实例，因此这里不依赖 Spring Bean。
 */
public class EasyMaskingJsonSerializer extends JsonSerializer<Object> implements ContextualSerializer {
    private final EasyMask mask;

    public EasyMaskingJsonSerializer() {
        this(null);
    }

    private EasyMaskingJsonSerializer(EasyMask mask) {
        this.mask = mask;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        EasyMaskType type = mask == null ? EasyMaskType.FULL : mask.type();
        int prefix = mask == null ? 0 : mask.prefix();
        int suffix = mask == null ? 0 : mask.suffix();
        gen.writeString(EasyMaskingSupport.mask(type, String.valueOf(value), prefix, suffix));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        EasyMask annotation = null;
        if (property != null) {
            annotation = property.getAnnotation(EasyMask.class);
            if (annotation == null) {
                annotation = property.getContextAnnotation(EasyMask.class);
            }
        }
        return new EasyMaskingJsonSerializer(annotation);
    }
}
