#!/usr/bin/env bash
set -euo pipefail
# Creates an admin account directly in Cognito. Admin accounts are never
# self-registered (see backend AuthController.register()'s doc comment) —
# this script, run out-of-band by an operator, is the only way one gets
# created. Sets a permanent password directly (like AuthController.register()
# already does) rather than Cognito's temporary-password/forced-reset flow,
# since that challenge isn't handled anywhere in this app's login endpoint.
#
# Usage: ./create-admin.sh <email> <name>
# Prompts for a password (hidden input) unless ADMIN_PASSWORD is set.

cd "$(dirname "$0")/.."
set -a
source .env.deploy
set +a

REGION="${COGNITO_REGION:?COGNITO_REGION not set in .env.deploy}"
POOL_ID="${COGNITO_USER_POOL_ID:?COGNITO_USER_POOL_ID not set in .env.deploy}"

EMAIL="${1:?Usage: $0 <email> <name>}"
NAME="${2:?Usage: $0 <email> <name>}"

if [ -z "${ADMIN_PASSWORD:-}" ]; then
  read -rsp "Password for $EMAIL: " ADMIN_PASSWORD
  echo
fi

echo "==> Creating Cognito user $EMAIL in $POOL_ID"
aws cognito-idp admin-create-user \
  --user-pool-id "$POOL_ID" \
  --username "$EMAIL" \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true Name=name,Value="$NAME" \
  --message-action SUPPRESS \
  --region "$REGION" >/dev/null

echo "==> Setting permanent password"
aws cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username "$EMAIL" \
  --password "$ADMIN_PASSWORD" \
  --permanent \
  --region "$REGION"

echo "==> Adding to the 'admin' group"
aws cognito-idp admin-add-user-to-group \
  --user-pool-id "$POOL_ID" \
  --username "$EMAIL" \
  --group-name admin \
  --region "$REGION"

echo "Done. $EMAIL can now sign in at /admin/login."
