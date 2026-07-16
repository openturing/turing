# syntax=docker/dockerfile:1.7
# Multi-stage build for Viglet Turing ES.
# Ported from viglet/cloud docker/Dockerfile.turing — the `viglet/jdk-build`
# base (eclipse-temurin:21-jdk + git) is inlined here so the build is
# self-contained and needs no externally published base image.
#
# Java 21 (NOT 25): embedded Artemis 2.43.0 (via Netty) crashes on Java 25 with
# "Cannot clean arbitrary ByteBuffer instances" (CleanerJava25) while loading its
# NIO journal, which takes down the broker and every JMS listener.

# Stage 1 — Frontend (pnpm monorepo → SPA)
FROM node:26.3.1-slim AS node-builder
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
FROM eclipse-temurin:21-jdk AS build
COPY --from=node-builder /src /src
WORKDIR /src
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -pl '!turing-utils' \
      -Dskip.installnodenpm=true -Dskip.npm=true -Dskip.pnpm=true 2>&1

# Stage 3 — Runtime
FROM eclipse-temurin:21-jre
ARG CACHE_BUST=none
LABEL org.opencontainers.image.revision=$CACHE_BUST
RUN groupadd -r java && useradd -r -g java -u 1001 java
# Docker CLI (client only — NO daemon) for the opt-in Code Interpreter DOCKER
# execution mode (T80). At runtime the container talks to a Docker daemon via a
# bind-mounted host socket (Docker-out-of-Docker) or a DinD sidecar — see
# docker-compose.yaml / docker-compose-minimal.yaml. Copying just the static
# client from the official image keeps the daemon + its deps out of this image
# (~50 MB) and is a no-op for NATIVE-mode deployments that never mount a socket.
COPY --from=docker:28-cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY --from=build /src/turing-app/target/viglet-turing.jar app.jar
# Pre-create the store subdirs the demo mounts as their OWN named volumes
# (onnx-cache, djl-cache — see viglet/cloud docker-compose, T656). A named volume
# mounted onto a path that EXISTS in the image inherits that dir's ownership; if
# the path is absent, Docker creates it root-owned and the non-root app user
# (uid 1001, cap_drop ALL) cannot write it. So they must exist + be chowned here.
RUN mkdir -p /app/store/onnx-cache /app/store/djl-cache && chown -R java:java /app
USER java
VOLUME ["/app/store"]
EXPOSE 2700
# Headless image (no X11): never try to open a desktop browser on startup.
ENV TURING_OPEN_BROWSER=false
ENV JAVA_OPTS="-Xmx512m -Xms512m -Djava.awt.headless=true"
ENV DEBUG_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dlogging.config=classpath:logback-spring-console.xml -jar /app/app.jar"]
