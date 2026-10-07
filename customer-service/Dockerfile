FROM eclipse-temurin:21-jre AS app
ARG JAR_FILE=target/customer-service-0.1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

FROM app AS cloud
COPY target/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar"
EXPOSE 8080

# Default target preserves the local Compose image without requiring the agent.
FROM app AS local
