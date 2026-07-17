package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowVariableSnapshotService {

    private final EasyJsonCodec jsonCodec;
    private final WorkflowInstanceStateGuard instanceStateGuard;

    public Map<String, Object> startVariables(WfProcessDefinition definition,
                                              WfStartProcessRequest request,
                                              Long operatorId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("definitionId", definition.getId());
        variables.put("processKey", definition.getProcessKey());
        variables.put("businessType", request.getBusinessType());
        variables.put("businessId", request.getBusinessId());
        variables.put("title", request.getTitle());
        variables.put("initiatorId", operatorId);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        return variables;
    }

    public Map<String, Object> actionVariables(WfProcessInstance instance,
                                               WfTaskActionRequest request,
                                               Long operatorId) {
        Map<String, Object> variables = readInstanceVariables(instance);
        variables.put("instanceId", instance.getId());
        variables.put("processKey", instance.getProcessKey());
        variables.put("businessType", instance.getBusinessType());
        variables.put("businessId", instance.getBusinessId());
        variables.put("title", instance.getTitle());
        variables.put("initiatorId", instance.getInitiatorId());
        variables.put("operatorId", operatorId);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
            instance.setVariablesJson(jsonCodec.toJson(variables));
            instanceStateGuard.updateOrThrow(instance);
        }
        return variables;
    }

    public Map<String, Object> readInstanceVariables(WfProcessInstance instance) {
        if (!StringUtils.hasText(instance.getVariablesJson())) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(jsonCodec.fromJson(instance.getVariablesJson(), new TypeReference<Map<String, Object>>() {
        }));
    }
}
