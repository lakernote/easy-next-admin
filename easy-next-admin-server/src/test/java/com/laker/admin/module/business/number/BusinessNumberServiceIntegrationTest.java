package com.laker.admin.module.business.number;

import com.laker.admin.module.business.number.service.BusinessNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class BusinessNumberServiceIntegrationTest {

    @Autowired
    private BusinessNumberService businessNumberService;

    @Test
    void seededRulesGenerateIndependentDailyBusinessNumbers() {
        String firstPurchaseNo = businessNumberService.nextNumber("PURCHASE_REQUEST");
        String secondPurchaseNo = businessNumberService.nextNumber("PURCHASE_REQUEST");
        String firstRepairNo = businessNumberService.nextNumber("REPAIR_REQUEST");

        assertThat(firstPurchaseNo).matches("PR-\\d{8}-\\d{6}");
        assertThat(secondPurchaseNo).matches("PR-\\d{8}-\\d{6}");
        assertThat(firstRepairNo).matches("RP-\\d{8}-\\d{6}");
        assertThat(sequenceValue(secondPurchaseNo)).isEqualTo(sequenceValue(firstPurchaseNo) + 1);
        assertThat(sequenceValue(firstRepairNo)).isEqualTo(1);
    }

    private long sequenceValue(String requestNo) {
        return Long.parseLong(requestNo.substring(requestNo.lastIndexOf('-') + 1));
    }
}
