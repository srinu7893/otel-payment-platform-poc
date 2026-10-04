FROM eclipse-temurin:21-jre
ARG OTEL_JAVA_AGENT_VERSION=2.19.0
ADD https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_JAVA_AGENT_VERSION}.jar /otel/opentelemetry-javaagent.jar
WORKDIR /app
COPY target/api-gateway-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java","-javaagent:/otel/opentelemetry-javaagent.jar","-jar","/app/app.jar"]
