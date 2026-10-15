package com.laker.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 *
 * @author laker
 */
@SpringBootApplication
@EnableScheduling
public class EasyNextAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasyNextAdminApplication.class, args);
    }
}
