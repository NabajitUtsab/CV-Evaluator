# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only the pom first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the rest of the source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user to run the app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Spring Boot default port
EXPOSE 8080

# Required at runtime — pass with `docker run -e OPENROUTER_API_KEY=...`
ENV OPENROUTER_API_KEY=""

ENTRYPOINT ["java", "-jar", "app.jar"]