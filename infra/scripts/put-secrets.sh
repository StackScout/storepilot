#!/usr/bin/env bash
set -euo pipefail
# Creates/updates the SSM SecureString parameters the EC2 instance reads at
# boot (see cloudformation/compute.yaml's UserData). CloudFormation can't
# create SecureString parameters natively — AWS::SSM::Parameter only
# supports the String/StringList types — which is why this is a script
# instead of a CFN template. Run this once after deploy.sh, and again any
# time you rotate a value (then re-run sync-and-deploy.sh to pick it up).

cd "$(dirname "$0")/.."
set -a
source .env.deploy
set +a

REGION="${AWS_REGION:-us-east-1}"

put() {
  aws ssm put-parameter --name "$1" --value "$2" --type SecureString --overwrite --region "$REGION" >/dev/null
  echo "  set $1"
}

echo "==> Writing secrets to SSM Parameter Store under /islandcart/*"
put /islandcart/db-username "$DB_USERNAME"
put /islandcart/db-password "$DB_PASSWORD"
put /islandcart/payhere-merchant-id "$PAYHERE_MERCHANT_ID"
put /islandcart/payhere-merchant-secret "$PAYHERE_MERCHANT_SECRET"
put /islandcart/ses-sender-email "$SES_SENDER_EMAIL"
put /islandcart/cognito-oauth-client-id "$COGNITO_OAUTH_CLIENT_ID"
put /islandcart/cognito-oauth-client-secret "$COGNITO_OAUTH_CLIENT_SECRET"
put /islandcart/google-client-id "$GOOGLE_CLIENT_ID"
put /islandcart/google-client-secret "$GOOGLE_CLIENT_SECRET"

echo "Done."
