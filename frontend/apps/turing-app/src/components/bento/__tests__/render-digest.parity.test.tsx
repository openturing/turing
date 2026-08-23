import { IconCpu2, IconSettings } from "@tabler/icons-react";
import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

// The app's own stylesheet chain, in the order main.tsx loads it: Tailwind v4,
// the design-system preset (which declares the accent tokens and derives the
// surfaces), and this app's `@theme inline` + base layer on top. Importing it
// here is the point of the file — see the note below.
import "@/index.css";

import {
  BentoEntityTile,
  BentoFormSection,
  BentoHero,
  BentoScrollSaveBar,
  BentoTileGrid,
} from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";

/**
 * T1001 — a page is proved unchanged by the move, not assumed.
 *
 * Every deletion in Block BX rests on one claim: a page renders the same when
 * its components arrive from `@viglet/viglet-design-system` instead of from a
 * folder in this repository. Nothing here checked it. `tsc` sees an import
 * path. `package-parity.test.tsx` next door sees roles and class names and says
 * so in its own doc comment — "not the styling itself: jsdom computes no CSS".
 * Neither sees a frosted surface that stopped being frosted because a
 * stylesheet was not imported, or a hover lift that no longer lifts.
 *
 * The package runs a digest of its own. This one is not a copy of it, because
 * it asks a question that cannot be asked there: the package renders its
 * components under the package's stylesheet, and this product renders them
 * under `src/index.css` — Tailwind's preflight, the `@theme inline` block that
 * maps shadcn's variables, and a base layer that sets `bg-background
 * text-foreground` on `body`. That extra layer exists only here, sits above the
 * shared components only here, and is exactly where a product-local rule would
 * reflow a leaf the package believes it owns.
 *
 * So: render this product's five surfaces once per accent, in a browser, and
 * digest what it resolved. Three claims —
 *
 *   * no structural property moves when the accent is re-keyed;
 *   * each accent-bearing surface moves in colour, checked one surface at a
 *     time; and
 *   * each tone-driven surface does not move at all.
 *
 * The last two are the pair a copied test would miss, and they are what the
 * layering actually says: the accent is the product's colour and lives in the
 * controls, while a tile is painted by the tone its caller chose. Checking the
 * composition as a whole cannot tell those apart — a surface that renders but
 * resolves no token hides behind the ones that still work, and a tone quietly
 * repainted by the accent is drowned out by the buttons that were supposed to
 * move. Per surface, neither has anywhere to go.
 */

/**
 * Three accents far enough apart that a hardcoded hue cannot hide between any
 * two. The values are the package's own contract: four properties on `:root`,
 * from which the preset derives the tint, the hairline and the fill via
 * `color-mix`. Setting them on a wrapper instead would re-key only what a
 * utility class mixes inline and leave every derived token at the root's value.
 */
const TOKEN_SETS = {
  cool: {
    "--vg-accent-from": "oklch(62.3% 0.214 259.815)",
    "--vg-accent-to": "oklch(58.5% 0.233 277.117)",
    "--vg-accent-text": "oklch(54.6% 0.245 262.881)",
    "--vg-accent-text-dark": "oklch(70.7% 0.165 254.624)",
  },
  warm: {
    "--vg-accent-from": "oklch(70.5% 0.213 47.604)",
    "--vg-accent-to": "oklch(64.6% 0.222 41.116)",
    "--vg-accent-text": "oklch(50.5% 0.185 38.402)",
    "--vg-accent-text-dark": "oklch(75% 0.183 55.934)",
  },
  green: {
    "--vg-accent-from": "oklch(69.6% 0.17 162.48)",
    "--vg-accent-to": "oklch(60% 0.118 184.704)",
    "--vg-accent-text": "oklch(50.8% 0.118 165.612)",
    "--vg-accent-text-dark": "oklch(76.5% 0.177 163.223)",
  },
} as const;

/**
 * Where things are and how big they are. A re-key must not move one of them:
 * that is what "the page looks the same" means, and it is the half a person
 * comparing two browser windows is worst at.
 */
const STRUCTURAL = [
  "display",
  "position",
  "flex-direction",
  "flex-wrap",
  "align-items",
  "justify-content",
  "grid-template-columns",
  "grid-template-rows",
  "grid-column",
  "grid-row",
  "gap",
  "padding",
  "margin",
  "border-width",
  "border-radius",
  "font-size",
  "font-weight",
  "line-height",
  "letter-spacing",
  "text-align",
  "overflow",
  "opacity",
] as const;

/** The properties that carry colour — the only ones a re-key may change. */
const CHROMATIC = [
  "color",
  "background-color",
  "background-image",
  "border-color",
  "box-shadow",
  "outline-color",
  "fill",
  "stroke",
] as const;

