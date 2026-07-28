# StorePilot AWS deployment (test environment)

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
  create-key-pair --key-name storepilot-test --query
  'KeyMaterial' --output text > ~/.ssh/storepilot-test.pem && chmod 400
  ~/.ssh/storepilot-test.pem`).
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
   code change, from your own machine, over SSH.
5. Visit the `SiteAddress` output from step 2 (also printed at the end of
   step 4). Give Caddy a minute or two on first boot to obtain its TLS
   certificate.

Re-running `deploy.sh` is safe (CloudFormation only updates what changed).
Step 2 also deploys `cicd.yaml`, the GitHub Actions deploy pipeline's IAM
role — see the "CI/CD" section below for what that's for and the one-time
GitHub-side setup it needs.

## CI/CD (GitHub Actions)

`.github/workflows/deploy.yml` automates step 4 above — it does the
equivalent of `sync-and-deploy.sh`, but from GitHub Actions instead of
your own machine. It deliberately does **not** reuse SSH: GitHub-hosted
runners have unpredictable IPs, and `security.yaml`'s SSH rule is locked
to your own single IP on purpose (see that file's comment) — that stance
isn't relaxed for CI. Instead the workflow authenticates to AWS via a
**GitHub OIDC-federated IAM role** (`cicd.yaml`'s `DeployRole` — no AWS
access keys or SSH key stored as a GitHub secret, ever) with permission to
do exactly three things: upload a repo tarball to a small S3 bucket
(`storage.yaml`'s `CiDeployBucket`, auto-expires objects after 3 days),
tell the instance to pull and apply it via **SSM RunCommand** (which is
why `iam.yaml`'s instance role now also carries the AWS-managed
`AmazonSSMManagedInstanceCore` policy — AL2023's SSM agent is preinstalled
but was previously never granted permission to register), and read the
compute stack's outputs to find the instance/site address.

One-time setup after `deploy.sh` has created the `cicd.yaml` stack:

1. Read the `DeployRoleArn` output (`deploy.sh` prints it, or
   `aws cloudformation describe-stacks --stack-name <env>-cicd --query "Stacks[0].Outputs"`).
2. In the GitHub repo, go to **Settings → Secrets and variables → Actions
   → Variables** (not *Secrets* — nothing here is sensitive, since OIDC
   means no static credential exists at all) and set: `DEPLOY_ROLE_ARN`,
   `AWS_REGION`, `ENVIRONMENT_NAME`, `DEPLOY_BUCKET` (the
   `CiDeployBucketName` output from the same stack).
3. Trigger the workflow manually (Actions tab → Deploy →
   **Run workflow** — it's `workflow_dispatch`-only for now, no `push`
   trigger yet) and confirm it succeeds end to end before trusting it on
   every push. Once proven, `cicd.yaml`'s trust policy can be tightened
   from `repo:<org>/<repo>:*` to `repo:<org>/<repo>:ref:refs/heads/main`
   (redeploy the stack with that change) and a `push: branches: [main]`
   trigger added to the workflow.

`scripts/sync-and-deploy.sh` is unaffected and still works exactly as
before, for local/emergency deploys from your own allowed IP.

## Connecting a DB client to Postgres

Postgres isn't exposed to the internet — `docker-compose.prod.yml` binds it
to the instance's own loopback interface only (`127.0.0.1:5432`), and
`security.yaml`'s security group doesn't open 5432 either. To connect a
local GUI client (TablePlus, DBeaver, pgAdmin, ...), open an SSH tunnel
first, then point the client at your own forwarded local port:

```
ssh -i <path-to-your-key.pem> -L 5433:localhost:5432 -N ec2-user@<instance-public-ip>
```

Leave that running, then connect your client to `localhost:5433`,
database `storepilot`, with the `DB_USERNAME`/`DB_PASSWORD` values from
`.env.deploy` (or `aws ssm get-parameter --name
/storepilot/<env>/db-password --with-decryption`).

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

The CI/CD pieces add effectively nothing: the GitHub OIDC provider, the
deploy IAM role, and every SSM RunCommand invocation are $0 (no free-tier
caveat, they're just free); `CiDeployBucket` holds a handful of MB-scale
tarballs that auto-expire after 3 days, so it stays pennies regardless of
how often the pipeline runs. GitHub Actions minutes are free too at this
usage (unlimited on a public repo, 2,000 free min/month on private — this
job runs a few minutes).

## Teardown

```
aws cloudformation delete-stack --stack-name storepilot-test-cicd
aws cloudformation delete-stack --stack-name storepilot-test-compute
aws cloudformation delete-stack --stack-name storepilot-test-security
aws cloudformation delete-stack --stack-name storepilot-test-iam
aws cloudformation delete-stack --stack-name storepilot-test-storage   # empty both S3 buckets first, CFN won't delete a non-empty bucket
```
(reverse of deploy order — `cicd` first since it imports both `storage`'s
and `compute`'s exports; `compute` before `security`/`iam` since it
imports their exports; `storage` last since `iam` imports its export).

## Layout

```
infra/
  cloudformation/
    storage.yaml      S3 buckets: receipts (private), ci-deploy (private,
                       3-day lifecycle expiry)
    iam.yaml           EC2 instance role (S3 + SSM param + SES + CI
                        deploy bucket read policies, SSM Core managed
                        policy)
    security.yaml        Security group (22 restricted, 80/443 public)
    compute.yaml            EC2 instance + Elastic IP, UserData installs
                             Docker and writes .env from SSM
    cicd.yaml                  GitHub OIDC provider (or reuse) + repo-
                                scoped deploy IAM role — see "CI/CD" above
  docker/
    docker-compose.prod.yml   postgres + backend + frontend + caddy
    Caddyfile                   reverse proxy + auto-HTTPS via sslip.io
  scripts/
    deploy.sh                    deploys the CFN stacks in order
    put-secrets.sh                  writes SecureString SSM parameters
    sync-and-deploy.sh                 rsyncs code, (re)starts the stack
                                        (local/manual, over SSH)
  .env.deploy.example                   template — copy to .env.deploy (gitignored)
../.github/workflows/
  deploy.yml   GitHub Actions deploy pipeline (S3 + SSM RunCommand,
               workflow_dispatch — see "CI/CD" above)
```
