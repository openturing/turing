import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringHtmlSandbox } from "../TuringHtmlSandbox";
import { SkinShowcase, htmlSandboxClassNames } from "./skins";

/**
 * # TuringHtmlSandbox
 *
 * Headless live-preview for an assistant ` ```html ` block — runs the fragment
 * in a sandboxed `<iframe>` (`allow-scripts`, no `allow-same-origin`), with a
 * fullscreen affordance and an optional Code/Preview toggle.
 *
 * **The design contract.** The component ships ZERO styling of its own. The two
 * panels below are the SAME component skinned two ways purely by passing
 * different `classNames` / `labels` / `icons` — the shadcn-neutral look of the
 * Turing admin console and the blue→indigo Viglet brand look of viglet.com.
 */
const SAMPLE_HTML = `<div style="display:grid;place-items:center;height:160px;font-family:system-ui">
  <button id="b" style="padding:10px 18px;border:0;border-radius:10px;cursor:pointer;
    background:linear-gradient(135deg,#2563eb,#4f46e5);color:#fff;font-weight:600">
    Click me — count: <span id="c">0</span>
  </button>
  <script>
    let n = 0;
    document.getElementById('b').addEventListener('click', () => {
      document.getElementById('c').textContent = ++n;
    });
  </script>
</div>`;

const ICONS = {
  fullscreen: "⛶",
  close: "✕",
  code: "</>",
  preview: "▣",
};

const meta: Meta<typeof TuringHtmlSandbox> = {
  title: "Headless UI/TuringHtmlSandbox",
  component: TuringHtmlSandbox,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Sandboxed live-preview of an assistant `html` block. Design-agnostic: skin via classNames / labels / icons.",
      },
    },
  },
  argTypes: {
    code: { control: "text", description: "Self-contained HTML fragment" },
    height: {
      control: { type: "number", min: 80, max: 640, step: 20 },
      description: "Inline preview height (px)",
    },
    showCodeToggle: {
      control: "boolean",
      description: "Show a Code/Preview source toggle",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringHtmlSandbox>;

/**
 * ## Two skins, one implementation
 *
 * The living design contract — admin (shadcn) on the left, viglet.com brand on
 * the right. Only the injected `classNames` / `labels` / `icons` differ.
 */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringHtmlSandbox
          code={SAMPLE_HTML}
          height={200}
          showCodeToggle
          classNames={htmlSandboxClassNames[skin]}
          icons={ICONS}
          labels={{
            title: skin === "viglet" ? "Viglet Preview" : "Preview",
          }}
        />
      )}
    </SkinShowcase>
  ),
};

/**
 * ## Playground
 *
 * Edit the args (Controls tab) and inspect the a11y report. Rendered in the
 * shadcn skin.
 */
export const Playground: Story = {
  args: {
    code: SAMPLE_HTML,
    height: 220,
    showCodeToggle: true,
  },
  render: (args) => (
    <TuringHtmlSandbox
      {...args}
      classNames={htmlSandboxClassNames.shadcn}
      icons={ICONS}
    />
  ),
};
