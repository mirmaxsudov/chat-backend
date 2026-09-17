FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system lms && useradd --system --gid lms --home-dir /app --shell /usr/sbin/nologin lms
RUN mkdir -p /app/logs && chown -R lms:lms /app/logs

COPY --from=build /workspace/target/*.jar app.jar

USER lms

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
