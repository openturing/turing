import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    emptyOutDir: true,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            // Force Recharts and D3 into their own "charts" chunk
            if (id.includes("recharts") || id.includes("d3")) {
              return "vendor-charts";
            }
            // Force UI components (Radix) into their own chunk
            if (id.includes("@radix-ui")) {
              return "vendor-ui";
            }
            // Everything else in node_modules goes to a general vendor
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
