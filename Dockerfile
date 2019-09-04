FROM openjdk:8-jre-alpine

RUN apk update && apk add wget bash tar

WORKDIR /app
ENV PATH $PATH:/app

RUN \
    wget https://get.helm.sh/helm-v3.0.0-beta.1-linux-amd64.tar.gz && \
    tar -zxvf helm-v3.0.0-beta.1-linux-amd64.tar.gz && \
    mv linux-amd64/helm /app/

ARG PRESTOK8S_VERSION
ENV JAR_FILE prestok8s-${PRESTOK8S_VERSION}-jar-with-dependencies.jar

COPY ./target/${JAR_FILE} /app
COPY ./config.yml /app
EXPOSE 8080 8090 8091

CMD java -jar $JAR_FILE server config.yml

