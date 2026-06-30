import React from "react";
import { hydrateRoot } from "react-dom/client";
import App from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";
import "./index.css";

// The HTML for this route was prerendered at build time (see scripts/prerender.mjs),
// so we hydrate the existing markup rather than mounting from scratch. The route
// is derived from the URL — the same trivial rule the server used.
hydrateRoot(
  document.getElementById("root")!,
  <React.StrictMode>
    <ThemeProvider defaultTheme="system" storageKey="turing-site-theme">
      <App path={globalThis.location.pathname} />
    </ThemeProvider>
  </React.StrictMode>
);
