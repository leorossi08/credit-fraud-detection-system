FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY services/fraud-analyzer/pom.xml ./pom.xml
COPY services/fraud-analyzer/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/fraud-analyzer-1.0.0-SNAPSHOT.jar ./app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]