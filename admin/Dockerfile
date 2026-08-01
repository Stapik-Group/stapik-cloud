# ---- deps ----
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# admin-api.yaml (and its relative $refs, e.g. common/error.yaml) are outside
# this build context - pulled in via the "openapi" additional context
# defined in docker-compose.yml
COPY --from=openapi . ./openapi-spec
RUN npx openapi-typescript ./openapi-spec/admin-api.yaml -o lib/api-types.ts --default-non-nullable false && rm -rf ./openapi-spec

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# Wymaga next.config.ts z "output: 'standalone'"
COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]