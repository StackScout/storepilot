#!/usr/bin/env bash
set -euo pipefail
# Deploys the CloudFormation stacks in dependency order (storage -> iam ->
# security -> compute -> cicd). Reads deploy-time parameters from
# infra/.env.deploy — copy infra/.env.deploy.example there first and fill
# in real values (gitignored, never committed). See infra/README.md.

cd "$(dirname "$0")/.."
set -a
source .env.deploy
set +a

REGION="${AWS_REGION:-us-east-1}"
ENV_NAME="${ENVIRONMENT_NAME:-storepilot-test}"

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
    CognitoRegion="$COGNITO_REGION" \
    CognitoUserPoolId="$COGNITO_USER_POOL_ID" \
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
    PlatformName="${PLATFORM_NAME:-StorePilot}" \
    PlatformTagline="${PLATFORM_TAGLINE:-Australia’s marketplace for small business sellers}" \
    PlatformCountryName="${PLATFORM_COUNTRY_NAME:-Australia}" \
    PlatformCountryCode="${PLATFORM_COUNTRY_CODE:-AU}" \
    PlatformCurrencyCode="${PLATFORM_CURRENCY_CODE:-AUD}" \
    PlatformCurrencySymbol="${PLATFORM_CURRENCY_SYMBOL:-\$}" \
    PlatformCurrencyLocale="${PLATFORM_CURRENCY_LOCALE:-en-AU}" \
    PlatformFeePercent="${PLATFORM_FEE_PERCENT:-3.5}" \
    PlatformFlatShippingFee="${PLATFORM_FLAT_SHIPPING_FEE:-10}" \
    PlatformDefaultCodEnabled="${PLATFORM_DEFAULT_COD_ENABLED:-true}" \
    PlatformDefaultOnlinePaymentEnabled="${PLATFORM_DEFAULT_ONLINE_PAYMENT_ENABLED:-false}" \
    PlatformDefaultBankTransferEnabled="${PLATFORM_DEFAULT_BANK_TRANSFER_ENABLED:-true}" \
    SupportEmail="${SUPPORT_EMAIL:-hello@storepilot.au}" \
    CompanyLocation="${COMPANY_LOCATION:-Sydney, Australia}" \
    CognitoRegion="$COGNITO_REGION" \
    CognitoUserPoolId="$COGNITO_USER_POOL_ID" \
    CognitoClientId="$COGNITO_CLIENT_ID" \
    CognitoOauthDomain="$COGNITO_OAUTH_DOMAIN" \
  --region "$REGION"

echo "==> cicd.yaml"
aws cloudformation deploy \
  --template-file cloudformation/cicd.yaml \
  --stack-name "${ENV_NAME}-cicd" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnvironmentName="$ENV_NAME" \
    GitHubOrg="${GITHUB_ORG:-StackScout}" \
    GitHubRepo="${GITHUB_REPO:-storepilot}" \
    GitHubOrgId="$GITHUB_ORG_ID" \
    GitHubRepoId="$GITHUB_REPO_ID" \
    CreateOidcProvider="${CREATE_OIDC_PROVIDER:-true}" \
    ExistingOidcProviderArn="${EXISTING_OIDC_PROVIDER_ARN:-}" \
    GithubOidcThumbprint="${GITHUB_OIDC_THUMBPRINT}" \
  --region "$REGION"

echo "==> Done. Outputs:"
aws cloudformation describe-stacks --stack-name "${ENV_NAME}-compute" --query "Stacks[0].Outputs" --region "$REGION"
echo
aws cloudformation describe-stacks --stack-name "${ENV_NAME}-cicd" --query "Stacks[0].Outputs" --region "$REGION"
echo
echo "Next: run scripts/put-secrets.sh, then scripts/sync-and-deploy.sh"
echo "For GitHub Actions deploys: set the DeployRoleArn output above as the"
echo "DEPLOY_ROLE_ARN repo variable — see infra/README.md's CI/CD section."
