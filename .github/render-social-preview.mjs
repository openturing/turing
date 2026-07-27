/**
 * Renders .github/social-preview.svg to .github/social-preview.png at the size
 * GitHub wants for a repository social preview card (1280x640).
 *
 * GitHub does not expose social preview over its REST API, so the PNG has to be
 * uploaded by hand: repo Settings -> Social preview -> Upload an image. Keeping
 * the SVG source + this renderer in the repo means the card can be regenerated
 * (new tagline, new version) without redoing it in a design tool.
 *
 * Usage:  node .github/render-social-preview.mjs
 * Requires @resvg/resvg-js, already present in the frontend pnpm store.
 */
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, "..");

// @resvg/resvg-js is a transitive dependency of the frontend workspace, so it
// is not hoisted to frontend/node_modules — resolve it from the pnpm store.
const require = createRequire(join(repoRoot, "frontend", "package.json"));
function loadResvg() {
  try {
    return require("@resvg/resvg-js");
  } catch {
    const store = join(repoRoot, "frontend", "node_modules", ".pnpm");
    const pkg = readdirSync(store).find((d) => d.startsWith("@resvg+resvg-js@"));
    if (!pkg) {
      throw new Error(
        "@resvg/resvg-js not found. Run `pnpm install` in frontend/ first.",
      );
    }
    return require(join(store, pkg, "node_modules", "@resvg", "resvg-js"));
  }
}
const { Resvg } = loadResvg();

const svgPath = join(here, "social-preview.svg");
const pngPath = join(here, "social-preview.png");

const resvg = new Resvg(readFileSync(svgPath, "utf8"), {
  fitTo: { mode: "width", value: 1280 },
  font: { loadSystemFonts: true, defaultFontFamily: "Segoe UI" },
});

const png = resvg.render().asPng();
writeFileSync(pngPath, png);

const { width, height } = resvg.render();
console.log(
  `Wrote ${pngPath} (${width}x${height}, ${(png.length / 1024).toFixed(0)} KB)`,
);
