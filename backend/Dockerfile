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
ENV MAVEN_OPTS="-Xms32m -Xmx384m -XX:+UseSerialGC -XX:ActiveProcessorCount=2"
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG KUBECTL_VERSION=v1.37.1
ARG HELM_VERSION=v3.22.0
ARG TARGETARCH
RUN apk add --no-cache nodejs curl ca-certificates tar \
    && curl -fsSLo /tmp/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" \
    && curl -fsSLo /tmp/kubectl.sha256 "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl.sha256" \
    && echo "$(cat /tmp/kubectl.sha256)  /tmp/kubectl" | sha256sum -c - \
    && install -m 0755 /tmp/kubectl /usr/local/bin/kubectl \
    && curl -fsSLo /tmp/helm.tar.gz "https://get.helm.sh/helm-${HELM_VERSION}-linux-${TARGETARCH}.tar.gz" \
    && curl -fsSLo /tmp/helm.tar.gz.sha256sum "https://get.helm.sh/helm-${HELM_VERSION}-linux-${TARGETARCH}.tar.gz.sha256sum" \
    && echo "$(cut -d ' ' -f 1 /tmp/helm.tar.gz.sha256sum)  /tmp/helm.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/helm.tar.gz -C /tmp \
    && install -m 0755 "/tmp/linux-${TARGETARCH}/helm" /usr/local/bin/helm \
    && rm -rf /tmp/kubectl* /tmp/helm.tar.gz* /tmp/linux-${TARGETARCH} \
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
COPY --chown=10001:10001 helm/store-template /app/helm/store-template

ENV MCP_NODE_COMMAND=node
ENV MCP_SERVER_PATH=/app/mcp-servers/woocommerce/dist/server.js
ENV WEATHER_MCP_SERVER_PATH=/app/mcp-servers/weather/dist/server.js

EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/backend/app.jar"]
