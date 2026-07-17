package com.laker.admin.module.schedule.core;

import java.util.Map;

public interface EasyJobHandler {

    void execute(Map<String, Object> params) throws Exception;
}
