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
# Scoped per-environment — a second deployment (e.g. an Australia stack
# alongside this Sri Lanka one, same ../.env.deploy.example vars, different
# ENVIRONMENT_NAME) must NOT overwrite this one's secrets. iam.yaml's SSM
# policy is already a wildcard on /storepilot/* so it still covers this.
ENV_NAME="${ENVIRONMENT_NAME:-storepilot-test}"
PREFIX="/storepilot/$ENV_NAME"

put() {
  # SSM rejects an empty string value outright — some of these (Stripe,
  # ABR) are legitimately unconfigured/optional (see application.yml's
  # empty-string defaults), so skip writing rather than erroring. UserData's
  # get_param falls back to "" for a parameter that was never written.
  if [ -z "$2" ]; then
    echo "  skip $1 (empty)"
    return
  fi
  aws ssm put-parameter --name "$1" --value "$2" --type SecureString --overwrite --region "$REGION" >/dev/null
  echo "  set $1"
}

echo "==> Writing secrets to SSM Parameter Store under $PREFIX/*"
put "$PREFIX/db-username" "$DB_USERNAME"
put "$PREFIX/db-password" "$DB_PASSWORD"
put "$PREFIX/payhere-merchant-id" "$PAYHERE_MERCHANT_ID"
put "$PREFIX/payhere-merchant-secret" "$PAYHERE_MERCHANT_SECRET"
put "$PREFIX/stripe-secret-key" "$STRIPE_SECRET_KEY"
put "$PREFIX/stripe-publishable-key" "$STRIPE_PUBLISHABLE_KEY"
put "$PREFIX/stripe-webhook-secret" "$STRIPE_WEBHOOK_SECRET"
put "$PREFIX/stripe-billing-webhook-secret" "$STRIPE_BILLING_WEBHOOK_SECRET"
put "$PREFIX/abr-guid" "$ABR_GUID"
put "$PREFIX/ses-sender-email" "$SES_SENDER_EMAIL"
put "$PREFIX/cognito-oauth-client-id" "$COGNITO_OAUTH_CLIENT_ID"
put "$PREFIX/cognito-oauth-client-secret" "$COGNITO_OAUTH_CLIENT_SECRET"

echo "Done."
