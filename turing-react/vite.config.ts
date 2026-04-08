import { federation } from "@module-federation/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";

// Redirect CJS use-sync-external-store imports to ESM shims.
// React 19 has useSyncExternalStore built-in, but the CJS shim
// does require('react') which breaks Rolldown's browser CJS interop.
function syncExternalStoreShimPlugin(): Plugin {
  const shimDir = path.resolve(__dirname, "./src/shims");
  return {
    name: "use-sync-external-store-shim",
    enforce: "pre",
    resolveId(source) {
      if (source === "use-sync-external-store/shim/with-selector") {
        return path.join(shimDir, "use-sync-external-store-with-selector.js");
      }
      if (source === "use-sync-external-store/shim") {
        return path.join(shimDir, "use-sync-external-store-shim.js");
      }
      if (
        source === "use-sync-external-store/with-selector" ||
        source === "use-sync-external-store/with-selector.js"
      ) {
        return path.join(shimDir, "use-sync-external-store-with-selector.js");
      }
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  server: {
    proxy: {
      "/pages": {
        target: "http://localhost:2700",
        changeOrigin: true,
      },
      "/api/sn": {
        target: "http://localhost:2700",
        changeOrigin: true,
      },
      "/sn": {
        target: "http://localhost:2700",
        changeOrigin: true,
      },
    },
  },
  plugins: [
    syncExternalStoreShimPlugin(),
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
            if (id.includes("recharts") || id.includes("d3"))
              return "vendor-charts";
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
      "@openviglet/turing-react-sdk": path.resolve(
        __dirname,
        "../turing-js-sdk/turing-react-sdk/src/index.ts",
      ),
    },
  },
});
