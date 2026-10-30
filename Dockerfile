# ==========================================
# Stage 1: Build Stage (Maven + OpenJDK 21)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy dependency definition file first to leverage Docker layer caching
COPY pom.xml .

# Download dependencies offline to speed up subsequent builds
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build executable JAR file, skipping unit tests during packaging
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Runtime Stage (JDK 21 Runtime)
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security best practices
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built JAR artifact from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose default Spring Boot application port
ENV PORT=8080
EXPOSE 8080



# Configure JVM memory limits and execute Spring Boot app
ENTRYPOINT ["java", "-Xms256m", "-Xmx384m", "-XX:+UseG1GC", "-jar", "app.jar"]