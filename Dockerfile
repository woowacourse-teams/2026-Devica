# stage 1 - jar 파일을 빌드한다
FROM eclipse-temurin:21.0.11_10-jdk-alpine AS build
COPY . /src
WORKDIR /src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# stage 2 - 빌드한 jar 파일을 바탕으로 Docker 이미지를 빌드한다
FROM eclipse-temurin:21.0.11_10-jre-alpine
WORKDIR /app
USER nobody
COPY --from=build /src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
