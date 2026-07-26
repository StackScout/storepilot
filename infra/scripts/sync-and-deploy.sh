#!/usr/bin/env bash
set -euo pipefail
# Ships the repo to the EC2 instance and (re)starts the Docker Compose
# stack. Run this after deploy.sh + put-secrets.sh, and again any time you
# push a code change or rotate a secret in SSM.

cd "$(dirname "$0")/.."
set -a
source .env.deploy
set +a

REGION="${AWS_REGION:-us-east-1}"
ENV_NAME="${ENVIRONMENT_NAME:-islandcart-test}"

INSTANCE_IP=$(aws cloudformation describe-stacks --stack-name "${ENV_NAME}-compute" \
  --query "Stacks[0].Outputs[?OutputKey=='PublicIp'].OutputValue" --output text --region "$REGION")

echo "==> Syncing repo to ec2-user@${INSTANCE_IP}:/opt/islandcart"
rsync -avz --delete \
  --exclude .git --exclude node_modules --exclude .next --exclude build --exclude .gradle --exclude .kotlin \
  --exclude "backend/src/main/resources/config.yml" --exclude "infra/.env.deploy" \
  -e "ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=accept-new" \
  ../ "ec2-user@${INSTANCE_IP}:/opt/islandcart/"

echo "==> Starting the stack (rebuilds images on first run / after code changes)"
ssh -i "${SSH_KEY_PATH}" "ec2-user@${INSTANCE_IP}" \
  "cd /opt/islandcart/infra/docker && docker compose -f docker-compose.prod.yml --env-file .env up -d --build"

SITE_ADDRESS=$(aws cloudformation describe-stacks --stack-name "${ENV_NAME}-compute" \
  --query "Stacks[0].Outputs[?OutputKey=='SiteAddress'].OutputValue" --output text --region "$REGION")
echo "==> Done. App should be reachable at: $SITE_ADDRESS"
echo "(Give Caddy a minute or two to obtain its TLS certificate on first boot.)"
