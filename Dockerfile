# syntax=docker/dockerfile:1.7
# Multi-stage build for Viglet Turing ES.
# Ported from viglet/cloud docker/Dockerfile.turing — the `viglet/jdk-build`
# base (eclipse-temurin:25-jdk + git) is inlined here so the build is
# self-contained and needs no externally published base image.

# Stage 1 — Frontend (pnpm monorepo → SPA)
FROM node:24-slim AS node-builder
ARG REPO_BRANCH=2026.3
ARG CACHE_BUST=none
# Node 25+ não traz mais o corepack embutido, então instalamos o pnpm via npm.
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g pnpm@10.33.0
WORKDIR /src
# Reference CACHE_BUST in the RUN so a new SHA invalidates the git clone layer.
# `github_token` is a BuildKit secret. It's only present at build time via the
# runtime mount and never lands in any image layer. Optional — falls back to
# anonymous clone if the secret is empty.
RUN --mount=type=secret,id=github_token,required=false \
    echo "cache_bust=${CACHE_BUST}" && \
    if [ -s /run/secrets/github_token ]; then \
        URL="https://x-access-token:$(cat /run/secrets/github_token)@github.com/openviglet/turing.git"; \
    else \
        URL="https://github.com/openviglet/turing.git"; \
    fi && \
    git clone --depth 1 --branch ${REPO_BRANCH} "$URL" .
WORKDIR /src/frontend
RUN --mount=type=cache,target=/root/.local/share/pnpm/store/v3 \
    pnpm install --frozen-lockfile=false
RUN pnpm run build:app

# Stage 2 — Backend (Maven, frontend já compilado)
FROM eclipse-temurin:25-jdk AS build
COPY --from=node-builder /src /src
WORKDIR /src
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -pl '!turing-utils' \
      -Dskip.installnodenpm=true -Dskip.npm=true -Dskip.pnpm=true 2>&1

# Stage 3 — Runtime
FROM eclipse-temurin:25-jre
ARG CACHE_BUST=none
LABEL org.opencontainers.image.revision=$CACHE_BUST
RUN groupadd -r java && useradd -r -g java -u 1001 java
WORKDIR /app
COPY --from=build /src/turing-app/target/viglet-turing.jar app.jar
RUN mkdir -p /app/store && chown -R java:java /app
USER java
VOLUME ["/app/store"]
EXPOSE 2700
ENV JAVA_OPTS="-Xmx512m -Xms512m"
ENV DEBUG_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dlogging.config=classpath:logback-spring-console.xml -jar /app/app.jar"]
