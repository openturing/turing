/**
 * File templates emitted by {@code turing init}. Kept as a pure map (path →
 * content) so the {@code init} command is a thin write loop and the templates
 * are unit-testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

/** Returns the project skeleton as a map of relative path → file content. */
export function scaffoldFiles(name: string): Record<string, string> {
  const safeName = name.trim() || "my-agent";
  return {
    "turing.config.json": JSON.stringify(
      {
        name: safeName,
        agentId: "",
        default: "local",
        envs: {
          local: { url: "http://localhost:2700" },
          staging: { url: "https://staging.turing.example.com" },
        },
      },
      null,
      2,
    ) + "\n",

    "agent.json": JSON.stringify(
      {
        title: titleCase(safeName),
        description: "Scaffolded by `turing init`.",
        icon: "robot",
        systemPrompt:
          "You are a helpful assistant for " + titleCase(safeName) + ". Answer concisely and stay on topic.",
        enabled: 1,
        ragEnabled: 0,
        richContentEnabled: 0,
        skillsEnabled: 0,
        chatMemoryEnabled: 1,
        nativeTools: [],
      },
      null,
      2,
    ) + "\n",

    "evals/smoke.eval.yaml": SMOKE_EVAL,
    "docker-compose.dev.yml": DEV_COMPOSE,
    "flows/.gitkeep":
      "# Drop *.chat-flow.json files here (author them with @viglet/turing-flow-dsl).\n",
    "tools/.gitkeep": "# Drop *.groovy custom-tool scripts here.\n",
    "skills/.gitkeep":
      "# Each subfolder is an Anthropic-compatible skill (SKILL.md + scripts/ + references/).\n",
    ".gitignore": GITIGNORE,
    "README.md": readme(safeName),
  };
}

function titleCase(slug: string): string {
  return slug
    .replace(/[-_]+/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim();
}

const SMOKE_EVAL = `# Smoke eval — runs against the agent in turing.config.json.
# Run with:  turing eval
fixtures:
  - id: greeting-is-helpful
    description: The agent greets and stays in voice.
    conversation:
      - user: "Hello, can you help me?"
      - assert.assistant.matches: ".+"
      - assert.persona.forbidden: ["I cannot help", "I'm just an AI"]
`;

const DEV_COMPOSE = `# Minimal local Turing stack for \`turing dev\`.
#
# The default service runs Turing with its built-in H2 database and the
# embedded Lucene search engine — no external services required, so it boots
# from a single published image. Override the image with TURING_IMAGE.
#
# For the full stack (MariaDB + Solr Cloud + MinIO), uncomment the blocks below
# and set the matching SPRING_/TURING_ env on the turing service.
services:
  turing:
    image: \${TURING_IMAGE:-ghcr.io/openviglet/turing:latest}
    container_name: turing-dev
    ports:
      - "2700:2700"
    volumes:
      - vol-turing-store:/turing/store
    # environment:
    #   SPRING_DATASOURCE_URL: jdbc:mariadb://turing-mariadb:3306/turing
    #   SPRING_DATASOURCE_USERNAME: turing
    #   SPRING_DATASOURCE_PASSWORD: turing
    #   SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.mariadb.jdbc.Driver
    #   SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: org.hibernate.dialect.MariaDBDialect
    #   TURING_SOLR_CLOUD: "true"

  # turing-mariadb:
  #   image: mariadb:11
  #   container_name: turing-mariadb
  #   ports: ["3306:3306"]
  #   environment:
  #     MARIADB_DATABASE: turing
  #     MARIADB_ROOT_PASSWORD: turing
  #     MARIADB_USER: turing
  #     MARIADB_PASSWORD: turing
  #   volumes: ["vol-mariadb:/var/lib/mysql"]

  # turing-minio:
  #   image: minio/minio:latest
  #   container_name: turing-minio
  #   command: server /data --console-address ":9001"
  #   ports: ["9000:9000", "9001:9001"]
  #   environment:
  #     MINIO_ROOT_USER: admin
  #     MINIO_ROOT_PASSWORD: minha_senha_forte
  #   volumes: ["vol-minio:/data"]

volumes:
  vol-turing-store:
  # vol-mariadb:
  # vol-minio:
`;

const GITIGNORE = `node_modules/
dist/
*.log
.env
`;

function readme(name: string): string {
  return `# ${titleCase(name)}

A Viglet Turing ES agent project scaffolded by \`turing init\`.

## Layout

- \`agent.json\` — the AI Agent definition (system prompt, flags, native tools).
- \`flows/\` — \`*.chat-flow.json\` chat-flow definitions (author with \`@viglet/turing-flow-dsl\`).
- \`tools/\` — \`*.groovy\` custom-tool scripts.
- \`skills/\` — Anthropic-compatible skill folders (one per subdirectory).
- \`evals/\` — \`*.eval.yaml\` regression suites.
- \`turing.config.json\` — instance URL(s) per environment + the deployed agent id.

## Commands

\`\`\`bash
turing dev                  # boot a local Turing stack (docker compose)
turing deploy --env=local   # push agent + flows + tools + skills
turing eval                 # run the YAML eval suites in evals/
turing logs --conversation <id>   # tail live chat events for a conversation
turing eval record <conversationId> --out evals/from-prod.eval.yaml
\`\`\`

## Authentication

Set \`TURING_TOKEN\` (a dev token from the admin console) for unattended /
CI use, or \`--username\` + \`TURING_PASSWORD\` for interactive use. Credentials
are never written to \`turing.config.json\`.
`;
}
