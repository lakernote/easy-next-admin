package com.laker.admin.infrastructure.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EasyIdGeneratorTest {

    @Test
    void uuid32ShouldGenerateLowercaseUuidWithoutHyphen() {
        String value = EasyIdGenerator.uuid32();

        assertThat(value).hasSize(32);
        assertThat(value).matches("[0-9a-f]{32}");
    }
}
