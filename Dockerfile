FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/campus-lost-found.war /usr/local/tomcat/webapps/ROOT.war
ENV CATALINA_OPTS="-Dcampus.data.dir=/data"
EXPOSE 8080
