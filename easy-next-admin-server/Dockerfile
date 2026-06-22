# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

WORKDIR /app
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin --no-create-home app \
    && mkdir -p logs storage work \
    && chown -R app:app /app

COPY --chown=app:app easy-next-admin-server/target/easyNextAdmin.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
