FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY consolidation-service/pom.xml consolidation-service/pom.xml
COPY transaction-service/src transaction-service/src
COPY consolidation-service/src consolidation-service/src
ARG SERVICE
RUN mvn -B -ntp -pl ${SERVICE} -am package -DskipTests && cp ${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar /app.jar

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
