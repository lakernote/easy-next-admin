package com.laker.admin.infrastructure.security.permission;

/**
 * 平台内置权限码集中定义。
 *
 * <p>Controller 注解、初始化 SQL、前端能力声明必须使用同一套语义，避免权限字符串散落后难以维护。
 * 新业务模块可以在自己的模块内定义同名风格的权限常量，但权限码必须保持全局唯一。</p>
 */
public final class EasyPermissions {

    private EasyPermissions() {
    }

    public static final class Dashboard {
        public static final String VIEW = "dashboard:view";

        private Dashboard() {
        }
    }

    public static final class Auth {
        public static final String SESSION_REVOKE = "auth:session:revoke";

        private Auth() {
        }
    }

    public static final class Profile {
        public static final String VIEW = "profile:view";
        public static final String EDIT = "profile:edit";
        public static final String PASSWORD_CHANGE = "profile:password:change";
        public static final String SESSION_MANAGE = "profile:session:manage";

        private Profile() {
        }
    }

    public static final class System {
        public static final String USER_LIST = "sys:user:list";
        public static final String USER_ADD = "sys:user:add";
        public static final String USER_EDIT = "sys:user:edit";
        public static final String USER_DELETE = "sys:user:delete";
        public static final String USER_IMPORT = "sys:user:import";
        public static final String USER_EXPORT = "sys:user:export";
        public static final String USER_RESET_PASSWORD = "sys:user:reset-password";
        public static final String ROLE_LIST = "sys:role:list";
        public static final String ROLE_EDIT = "sys:role:edit";
        public static final String MENU_LIST = "sys:menu:list";
        public static final String MENU_EDIT = "sys:menu:edit";
        public static final String DEPT_LIST = "sys:dept:list";
        public static final String DEPT_EDIT = "sys:dept:edit";
        public static final String FILE_LIST = "sys:file:list";
        public static final String FILE_UPLOAD = "sys:file:upload";
        public static final String FILE_DELETE = "sys:file:delete";

        private System() {
        }
    }

    public static final class Monitor {
        public static final String SERVER_VIEW = "monitor:server:view";
        public static final String ONLINE_VIEW = "monitor:online:view";
        public static final String CACHE_VIEW = "monitor:cache:view";
        public static final String CACHE_CLEAR = "monitor:cache:clear";
        public static final String WEBLOG_VIEW = "monitor:weblog:view";
        public static final String WEBLOG_LEVEL = "monitor:weblog:level";

        private Monitor() {
        }
    }

    public static final class Audit {
        public static final String BEHAVIOR_VIEW = "audit:behavior:view";

        private Audit() {
        }
    }

    public static final class Report {
        public static final String VIEW = "report:view";

        private Report() {
        }
    }

    public static final class Message {
        public static final String VIEW = "message:view";
        public static final String READ = "message:read";

        private Message() {
        }
    }

    public static final class Developer {
        public static final String API_DOCS_VIEW = "developer:api-docs:view";

        private Developer() {
        }
    }

    public static final class Schedule {
        public static final String JOB_LIST = "schedule:job:list";
        public static final String JOB_EDIT = "schedule:job:edit";

        private Schedule() {
        }
    }

    public static final class Workflow {
        public static final String VIEW = "workflow:view";
        public static final String DEFINITION_EDIT = "workflow:definition:edit";
        public static final String INSTANCE_START = "workflow:instance:start";
        public static final String INSTANCE_MANAGE = "workflow:instance:manage";
        public static final String INSTANCE_REVOKE = "workflow:instance:revoke";
        public static final String INSTANCE_TERMINATE = "workflow:instance:terminate";
        public static final String TASK_APPROVE = "workflow:task:approve";
        public static final String TASK_REJECT = "workflow:task:reject";
        public static final String TASK_TRANSFER = "workflow:task:transfer";
        public static final String TASK_DELEGATE = "workflow:task:delegate";
        public static final String TASK_RETURN = "workflow:task:return";
        public static final String TASK_ADD_SIGN = "workflow:task:add-sign";
        public static final String TASK_REMOVE_SIGN = "workflow:task:remove-sign";
        public static final String TASK_REMIND = "workflow:task:remind";

        private Workflow() {
        }
    }
}
