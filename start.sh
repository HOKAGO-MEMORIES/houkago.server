#!/bin/bash

docker stop houkago-server || true
docker rm houkago-server || true

source /home/ubuntu/app/.env

docker build -t houkago-server /home/ubuntu/app

docker run -d -p 80:80 \
  -e GITHUB_API_URL=$GITHUB_API_URL \
  -e GITHUB_IMAGE_URL=$GITHUB_IMAGE_URL \
  -e GITHUB_TOKEN=$GITHUB_TOKEN \
  -e ALLOWED_ORIGINS=$ALLOWED_ORIGINS \
  -e DATABASE_URL=$DATABASE_URL \
  -e DATABASE_USERNAME=$DATABASE_USERNAME \
  -e DATABASE_PASSWORD=$DATABASE_PASSWORD \
  --name houkago-server houkago-server
