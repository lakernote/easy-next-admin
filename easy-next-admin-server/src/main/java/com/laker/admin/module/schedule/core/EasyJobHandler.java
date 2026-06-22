package com.laker.admin.module.schedule.core;

import java.util.Map;

public interface EasyJobHandler {

    void execute(Map map) throws Exception;
}
