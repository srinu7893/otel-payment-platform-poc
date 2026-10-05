FROM eclipse-temurin:21-jre
ARG SERVICE
COPY ${SERVICE}/target/*.jar /app.jar
COPY observability/agent/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -Xms64m -Xmx384m"
ENTRYPOINT ["java","-jar","/app.jar"]
