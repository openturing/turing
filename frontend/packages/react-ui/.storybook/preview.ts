import type { Preview } from "@storybook/react-vite";
// The two demo skins (shadcn-neutral vs viglet-brand) that every story dresses
// the headless components in. Loading it here makes the literal `.shadcn-*` /
// `.viglet-*` classes from `skins.ts` resolve in every story — the library
// itself ships ZERO styling, so the look lives entirely in this stylesheet.
import "../src/stories/skins.css";

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    docs: {
      toc: true,
    },
    a11y: {
      // Surface violations in the a11y panel without failing the story render —
      // these headless primitives are skinned by the host, so contrast etc. is
      // the host's responsibility; we still want the report visible.
      test: "todo",
    },
  },
};

export default preview;
