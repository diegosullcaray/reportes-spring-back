FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/task-reportes-back-*.jar app.jar
ENV TZ=America/Lima
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