/**
 * The five surfaces the 118 bento pages are built from, each under a marker so
 * a colour check can be made per surface rather than over the whole page.
 */
const SURFACES = ["hero", "form-section", "entity-tile", "list-mosaic", "save-bar"] as const;
type Surface = (typeof SURFACES)[number];

/**
 * The accent is a product's colour and lives in the controls: the hero's
 * actions, the form's switch and buttons, the save bar's Save. These three must
 * move when it is re-keyed.
 */
const ACCENT_BEARING = ["hero", "form-section", "save-bar"] as const satisfies readonly Surface[];

/**
 * A tile is painted by the tone its caller passed, and a tone is not the
 * accent. These two must not move by a single property — measured here across
 * 20 and 48 colour-bearing elements respectively, which is a stronger statement
 * than the tonal chips alone.
 */
const TONE_DRIVEN = ["entity-tile", "list-mosaic"] as const satisfies readonly Surface[];

function Composition() {
  const items = [
    { id: "a", title: "Alpha", description: "First" },
    { id: "b", title: "Beta", description: "Second" },
  ];
  return (
    <div style={{ width: 1200 }}>
      <div data-surface="hero">
        <BentoHero
          title="Parity"
          subtitle="One composition, every accent."
          backTo="/console"
          backLabel="Console"
          // Every bento hero in this product carries its page's actions here,
          // and that is where its accent lives: the hero's own leaves are
          // `text-muted-foreground` by design, so a hero composed without
          // actions reads no token and this file would be asserting nothing
          // about it.
          trailing={<GradientButton>New</GradientButton>}
        />
      </div>

      <div data-surface="form-section">
        <BentoFormSection
          icon={IconSettings}
          tone="blue"
          title="Settings"
          description="A form section."
        >
          <input aria-label="Name" defaultValue="Turing" />
          <GradientSwitch defaultChecked aria-label="Enabled" />
          <GradientButton variant="outline">Cancel</GradientButton>
        </BentoFormSection>
      </div>

      <div data-surface="entity-tile">
        <BentoEntityTile
          to="/console/e/a"
          defaultIcon={IconCpu2}
          tone="blue"
          title="An entity"
          description="With a description"
          hasStatus
          enabled={1}
        />
      </div>

      <div data-surface="list-mosaic">
        <BentoTileGrid
          items={items}
          tryAgainUrl="/retry"
          tone="indigo"
          newRoute="/console/e/new"
          newLabel="New"
          itemKey={(i) => i.id}
          renderTile={(i) => (
            <BentoEntityTile
              to={`/console/e/${i.id}`}
              defaultIcon={IconCpu2}
              tone="blue"
              title={i.title}
              description={i.description}
            />
          )}
          emptyTitle="Nothing"
          emptyDescription="Nothing yet"
        />
      </div>

      <div data-surface="save-bar">
        <BentoScrollSaveBar title="An entity" dirty />
      </div>
    </div>
  );
}

/**
 * One line per element, in document order, so a failure names the element that
 * moved instead of reporting that two long strings differ. The path is
 * structural — tag plus index among same-tag siblings — rather than a class
 * name: a class name carries the token into the key and hides the very
 * difference being looked for.
 */
function digest(root: Element, properties: readonly string[]): string[] {
  const lines: string[] = [];

  const walk = (el: Element, path: string) => {
    const style = getComputedStyle(el);
    const values = properties.map((p) => `${p}=${style.getPropertyValue(p).trim()}`);
    const box = el as HTMLElement;
    lines.push(
      [
        path,
        // Rounded: sub-pixel noise between two runs is not a layout change.
        `box=${Math.round(box.offsetWidth)}x${Math.round(box.offsetHeight)}`,
        ...values,
      ].join(" "),
    );

    const byTag = new Map<string, number>();
    for (const child of Array.from(el.children)) {
      const tag = child.tagName.toLowerCase();
      const n = byTag.get(tag) ?? 0;
      byTag.set(tag, n + 1);
      walk(child, `${path}>${tag}[${n}]`);
    }
  };

  walk(root, root.tagName.toLowerCase());
  return lines;
}

/** The colour digest of one surface, keyed by its marker. */
function digestPerSurface(host: HTMLElement): Record<Surface, string[]> {
  const out = {} as Record<Surface, string[]>;
  for (const surface of SURFACES) {
    const el = host.querySelector(`[data-surface="${surface}"]`);
    out[surface] = el ? digest(el, CHROMATIC) : [];
  }
  return out;
}

/** The tonal chips, whose colour the caller chose and a re-key must not touch. */
function toneChips(host: HTMLElement): string[] {
  return Array.from(host.querySelectorAll("[class*='bento-tone-']")).map((el) => {
    const s = getComputedStyle(el);
    return `${s.backgroundImage}|${s.color}`;
  });
}

