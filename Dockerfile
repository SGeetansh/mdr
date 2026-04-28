FROM maven:3.9-eclipse-temurin-17

WORKDIR /workspace

COPY pom.xml .
RUN mvn dependency:go-offline -B

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]
