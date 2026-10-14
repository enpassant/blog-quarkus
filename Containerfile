FROM docker.io/library/eclipse-temurin:21-jre
ARG APP_DIR=modules/app
WORKDIR /app

# A Quarkus fast-jar needs the entire quarkus-app directory.
COPY --chown=1001:0 ${APP_DIR}/target/quarkus-app/ /app/
USER 1001
EXPOSE 8080
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
