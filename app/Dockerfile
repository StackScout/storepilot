# Multi-stage build using Next.js's `output: "standalone"` (next.config.ts)
# — the runtime stage only ships the minimal self-contained server bundle,
# not node_modules or the full build cache.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules node_modules
COPY . .
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
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup --system --gid 1001 storepilot && adduser --system --uid 1001 storepilot
COPY --from=build --chown=storepilot:storepilot /app/.next/standalone ./
COPY --from=build --chown=storepilot:storepilot /app/.next/static ./.next/static
USER storepilot
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["node", "server.js"]
