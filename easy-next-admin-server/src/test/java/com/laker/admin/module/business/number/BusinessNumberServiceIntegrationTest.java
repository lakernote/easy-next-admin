package com.laker.admin.module.business.number;

import com.laker.admin.module.business.number.entity.BusinessNumberSequence;
import com.laker.admin.module.business.number.mapper.BusinessNumberSequenceMapper;
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

    @Autowired
    private BusinessNumberSequenceMapper sequenceMapper;

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

    @Test
    void sequenceMapperAtomicallyIncrementsCurrentValue() {
        BusinessNumberSequence sequence = new BusinessNumberSequence();
        sequence.setSequenceKey("TEST_RULE:20260626");
        sequence.setRuleCode("TEST_RULE");
        sequence.setSegment("20260626");
        sequence.setCurrentValue(10L);
        sequenceMapper.insert(sequence);

        int updated = sequenceMapper.incrementCurrentValue("TEST_RULE:20260626", 5);
        Long currentValue = sequenceMapper.selectCurrentValue("TEST_RULE:20260626");

        assertThat(updated).isEqualTo(1);
        assertThat(currentValue).isEqualTo(15L);
    }

    private long sequenceValue(String requestNo) {
        return Long.parseLong(requestNo.substring(requestNo.lastIndexOf('-') + 1));
    }
}
