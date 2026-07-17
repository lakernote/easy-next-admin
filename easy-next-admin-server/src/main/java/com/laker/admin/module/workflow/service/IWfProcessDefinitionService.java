package com.laker.admin.module.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionDetail;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionRequest;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;

public interface IWfProcessDefinitionService extends IService<WfProcessDefinition> {

    PageResponse<WfProcessDefinitionView> pageDefinitions(long page, long limit, String keyword, String status);

    WfProcessDefinitionDetail detail(Long id);

    WfProcessDefinitionDetail saveDefinition(WfProcessDefinitionRequest request);

    boolean updateStatus(Long id, String status);

    WfProcessDefinitionDetail publish(Long id);

    boolean deleteDefinition(Long id);
}
