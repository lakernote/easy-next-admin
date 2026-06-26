package com.laker.admin.module.batch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.mapper.BatchTaskItemMapper;
import com.laker.admin.module.batch.service.IBatchTaskItemService;
import org.springframework.stereotype.Service;

@Service
public class BatchTaskItemServiceImpl extends ServiceImpl<BatchTaskItemMapper, BatchTaskItem>
        implements IBatchTaskItemService {
}
