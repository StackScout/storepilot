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
# The backend's public URL — the only build-time config left. Country
# content (platform name, currency, states, fees, ...) is fetched from the
# backend's platform_settings/states tables at runtime (see
# app/src/lib/platform-config.ts), not baked in as NEXT_PUBLIC_* build args,
# so this same image serves any country — only the backend's DB differs.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
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
