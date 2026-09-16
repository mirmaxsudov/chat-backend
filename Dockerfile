FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S lms && adduser -S lms -G lms
RUN mkdir -p /app/logs && chown -R lms:lms /app/logs

COPY --from=build /workspace/target/*.jar app.jar

USER lms

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
