FROM maven:3.6.3-jdk-11-slim as maven
WORKDIR /application
COPY pom.xml pom.xml
RUN mvn dependency:go-offline package -B
COPY src src
COPY resources resources
RUN mvn package

FROM openjdk:11.0.5-jdk-stretch
WORKDIR /application
COPY --from=maven /application/target/image-gallery-0.0.1-jar-with-dependencies.jar app.jar
CMD ["java", "-jar", "app.jar"]
