package com.laker.admin.infrastructure.security.datascope.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTypeTest {

    @Test
    void shouldResolveStableRoleDataScopeCodes() {
        assertThat(DataScopeType.resolveRoleDataScope("ALL"))
                .contains(DataScopeType.ALL);
        assertThat(DataScopeType.resolveRoleDataScope("DEPT_SETS"))
                .contains(DataScopeType.DEPT_SETS);
    }

    @Test
    void shouldRejectUnknownOrNonStandardScopeValues() {
        assertThat(DataScopeType.resolveRoleDataScope("ALL_DATA")).isEmpty();
        assertThat(DataScopeType.resolveRoleDataScope("DEPT_SET")).isEmpty();
        assertThat(DataScopeType.resolveRoleDataScope("DEPT_TREE")).isEmpty();
    }
}
