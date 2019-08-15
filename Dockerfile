FROM openjdk:8-jre-alpine

ARG PRESTOK8S_VERSION

WORKDIR /app
COPY ./target/prestok8s-${PRESTOK8S_VERSION}-jar-with-dependencies.jar /app
COPY ./config.yml /app
EXPOSE 8080 8090 8091
CMD ["java",  "-jar",  "prestok8s-${PRESTOK8S_VERSION}-jar-with-dependencies.jar", "server", "config.yml"]

