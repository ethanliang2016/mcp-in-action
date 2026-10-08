FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/store-ha-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
