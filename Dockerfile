# ── STAGE 1: Build ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (for dependency caching)
COPY CV-Evaluator/pom.xml .
COPY CV-Evaluator/mvnw .
COPY CV-Evaluator/.mvn .mvn

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY CV-Evaluator/src ./src

# Build the JAR, skip tests
RUN ./mvnw package -DskipTests -B

# ── STAGE 2: Run ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]