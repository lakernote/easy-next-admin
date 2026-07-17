package com.laker.admin.module.monitor.controller;

import com.laker.admin.infrastructure.observability.actuator.EasyNextAdminHealthIndicator;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author easynext
 * @since 2024-04-03
 */
@Tag(name = "服务监控", description = "Spring Boot Actuator 提供生产可用的健康检查和服务信息。")
@Controller
@RequestMapping("/api/monitor/actuator")
@EasyPermission(EasyPermissions.Monitor.SERVER_VIEW)
public class ActuatorController {


    /**
     * <p>
     * /health原理
     * Spring boot的健康信息都是从ApplicationContext中的各种HealthIndicator Beans中收集到的，Spring boot框架中包含了大量的HealthIndicators的实现类，当然你也可以实现自己认为的健康状态。
     * 默认情况下，最终的spring boot应用的状态是由HealthAggregator汇总而成的，汇总的算法是：
     * 1.设置状态码顺序：setStatusOrder(Status.DOWN, Status.OUT_OF_SERVICE, Status.UP, Status.UNKNOWN)。
     * 2.过滤掉不能识别的状态码。
     * 3.如果无任何状态码，整个spring boot应用的状态是 UNKNOWN。
     * 4.将所有收集到的状态码按照 1 中的顺序排序。
     * 5.返回有序状态码序列中的第一个状态码，作为整个spring boot应用的状态。
     * 源代码请参见：org.springframework.boot.actuate.health.OrderedHealthAggregator。
     * </p>
     * 自定义 HealthIndicator参见
     *
     * @see EasyNextAdminHealthIndicator
     */
    @GetMapping("/health")
    @Operation(summary = "1. 查看当前 Spring Boot 运行健康状态，由 HealthIndicator 的实现类提供")
    public String health() {
        return "redirect:/actuator/health";
    }

    @GetMapping("/health/ping")
    @Operation(summary = "2. http://localhost:8080/actuator/health/ping")
    public String healthPing() {
        return "redirect:/actuator/health/ping";
    }

    @GetMapping("/health/liveness")
    @Operation(summary = "3. 给k8s用的探针-存活接口")
    public String healthLiveness() {
        return "redirect:/actuator/health/liveness";
    }

    @GetMapping("/health/readiness")
    @Operation(summary = "4. 给k8s用的探针-就绪接口")
    public String healthReadiness() {
        return "redirect:/actuator/health/readiness";
    }

    /**
     * 自定义类继承自 InfoContributor
     */
    @GetMapping("/info")
    @Operation(summary = "5. 查看服务版本,Git信息,properties中info开头的属性", description = "自定义类继承自 InfoContributor")
    public String info() {
        return "redirect:/actuator/info";
    }

}
