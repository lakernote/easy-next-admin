package com.laker.admin.module.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WfProcessInstanceDetail {
    private WfProcessInstanceView instance;
    private WfProcessDefinitionView definition;
    private WfProcessDefinitionVersionView version;
    private String graphJson;
    private Map<String, Object> variables;
    private List<WfParticipantView> participants;
    private List<WfTaskView> tasks;
    private List<WfEventView> events;
    private List<WfCcView> ccList;
}
