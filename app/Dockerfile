# Multi-stage build using Next.js's `output: "standalone"` (next.config.ts)
# — the runtime stage only ships the minimal self-contained server bundle,
# not node_modules or the full build cache.
#
# Build context is the REPO ROOT (see docker-compose.prod.yml), not app/
# — app/ depends on the sibling packages/shared-api npm-workspace member,
# which a context scoped to app/ alone can't see. npm ci therefore runs
# from the repo root and needs every workspace member's package.json
# present (see .dockerignore: mobile/'s source is excluded, but its
# package.json is deliberately kept so npm ci can validate the lockfile).

FROM node:22-alpine AS deps
WORKDIR /repo
COPY . .
# npm ci (not used here) trusts package-lock.json's already-resolved
# optional-dependency list verbatim — but a lockfile generated on a
# non-Linux host only resolves ITS OWN platform's native binaries (e.g.
# Tailwind's @tailwindcss/oxide-darwin-arm64), never adding the
# linux-musl variant this Alpine image needs (npm/cli#4828). `npm install`
# still respects the lockfile's version pins, but re-resolves each
# platform-specific optional binary fresh against the container's actual
# platform instead of trusting the host-frozen list.
RUN npm install

FROM node:22-alpine AS build
WORKDIR /repo
COPY --from=deps /repo ./
# The backend's public URL — country content (platform name, currency,
# states, fees, ...) is fetched from the backend's platform_settings/states
# tables at runtime (see app/src/lib/platform-config.ts), not baked in as
# NEXT_PUBLIC_* build args, so this same image serves any country — only
# the backend's DB differs.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
# proxy.ts (middleware) verifies the access token cookie's JWT itself,
# independent of the backend, to gate routes before rendering — it needs
# the Cognito issuer, which NEXT_PUBLIC_* vars only supply if set at build
# time (they're statically inlined, not read at container runtime).
# Without these, jwtVerify's issuer URL contains "undefined" and every
# token silently fails verification, so proxy.ts treats every signed-in
# user as signed out.
ARG NEXT_PUBLIC_COGNITO_REGION
ENV NEXT_PUBLIC_COGNITO_REGION=$NEXT_PUBLIC_COGNITO_REGION
ARG NEXT_PUBLIC_COGNITO_USER_POOL_ID
ENV NEXT_PUBLIC_COGNITO_USER_POOL_ID=$NEXT_PUBLIC_COGNITO_USER_POOL_ID
WORKDIR /repo/app
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup --system --gid 1001 storepilot && adduser --system --uid 1001 storepilot
# Next.js's standalone output mirrors the monorepo's directory structure
# when it detects a workspace root (see next.config.ts) — the traced
# server bundle lands at .next/standalone/app/, not .next/standalone/
# directly. Confirmed by inspecting the actual build output, not assumed.
COPY --from=build --chown=storepilot:storepilot /repo/app/.next/standalone/app ./
COPY --from=build --chown=storepilot:storepilot /repo/app/.next/standalone/node_modules ./node_modules
COPY --from=build --chown=storepilot:storepilot /repo/app/.next/static ./.next/static
USER storepilot
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["node", "server.js"]
