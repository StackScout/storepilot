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
# The backend's public URL, baked in at build time (NEXT_PUBLIC_* vars are
# inlined into the client bundle, not read at runtime) — see
# infra/docker/docker-compose.prod.yml for how this is passed in.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup --system --gid 1001 islandcart && adduser --system --uid 1001 islandcart
COPY --from=build /app/public public
COPY --from=build --chown=islandcart:islandcart /app/.next/standalone ./
COPY --from=build --chown=islandcart:islandcart /app/.next/static ./.next/static
USER islandcart
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["node", "server.js"]
