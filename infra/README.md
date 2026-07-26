# IslandCart AWS deployment (test environment)

A minimal, free-tier-conscious deployment: **one EC2 instance** running
Postgres + the Spring Boot backend + the Next.js frontend + Caddy (reverse
proxy + automatic HTTPS) via Docker Compose — plus S3 for bank-transfer
receipts and SES for real emails. See the root of this repo's docs for the
application itself; this file only covers infrastructure.

## Why this shape, not RDS/ECS/ALB

This targets a **new AWS account** (post-July-2024 signup): $200 in
credits for 6 months, plus a small "always free" service list — not the
old 12-month per-service free tier. For testing, that means cost matters
more than production polish:

- **No RDS** — Postgres runs in the same Docker Compose stack as the app,
  on the same instance. One billed resource instead of two.
- **No ECS/Fargate** — no free tier at all for Fargate; unnecessary
  complexity for a single service.
- **No ALB** — ~$16+/month, not needed for one instance.
- **No Secrets Manager** — $0.40/secret/month with no free tier. SSM
  Parameter Store (Standard tier) is used instead — it's always free.
- **No Route53/ACM** — no custom domain required. Caddy gets a real Let's
  Encrypt certificate for `https://<elastic-ip-with-dashes>.sslip.io`,
  a free wildcard DNS trick that resolves to your instance's IP with zero
  DNS setup. Swap in a real domain later by pointing an A record at the
  Elastic IP and changing one line in `docker/Caddyfile`.

Instance size is **t3.small** (2GB RAM), not t3.micro — 1GB isn't enough
for Postgres + a JVM + a Node process running concurrently without
thrashing.

## Prerequisites

- AWS CLI configured (`aws configure`) with credentials that can create
  IAM roles, EC2 instances, S3 buckets, and SSM parameters.
- An EC2 key pair already created in your target region (`aws ec2
  create-key-pair --key-name islandcart-test --query
  'KeyMaterial' --output text > ~/.ssh/islandcart-test.pem && chmod 400
  ~/.ssh/islandcart-test.pem`).
- Your default VPC's ID and a subnet ID within it:
  ```
  aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query "Vpcs[0].VpcId"
  aws ec2 describe-subnets --filters Name=vpc-id,Values=<VpcId> --query "Subnets[0].SubnetId"
  ```
- Your own public IP, for restricting SSH: `curl -s ifconfig.me`

## Deploy

1. `cp infra/.env.deploy.example infra/.env.deploy` and fill in real values
   (never committed — see `.gitignore`).
2. `infra/scripts/deploy.sh` — creates the CloudFormation stacks in order
   (S3 bucket → IAM role → security group → EC2 instance + Elastic IP).
3. `infra/scripts/put-secrets.sh` — writes DB credentials, the PayHere
   merchant secret, and the SES sender address to SSM Parameter Store as
   `SecureString` parameters. **This can't be a CloudFormation template**:
   `AWS::SSM::Parameter` only supports the `String`/`StringList` types, not
   `SecureString` — a known, long-standing CloudFormation limitation.
4. `infra/scripts/sync-and-deploy.sh` — rsyncs the repo to the instance and
   runs `docker compose up -d --build`. Re-run this any time you push a
   code change.
5. Visit the `SiteAddress` output from step 2 (also printed at the end of
   step 4). Give Caddy a minute or two on first boot to obtain its TLS
   certificate.

Re-running `deploy.sh` is safe (CloudFormation only updates what changed).

## SES sandbox

New SES accounts start in **sandbox mode**: you can only send to email
addresses you've individually verified, and the sender address itself must
be verified too. Verify addresses in the SES console (or `aws sesv2
create-email-identity --email-identity you@example.com`, then click the
confirmation link AWS emails you). To send to *any* address (real
production use), request production access from the SES console — this is
a manual AWS review, budget at least a day for approval.

## Cost notes

Everything here draws down the $200/6-month credit rather than being
free outright (new-account model, not the old always-free EC2/RDS tier).
Rough numbers: t3.small ≈ $15/mo, 20GB gp3 EBS ≈ $1.60/mo, Elastic IP is
free while attached to a running instance, S3/SES/SSM are pennies at this
scale. After the credit is used up (or 6 months, whichever first), this
becomes a real ~$17-20/month bill unless torn down.

## Teardown

```
aws cloudformation delete-stack --stack-name islandcart-test-compute
aws cloudformation delete-stack --stack-name islandcart-test-security
aws cloudformation delete-stack --stack-name islandcart-test-iam
aws cloudformation delete-stack --stack-name islandcart-test-storage   # empty the S3 bucket first, CFN won't delete a non-empty bucket
```
(reverse of deploy order — `compute` before `security`/`iam` since it
imports their exports; `storage` last since `iam` imports its export).

## Layout

```
infra/
  cloudformation/
    storage.yaml      S3 bucket for receipts (private)
    iam.yaml           EC2 instance role (S3 + SSM + SES policies)
    security.yaml        Security group (22 restricted, 80/443 public)
    compute.yaml            EC2 instance + Elastic IP, UserData installs
                             Docker and writes .env from SSM
  docker/
    docker-compose.prod.yml   postgres + backend + frontend + caddy
    Caddyfile                   reverse proxy + auto-HTTPS via sslip.io
  scripts/
    deploy.sh                    deploys the CFN stacks in order
    put-secrets.sh                  writes SecureString SSM parameters
    sync-and-deploy.sh                 rsyncs code, (re)starts the stack
  .env.deploy.example                   template — copy to .env.deploy (gitignored)
```
