# syntax=docker/dockerfile:1

FROM node:22-alpine AS mcp-build
WORKDIR /workspace/woocommerce
COPY mcp-servers/woocommerce/package.json mcp-servers/woocommerce/package-lock.json ./
RUN npm ci
COPY mcp-servers/woocommerce/ ./
RUN npm run build && npm prune --omit=dev

FROM node:22-alpine AS weather-mcp-build
WORKDIR /workspace/weather
COPY mcp-servers/weather/package.json mcp-servers/weather/package-lock.json ./
RUN npm ci
COPY mcp-servers/weather/ ./
RUN npm run build && npm prune --omit=dev

FROM maven:3.9-eclipse-temurin-21-alpine AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache nodejs \
    && addgroup -g 10001 -S app \
    && adduser -u 10001 -S app -G app

WORKDIR /app/backend
RUN mkdir -p /app/mcp-servers/woocommerce \
    /app/mcp-servers/weather \
    && chown -R 10001:10001 /app

COPY --from=backend-build --chown=10001:10001 \
    /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
COPY --from=mcp-build --chown=10001:10001 \
    /workspace/woocommerce/package.json /app/mcp-servers/woocommerce/package.json
COPY --from=mcp-build --chown=10001:10001 \
    /workspace/woocommerce/node_modules /app/mcp-servers/woocommerce/node_modules
COPY --from=mcp-build --chown=10001:10001 \
    /workspace/woocommerce/dist /app/mcp-servers/woocommerce/dist
COPY --from=weather-mcp-build --chown=10001:10001 \
    /workspace/weather/package.json /app/mcp-servers/weather/package.json
COPY --from=weather-mcp-build --chown=10001:10001 \
    /workspace/weather/node_modules /app/mcp-servers/weather/node_modules
COPY --from=weather-mcp-build --chown=10001:10001 \
    /workspace/weather/dist /app/mcp-servers/weather/dist

ENV MCP_NODE_COMMAND=node
ENV MCP_SERVER_PATH=/app/mcp-servers/woocommerce/dist/server.js
ENV WEATHER_MCP_SERVER_PATH=/app/mcp-servers/weather/dist/server.js

EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/backend/app.jar"]
