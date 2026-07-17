package com.laker.admin.module.workflow.support;

public enum WorkflowEventAction {
    SUBMIT,
    APPROVE,
    REJECT,
    RETURN,
    REVOKE,
    TRANSFER,
    DELEGATE,
    ADD_SIGN,
    REMOVE_SIGN,
    CC,
    REMIND,
    TERMINATE;

    public String code() {
        return name();
    }
}
