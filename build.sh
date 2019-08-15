#!/usr/bin/env bash

set -euxo pipefail
PRESTOK8S_VERSION=$1
DOCKER_TAG="${DOCKER_ID_USER}/prestok8s:${PRESTOK8S_VERSION}"

mvn clean install

docker build ./ -f Dockerfile -t "${DOCKER_TAG}" --build-arg "PRESTOK8S_VERSION=${PRESTOK8S_VERSION}"
docker login -u ${DOCKER_ID_USER} -p ${DOCKER_ACCESS_TOKEN}
docker push "${DOCKER_TAG}"
