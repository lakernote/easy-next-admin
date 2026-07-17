package com.laker.admin.module.assistant.tool;

/**
 * Tool 的业务主体作用域，用于向 Agent Guardrail 描述“这个 Tool 实际查谁/作用于谁”。
 *
 * <p>它不是权限模型：权限和数据范围仍由 Tool Visibility/Execution Policy 与领域服务确定。</p>
 */
public enum ToolSubjectScope {
    /** 没有人或业务对象主体，例如纯计算、内部结构化审查。 */
    NONE,
    /** 主体固定为当前登录用户，模型不能用参数把它改成其他人。 */
    CURRENT_USER,
    /** 主体由稳定标识指定，Tool 必须在执行时校验当前用户对目标的可见或操作权限。 */
    AUTHORIZED_TARGET
}
