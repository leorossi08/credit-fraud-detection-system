FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY services/transaction-gateway/pom.xml ./pom.xml
COPY services/transaction-gateway/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/transaction-gateway-1.0.0-SNAPSHOT.jar ./app.jar
COPY services/transaction-gateway/wait-for-db.sh ./wait-for-db.sh
RUN chmod +x ./wait-for-db.sh
EXPOSE 8080
ENTRYPOINT ["./wait-for-db.sh"]