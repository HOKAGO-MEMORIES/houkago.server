#!/bin/bash

source /home/ubuntu/app/.env

AWS_REGION="ap-northeast-2"
ECR_REPO_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/houkago-repo"

aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPO_URL}

docker stop houkago-server || true
docker rm houkago-server || true

docker pull ${ECR_REPO_URL}:latest

docker run -d -p 80:80 \
  -e GITHUB_API_URL=${GITHUB_API_URL} \
  -e GITHUB_IMAGE_URL=${GITHUB_IMAGE_URL} \
  -e GITHUB_TOKEN=${GITHUB_TOKEN} \
  -e ALLOWED_ORIGINS=${ALLOWED_ORIGINS} \
  -e DATABASE_URL=${DATABASE_URL} \
  -e DATABASE_USERNAME=${DATABASE_USERNAME} \
  -e DATABASE_PASSWORD=${DATABASE_PASSWORD} \
  --restart unless-stopped \
  --name houkago-server ${ECR_REPO_URL}:latest