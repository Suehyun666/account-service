####
# Dockerfile for Quarkus with GraalVM (Local Build)
# 사용 전에 로컬에서 먼저 빌드: ./gradlew build
####

FROM ghcr.io/graalvm/jdk-community:21.3.4 AS graalvm

# Set working directory
WORKDIR /app

# Copy the pre-built application from local build directory
COPY build/quarkus-app/lib/ ./lib/
COPY build/quarkus-app/*.jar ./
COPY build/quarkus-app/app/ ./app/
COPY build/quarkus-app/quarkus/ ./quarkus/

# Expose ports
EXPOSE 50052
EXPOSE 9052

# Set environment variables for production
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV QUARKUS_GRPC_SERVER_PORT=50052
ENV QUARKUS_HTTP_PORT=9052

# Run the application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]