#!/usr/bin/env bash
set -euo pipefail
# Deploys the CloudFormation stacks in dependency order (storage -> iam ->
# security -> compute). Reads deploy-time parameters from infra/.env.deploy
# — copy infra/.env.deploy.example there first and fill in real values
# (gitignored, never committed). See infra/README.md.

cd "$(dirname "$0")/.."
set -a
source .env.deploy
set +a

REGION="${AWS_REGION:-us-east-1}"
ENV_NAME="${ENVIRONMENT_NAME:-islandcart-test}"

echo "==> storage.yaml"
aws cloudformation deploy \
  --template-file cloudformation/storage.yaml \
  --stack-name "${ENV_NAME}-storage" \
  --parameter-overrides EnvironmentName="$ENV_NAME" \
  --region "$REGION"

echo "==> iam.yaml"
aws cloudformation deploy \
  --template-file cloudformation/iam.yaml \
  --stack-name "${ENV_NAME}-iam" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnvironmentName="$ENV_NAME" \
  --region "$REGION"

echo "==> security.yaml"
aws cloudformation deploy \
  --template-file cloudformation/security.yaml \
  --stack-name "${ENV_NAME}-security" \
  --parameter-overrides EnvironmentName="$ENV_NAME" VpcId="$VPC_ID" SshAllowedCidr="$SSH_ALLOWED_CIDR" \
  --region "$REGION"

echo "==> compute.yaml"
aws cloudformation deploy \
  --template-file cloudformation/compute.yaml \
  --stack-name "${ENV_NAME}-compute" \
  --parameter-overrides EnvironmentName="$ENV_NAME" SubnetId="$SUBNET_ID" KeyName="$KEY_NAME" \
  --region "$REGION"

echo "==> Done. Outputs:"
aws cloudformation describe-stacks --stack-name "${ENV_NAME}-compute" --query "Stacks[0].Outputs" --region "$REGION"
echo
echo "Next: run scripts/put-secrets.sh, then scripts/sync-and-deploy.sh"
