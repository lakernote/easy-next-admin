package com.laker.admin.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EasyPageSupportTest {

    @Test
    void pageShouldNormalizeInvalidPaginationArguments() {
        Page<String> page = EasyPageSupport.page(0, 0);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
    }

    @Test
    void responseShouldMapRecordsAndKeepTotal() {
        Page<String> page = new Page<>(2, 5);
        page.setTotal(21);
        page.setRecords(List.of("alpha", "beta"));

        PageResponse<Integer> response = EasyPageSupport.response(page, String::length);

        assertThat(response.getData().list()).containsExactly(5, 4);
        assertThat(response.getData().total()).isEqualTo(21);
    }
}
