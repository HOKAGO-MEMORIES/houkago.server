FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./

RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system houkago && useradd --system --gid houkago houkago

COPY --from=build /workspace/build/libs/*.jar /app/houkago.server.jar

USER houkago

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/houkago.server.jar"]
