package com.laker.admin.config.properties;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 自定义配置
 *
 * @author laker
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "easy")
public class EasyNextAdminConfig {
    /**
     * log配置
     */
    private String logFilePath = "logs/easy-next-admin.log";

    /**
     * 用户初始密码
     */
    private String defaultPwd = "easynext";

    /**
     * 防火墙
     */
    private Waf waf = new Waf();

    /**
     * Web 安全边界配置。
     */
    private Web web = new Web();

    /**
     * 文件存储
     */
    private Storage storage = new Storage();

    private Auth auth = new Auth();

    private Features features = new Features();

    @Data
    public static class Features {
        private boolean monitor = true;
    }

    @Data
    public static class Waf {
        private boolean xssEnabled = true;
        private boolean sqlEnabled = true;
        private String excludes = "";
    }

    @Data
    public static class Web {
        private Cors cors = new Cors();
        private SecurityHeaders securityHeaders = new SecurityHeaders();

        @Data
        public static class Cors {
            /**
             * 明确允许的前端 Origin。生产环境应替换为真实域名。
             */
            private List<String> allowedOrigins = List.of(
                    "http://localhost:5174",
                    "http://127.0.0.1:5174",
                    "http://localhost:5175",
                    "http://127.0.0.1:5175",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173"
            );

            /**
             * 支持 Spring simple pattern，例如 https://*.example.com。
             */
            private List<String> allowedOriginPatterns = List.of();

            private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
            private List<String> allowedHeaders = List.of("*");
            private List<String> exposedHeaders = List.of(EasyNextAdminConstants.TRACE_ID_HEADER);
            private boolean allowCredentials = true;
            private long maxAgeSeconds = 3600L;
        }

        @Data
        public static class SecurityHeaders {
            private String frameOptions = "SAMEORIGIN";
            private String contentSecurityPolicy = "";
            private boolean hstsEnabled = false;
            private String hsts = "max-age=31536000; includeSubDomains";
            private String referrerPolicy = "strict-origin-when-cross-origin";
            private String permissionsPolicy = "geolocation=(), microphone=(), camera=()";
        }
    }

    @Data
    public static class Storage {
        private Local local = new Local();
        private Aliyun aliyun = new Aliyun();
    }

    @Data
    public static class Auth {
        private Session session = new Session();

        @Data
        public static class Session {
            /**
             * 会话空闲超时时间；用户持续操作时按该时间滑动续约。
             */
            private Duration idleTimeout = Duration.ofMinutes(30);

            /**
             * 会话绝对超时时间；达到后即使持续操作也必须重新登录。
             */
            private Duration absoluteTimeout = Duration.ofHours(8);
        }
    }

    @Data
    public static class Local {
        private boolean enable = true;
        private String address = "http://localhost:8080";
        private String storagePath = "storage";

    }

    @Data
    public static class Aliyun {
        private boolean enable;
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
    }

}
