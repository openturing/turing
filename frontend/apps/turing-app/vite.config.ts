import { federation } from "@module-federation/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { vigletBootLoader } from "@viglet/viglet-design-system/vite";
import fs from "node:fs";
import path from "node:path";
import { defineConfig, type Plugin, type ViteDevServer } from "vite";

// Captures the Vite dev server instance so the /sn proxy can call
// server.transformIndexHtml() and serve a properly transformed index.html
// when the backend signals embedded-search mode (X-Turing-Embedded header).
let viteServer: ViteDevServer | null = null;
function captureServerPlugin(): Plugin {
  return {
    name: "turing-capture-server",
    configureServer(server) {
      viteServer = server;
    },
  };
}

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
      "/api": {
        target: "http://localhost:2700",
        changeOrigin: true,
      },
      "/csrf": {
        target: "http://localhost:2700",
        changeOrigin: true,
      },
      "/sn": {
        target: "http://localhost:2700",
        changeOrigin: true,
        selfHandleResponse: true,
        configure(proxy) {
          const rawHtml = fs.readFileSync(
            path.resolve(__dirname, "index.html"),
            "utf-8",
          );
          proxy.on("proxyRes", (proxyRes, req, res) => {
            if (proxyRes.headers["x-turing-embedded"] === "true" && viteServer) {
              // Backend says: use embedded search → serve Vite-transformed index.html
              proxyRes.resume();
              viteServer
                .transformIndexHtml(req.url || "/", rawHtml)
                .then((html) => {
                  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
                  res.end(html);
                })
                .catch(() => {
                  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
                  res.end(rawHtml);
                });
            } else {
              // SPA template or API response → pass through
              res.writeHead(proxyRes.statusCode || 200, proxyRes.headers);
              proxyRes.pipe(res);
            }
          });
        },
      },
    },
  },
  plugins: [
    captureServerPlugin(),
    syncExternalStoreShimPlugin(),
    vigletBootLoader({
      title: "Viglet Turing ES",
      subtitle: "Enterprise Search Intelligence",
      color: "#4169E1",
      colorDark: "#2c4a99",
      prefix: "turing",
      testFlag: "__TURING_LOADING_TEST__",
    }),
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
        sonner: { singleton: true, requiredVersion: "*" },
        "next-themes": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/router": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/i18n": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/floating-formulas-bg": { singleton: true, requiredVersion: "*" },
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
    outDir: "../../../turing-app/src/main/resources/public/",
  },
  resolve: {
    dedupe: ["react", "react-dom", "react/jsx-runtime", "react/jsx-dev-runtime"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
