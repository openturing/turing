import { federation } from "@module-federation/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    federation({
      name: "turing_react",
      remotes: {},
      shared: {
        react: { singleton: true, requiredVersion: "*" },
        "react-dom": { singleton: true, requiredVersion: "*" },
        "react-router-dom": { singleton: true, requiredVersion: "*" },
        "react-i18next": { singleton: true, requiredVersion: "*" },
        i18next: { singleton: true, requiredVersion: "*" },
        axios: { singleton: true, requiredVersion: "*" },
        sonner: { singleton: true, requiredVersion: "*" },
      },
    }),
  ],
  build: {
    emptyOutDir: true,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            if (id.includes("recharts") || id.includes("d3")) return "vendor-charts";
            if (id.includes("@radix-ui")) return "vendor-ui";
            if (
              id.includes("react-markdown") ||
              id.includes("rehype") ||
              id.includes("remark") ||
              id.includes("unified") ||
              id.includes("hast") ||
              id.includes("mdast") ||
              id.includes("micromark") ||
              id.includes("highlight.js") ||
              id.includes("lowlight") ||
              id.includes("unist-") ||
              id.includes("vfile") ||
              id.includes("devlop") ||
              id.includes("property-information") ||
              id.includes("space-separated-tokens") ||
              id.includes("comma-separated-tokens") ||
              id.includes("estree-") ||
              id.includes("style-to-js") ||
              id.includes("html-url-attributes")
            )
              return "vendor-markdown";
            return "vendor-lib";
          }
        },
      },
    },
    outDir: "../turing-app/src/main/resources/public/",
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
