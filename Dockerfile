FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Internal dusk-* parent POMs and libraries (dusk-module-parent,
# dusk-module-shared-parent, dusk-common) are hosted on GitHub Packages,
# not Maven Central. The project settings.xml declares the servers/repos
# with the credential read from the GH_PAT environment variable.
# Provide a PAT with read:packages scope:
#   docker build --build-arg GH_PAT=<PAT> -t dusk-module-workflow .
ARG GH_PAT=""

# Use the project's Maven settings and export GH_PAT so ${env.GH_PAT} in
# settings.xml resolves to the credential.
COPY settings.xml /root/.m2/settings.xml
ENV GH_PAT=${GH_PAT}

COPY . .

# 1) Build the whole reactor (installs dusk-module-workflow-shared into the
#    local repo so the next step can resolve it).
# 2) Repackage ONLY the service module into the executable Spring Boot fat
#    jar. Plain `mvn package` only produces a thin jar here because the
#    spring-boot-maven-plugin is declared in pluginManagement but not bound
#    to the build lifecycle.
RUN mvn -B -ntp -U -DskipTests clean install \
    && mvn -B -ntp -U -DskipTests -f dusk-module-workflow/pom.xml \
       org.springframework.boot:spring-boot-maven-plugin:3.2.12:repackage

##############################
# Stage 2: Runtime
##############################
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 1001 --create-home dusk \
    && mkdir -p /app/logs \
    && chown -R dusk:dusk /app

COPY --from=build --chown=dusk:dusk \
     /workspace/dusk-module-workflow/target/dusk-module-workflow.jar /app/app.jar

USER dusk

ENV SPRING_PROFILES_ACTIVE=sit \
    SERVER_PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
