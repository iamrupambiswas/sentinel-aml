# Sentinel console: production image (Phase 7b). Build from the repository root with
#   docker compose build console
# or from this folder:  docker build -t sentinel-console .

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Build-time placeholders only: server code reads the real values when the container starts
ENV NEXT_TELEMETRY_DISABLED=1 \
    NEXTAUTH_URL=http://localhost:3000 \
    NEXTAUTH_SECRET=build-placeholder \
    KEYCLOAK_ISSUER=http://localhost:8180/realms/sentinel \
    KEYCLOAK_CLIENT_ID=sentinel-console \
    KEYCLOAK_CLIENT_SECRET=build-placeholder
RUN npm run build

FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
USER app
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:3000/login || exit 1
CMD ["node", "server.js"]
