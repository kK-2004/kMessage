FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/kmessage-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
