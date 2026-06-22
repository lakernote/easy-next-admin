package com.laker.admin.module.workflow.sequence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.workflow.sequence.entity.BizRequestSequence;
import com.laker.admin.module.workflow.sequence.mapper.BizRequestSequenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class BusinessRequestNoService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int SEQUENCE_WIDTH = 6;

    private final BizRequestSequenceMapper sequenceMapper;

    public BusinessRequestNoService(BizRequestSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextRequestNo(String prefix) {
        if (!StringUtils.hasText(prefix) || !prefix.matches("[A-Z]{2,8}")) {
            throw new BusinessException("业务单号前缀不正确");
        }
        String datePart = DATE_FORMAT.format(LocalDate.now());
        String sequenceKey = prefix + ":" + datePart;
        long nextValue = nextValue(sequenceKey);
        return prefix + "-" + datePart + "-" + String.format("%0" + SEQUENCE_WIDTH + "d", nextValue);
    }

    private long nextValue(String sequenceKey) {
        ensureSequenceRow(sequenceKey);
        BizRequestSequence sequence = selectForUpdate(sequenceKey);
        if (sequence == null) {
            throw new BusinessException("业务单号序列不存在");
        }
        long nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceMapper.updateById(sequence);
        return nextValue;
    }

    private void ensureSequenceRow(String sequenceKey) {
        if (sequenceMapper.selectById(sequenceKey) != null) {
            return;
        }
        BizRequestSequence sequence = new BizRequestSequence();
        sequence.setSequenceKey(sequenceKey);
        sequence.setCurrentValue(0L);
        sequence.setUpdatedAt(LocalDateTime.now());
        try {
            sequenceMapper.insert(sequence);
        } catch (DuplicateKeyException ignored) {
            // Another request created today's sequence row first.
        }
    }

    private BizRequestSequence selectForUpdate(String sequenceKey) {
        return sequenceMapper.selectOne(new QueryWrapper<BizRequestSequence>()
                .eq("sequence_key", sequenceKey)
                .last("FOR UPDATE"));
    }
}
