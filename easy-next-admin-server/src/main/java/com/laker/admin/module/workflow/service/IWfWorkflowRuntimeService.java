package com.laker.admin.module.workflow.service;

import com.laker.admin.module.workflow.dto.WfInstanceActionRequest;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;

public interface IWfWorkflowRuntimeService {
    WfProcessInstanceDetail start(WfStartProcessRequest request);

    WfProcessInstanceDetail detail(Long instanceId);

    WfProcessInstanceDetail approve(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail reject(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail transfer(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail delegate(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail returnTask(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail addSign(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail removeSign(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail remind(Long taskId, WfTaskActionRequest request);

    WfProcessInstanceDetail revoke(Long instanceId, WfInstanceActionRequest request);

    WfProcessInstanceDetail terminate(Long instanceId, WfInstanceActionRequest request);
}
