FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY tradepulse-kafka ./tradepulse-kafka
COPY tradepulse ./tradepulse
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/tradepulse/target/tradepulse-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
