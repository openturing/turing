import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringD2Diagram } from "../TuringD2Diagram";
import { SkinShowcase, d2ClassNames } from "./skins";

/**
 * # TuringD2Diagram
 *
 * Headless renderer for an assistant ` ```d2 ` block. The default engine is a
 * LAZY `import("@terrastruct/d2")` (optional peer, code-split & tree-shaken when
 * unused). When the peer is missing OR the source fails, it falls back to the
 * raw source so nothing is lost.
 *
 * Storybook doesn't install the heavy WASM engine, so the stories pass a `render`
 * stub: a fake SVG (the "ready" state) and a rejecting one (the source fallback).
 */
const SAMPLE = `direction: right
search -> index -> rank -> results`;

/** Stand-in renderer: returns a hand-rolled SVG so the "ready" state is visible. */
const fakeRender = (code: string) =>
  Promise.resolve(
    `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="80" viewBox="0 0 320 80">
       <rect x="2" y="2" width="316" height="76" rx="10" fill="#eef2ff" stroke="#c7d2fe"/>
       <text x="160" y="34" text-anchor="middle" font-family="ui-sans-serif" font-size="13" fill="#4f46e5" font-weight="600">D2 diagram (stub SVG)</text>
       <text x="160" y="54" text-anchor="middle" font-family="ui-monospace" font-size="10" fill="#6366f1">${code.replace(/\n/g, " · ")}</text>
     </svg>`,
  );

/** Rejecting renderer: forces the lossless source fallback. */
const failingRender = () => Promise.reject(new Error("engine unavailable"));

const meta: Meta<typeof TuringD2Diagram> = {
  title: "Headless UI/TuringD2Diagram",
  component: TuringD2Diagram,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Compiles D2 source to SVG via a lazily-loaded optional engine; falls back to the source when unavailable.",
      },
    },
  },
  argTypes: {
    code: { control: "text", description: "Raw ```d2 diagram source" },
    sketch: { control: "boolean", description: "Hand-drawn mode (default engine)" },
  },
};

export default meta;
type Story = StoryObj<typeof TuringD2Diagram>;

/** ## Two skins, one implementation (rendered SVG) */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringD2Diagram
          code={SAMPLE}
          render={fakeRender}
          classNames={d2ClassNames[skin]}
        />
      )}
    </SkinShowcase>
  ),
};

/**
 * ## Source fallback
 *
 * When the engine can't render (optional peer missing or invalid D2), the raw
 * source is shown — losslessly — beneath an error caption.
 */
export const SourceFallback: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringD2Diagram
          code={SAMPLE}
          render={failingRender}
          classNames={d2ClassNames[skin]}
          labels={{ error: "Could not render diagram" }}
        />
      )}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { code: SAMPLE, sketch: false },
  render: (args) => (
    <TuringD2Diagram
      {...args}
      render={fakeRender}
      classNames={d2ClassNames.shadcn}
    />
  ),
};