function renderUnder(tokens: Record<string, string>, tree: ReactElement): HTMLElement {
  const root = document.documentElement;
  for (const [name, value] of Object.entries(tokens)) root.style.setProperty(name, value);

  const host = document.createElement("div");
  document.body.appendChild(host);
  render(<MemoryRouter>{tree}</MemoryRouter>, { container: host });
  return host;
}

function clearTokens() {
  const root = document.documentElement;
  for (const set of Object.values(TOKEN_SETS)) {
    for (const name of Object.keys(set)) root.style.removeProperty(name);
  }
  // BentoScrollSaveBar mounts useBentoScrollFade, which drives the morph by
  // writing this on <html>. Left behind, the next render starts mid-morph and
  // the save bar's opacity differs for a reason that is not the accent.
  root.style.removeProperty("--bento-fade");
  root.classList.remove("bento-fade-half");
}

describe("the bento surfaces, rendered from the package under this app's stylesheet", () => {
  // Digested one at a time: the tokens live on the shared root, so two trees
  // mounted at once would both read whichever set was applied last.
  const digests = Object.entries(TOKEN_SETS).map(([name, tokens]) => {
    const host = renderUnder(tokens, <Composition />);
    const measured = {
      name,
      structure: digest(host, STRUCTURAL),
      colour: digest(host, CHROMATIC),
      perSurface: digestPerSurface(host),
      text: host.textContent ?? "",
      tones: toneChips(host),
    };
    host.remove();
    clearTokens();
    return measured;
  });

  const [first, ...rest] = digests;


  /** Every differing colour line between two runs of one surface. */
  function movedIn(
    a: (typeof digests)[number],
    b: (typeof digests)[number],
    surface: Surface,
  ): string[] {
    const mine = a.perSurface[surface];
    const theirs = b.perSurface[surface];
    return mine
      .map((line, i) => [line, theirs[i]] as const)
      .filter(([x, y]) => x !== y)
      .map(([x, y]) => `\n  ${a.name}: ${x}\n  ${b.name}: ${y}`);
  }

  it("mounted all five surfaces — the assertions below have something to read", () => {
    expect(first.structure.length).toBeGreaterThan(40);
    expect(first.text).toContain("Parity");
    expect(first.text).toContain("Settings");
    expect(first.text).toContain("An entity");
    expect(first.text).toContain("Alpha");
    for (const surface of SURFACES) {
      expect(
        first.perSurface[surface].length,
        `the ${surface} surface rendered nothing`,
      ).toBeGreaterThan(0);
    }
  });

  it("moves nothing: every structural property survives a re-key", () => {
    for (const other of rest) {
      expect(
        first.structure.length,
        `${first.name} and ${other.name} produced different element counts`,
      ).toBe(other.structure.length);

      const moved = first.structure
        .map((line, i) => [line, other.structure[i]] as const)
        .filter(([x, y]) => x !== y);
      expect(
        moved.map(([x, y]) => `\n  ${first.name}: ${x}\n  ${other.name}: ${y}`),
        "a re-key changed the layout, which is what it must never do",
      ).toEqual([]);
    }
  });

  it("every accent-bearing surface moves — one at a time, not as a crowd", () => {
    // The check the package's own digest cannot make, because it checks the
    // composition as a whole. A surface whose stylesheet never arrived still
    // renders and still holds its class names; it just resolves no token. Over
    // a whole page that is the working surfaces outvoting it. Per surface, it
    // has nobody to hide behind.
    for (const other of rest) {
      for (const surface of ACCENT_BEARING) {
        expect(
          movedIn(first, other, surface).length,
          `${surface}: the accent reached nothing — this surface is not resolving the tokens`,
        ).toBeGreaterThan(0);
      }
    }
  });

  it("every tone-driven surface stays exactly where it was", () => {
    // The other half, and the reason this is a contract rather than an
    // observation: a tile is painted by the tone its caller passed, so the
    // product's accent must not reach it at all. `toneChips` below checks the
    // chips; this checks every colour-bearing property of the whole surface,
    // which is what catches an accent leaking in through a container.
    for (const other of rest) {
      for (const surface of TONE_DRIVEN) {
        expect(
          movedIn(first, other, surface),
          `${surface}: a re-key repainted a surface the caller tones`,
        ).toEqual([]);
      }
    }
  });

  it("leaves the tones alone — they are the caller's choice, not the accent", () => {
    expect(first.tones.length, "no toned chip rendered, so this asserts nothing").toBeGreaterThan(0);
    for (const other of rest) {
      expect(other.tones, `${other.name} repainted a tone the caller chose`).toEqual(first.tones);
    }
  });
});
