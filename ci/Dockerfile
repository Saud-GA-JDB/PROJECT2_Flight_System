FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* && mkdir -p /app/uploads
COPY app.jar /app/app.jar
ENV SERVER_PORT=8080 TZ=Asia/Bahrain
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Bahrain"
EXPOSE 8080 8081 8083 8084 8085
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl --fail --silent --max-time 4 "http://localhost:${SERVER_PORT}/v3/api-docs" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
