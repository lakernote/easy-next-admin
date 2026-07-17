package com.laker.admin.module.business.number.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessNumberRuleMappingTest {

    @Test
    void businessNumberRuleUsesNonReservedSeparatorColumnName() throws NoSuchFieldException {
        assertThat(BusinessNumberRule.class.getDeclaredField("numberSeparator")).isNotNull();
        assertThat(BusinessNumberRule.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("separator");
    }
}
