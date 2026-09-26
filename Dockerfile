# Stage 1: Build the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /app

# Copy pom and source code
COPY pom.xml .
COPY src ./src

# Package the application (skip tests for faster deployment)
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install default-mysql-client so 'mysqldump' is available in the Linux environment
RUN apt-get update && \
    apt-get install -y default-mysql-client && \
    rm -rf /var/lib/apt/lists/*

# Copy the built jar from the builder stage
COPY --from=builder /app/target/tidb-db-downloader-0.0.1-SNAPSHOT.jar app.jar

# Render exposes the port via the PORT environment variable (default 10000)
ENV PORT=8080
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
