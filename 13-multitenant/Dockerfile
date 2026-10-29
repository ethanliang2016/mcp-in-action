FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 先在本模块执行：mvn clean package -DskipTests
ARG JAR_FILE=target/multitenant-mcp.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
