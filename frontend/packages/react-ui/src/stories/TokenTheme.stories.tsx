import type { Meta, StoryObj } from "@storybook/react-vite";
import { type CSSProperties } from "react";
import { TuringChatMessage } from "../TuringChatMessage";
import { TuringCodeBlock } from "../TuringCodeBlock";
import { TuringCopyButton } from "../TuringCopyButton";
import { TuringHtmlSandbox } from "../TuringHtmlSandbox";
import { TuringThinkingDots } from "../TuringThinkingDots";
import { type TuringUiTheme } from "../tokens";
// The OPTIONAL token-theming stylesheet — the artifact apps import as
// `@viglet/turing-react-ui/styles.css`. Here we load the source directly.
import "../styles/turing-ui.css";

/**
 * # Token theming (T305)
 *
 * The **alternative to per-slot `classNames`**: import
 * `@viglet/turing-react-ui/styles.css`, wrap a subtree in
 * `class="turing-ui-theme"`, and reskin every component by setting a few
 * `--turing-ui-*` CSS custom properties on the wrapper. No class map per
 * component — same headless implementation, themed by tokens.
 *
 * Each panel below renders the EXACT same components; only the wrapper's tokens
 * differ. The first uses the baked-in defaults (zero tokens set).
 */
const meta: Meta = {
  title: "Headless UI/Token theming",
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Theme the headless components via `--turing-ui-*` CSS custom properties on a `.turing-ui-theme` wrapper — an alternative to `classNames`.",
      },
    },
  },
};

export default meta;
type Story = StoryObj;

const PONG = `<canvas id="c" width="220" height="120" style="background:#111"></canvas>
<script>
const x=document.getElementById('c').getContext('2d');let p=10;
setInterval(()=>{x.clearRect(0,0,220,120);x.fillStyle='#4ade80';x.fillRect(p,50,18,18);p=(p+4)%220;},60);
</script>`;

/** The token overrides for each demo panel (typed via {@link TuringUiTheme}). */
const PRESETS: { name: string; theme: TuringUiTheme }[] = [
  { name: "Defaults (no tokens set)", theme: {} },
  {
    name: "Viglet brand",
    theme: {
      "--turing-ui-accent": "#4f46e5",
      "--turing-ui-accent-hover": "#4338ca",
      "--turing-ui-radius": "1rem",
      "--turing-ui-surface-muted": "#f5f3ff",
      "--turing-ui-border": "#c7d2fe",
      "--turing-ui-text": "#1e1b4b",
    },
  },
  {
    name: "Dark",
    theme: {
      "--turing-ui-surface": "#18181b",
      "--turing-ui-surface-muted": "#27272a",
      "--turing-ui-border": "#3f3f46",
      "--turing-ui-text": "#f4f4f5",
      "--turing-ui-text-muted": "#a1a1aa",
      "--turing-ui-accent": "#818cf8",
      "--turing-ui-accent-hover": "#a5b4fc",
    },
  },
];

function Demo() {
  return (
    <>
      <TuringChatMessage
        role="assistant"
        name="Turing"
        avatar="T"
        status={<TuringThinkingDots count={3} />}
      >
        Here&apos;s a live <code>&lt;canvas&gt;</code> demo and a snippet —{" "}
        <a href="#">read the docs</a>.
      </TuringChatMessage>
      <TuringHtmlSandbox code={PONG} height={120} showCodeToggle />
      <TuringCodeBlock
        code={'const greet = (n) => `Hi ${n}`;'}
        language="javascript"
      />
      <div>
        <TuringCopyButton value="copied!" labels={{ copy: "Copy", copied: "Copied ✓" }} />
      </div>
    </>
  );
}

const panel: CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: "0.75rem",
  padding: "1.25rem",
  minWidth: 0,
};

const caption: CSSProperties = {
  fontSize: "0.6875rem",
  fontWeight: 700,
  letterSpacing: "0.08em",
  textTransform: "uppercase",
  color: "#71717a",
  fontFamily: "ui-sans-serif, system-ui, sans-serif",
};

/**
 * ## Same components, themed by tokens
 *
 * Three wrappers, one implementation — the only difference between panels is the
 * `--turing-ui-*` tokens on the `.turing-ui-theme` element.
 */
export const Presets: Story = {
  render: () => (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
      }}
    >
      {PRESETS.map((p) => (
        <div
          key={p.name}
          className="turing-ui-theme"
          style={{
            ...p.theme,
            background: p.theme["--turing-ui-surface"] ?? "#ffffff",
          }}
        >
          <div style={panel}>
            <span style={caption}>{p.name}</span>
            <Demo />
          </div>
        </div>
      ))}
    </div>
  ),
};
