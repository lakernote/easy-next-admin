package com.laker.admin.infrastructure.observability.actuator;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

@Component
public class EasyNextAdminHealthIndicator extends AbstractHealthIndicator {

    @Override
    public void doHealthCheck(Health.Builder builder) throws Exception {
        builder.up()
                .withDetail("code", "OK")
                .withDetail("msg", "EasyNextAdmin application is ready.");
    }

}
