FROM openjdk:8-jre-alpine

RUN apk update && apk add curl bash openssl sudo
RUN curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash

ARG PRESTOK8S_VERSION
ENV JAR_FILE prestok8s-${PRESTOK8S_VERSION}-jar-with-dependencies.jar

WORKDIR /app
COPY ./target/${JAR_FILE} /app
COPY ./config.yml /app
EXPOSE 8080 8090 8091

CMD java -jar $JAR_FILE server config.yml

