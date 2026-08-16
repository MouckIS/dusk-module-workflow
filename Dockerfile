FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 1001 --create-home dusk \
    && mkdir -p /app/logs \
    && chown -R dusk:dusk /app

# 直接将本地解压好的 target/extracted 目录分层复制进去
COPY --chown=dusk:dusk target/extracted/dependencies/ ./
COPY --chown=dusk:dusk target/extracted/spring-boot-loader/ ./
COPY --chown=dusk:dusk target/extracted/snapshot-dependencies/ ./
COPY --chown=dusk:dusk target/extracted/application/ ./

USER dusk

ENV SPRING_PROFILES_ACTIVE=sit \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
