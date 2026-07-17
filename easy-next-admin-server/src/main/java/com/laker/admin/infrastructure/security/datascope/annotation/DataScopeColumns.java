package com.laker.admin.infrastructure.security.datascope.annotation;

/**
 * 数据范围外层查询可见字段常量。
 */
public final class DataScopeColumns {
    /**
     * MyBatis-Plus 投影后的实体属性别名。
     */
    public static final String DEPT_ID = "deptId";
    public static final String USER_ID = "userId";
    public static final String CREATE_DEPT_ID = "createDeptId";
    public static final String CREATE_BY = "createBy";

    /**
     * 原 SQL 外层直接暴露数据库列名时使用。
     */
    public static final String DB_DEPT_ID = "dept_id";
    public static final String DB_CREATE_DEPT_ID = "create_dept_id";
    public static final String DB_CREATE_BY = "create_by";
    public static final String DB_ASSIGNEE_DEPT_ID = "assignee_dept_id";
    public static final String DB_ASSIGNEE_ID = "assignee_id";

    private DataScopeColumns() {
    }
}
