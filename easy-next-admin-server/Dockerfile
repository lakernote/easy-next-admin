# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jre-jammy

# 服务端镜像默认面向生产部署；本地容器运行时显式覆盖为 local。
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

WORKDIR /app
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin --no-create-home app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p logs storage work \
    && chown -R app:app /app

COPY --chown=app:app easy-next-admin-server/target/easyNextAdmin.jar /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar \"$@\"", "--"]
