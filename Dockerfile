# jar 빌드가 선행되어야 한다: ./gradlew bootJar && docker build -t devica .
FROM eclipse-temurin:21.0.11_10-jre-alpine
WORKDIR /app
USER nobody
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
