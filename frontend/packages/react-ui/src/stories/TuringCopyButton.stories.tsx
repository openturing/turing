import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringCopyButton } from "../TuringCopyButton";
import { SkinShowcase, copyButtonClassNames } from "./skins";

/**
 * # TuringCopyButton
 *
 * Headless copy-to-clipboard button with a self-resetting "copied" confirmation.
 * No styling of its own — the `button` / `copied` / `idle` slots and the state
 * `icons` are the host's. The two panels are the same component, two skins.
 */
const ICONS = { copy: "⧉", copied: "✓" };

const meta: Meta<typeof TuringCopyButton> = {
  title: "Headless UI/TuringCopyButton",
  component: TuringCopyButton,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Clipboard button with a transient confirmation. data-turing-copy-button toggles 'idle' / 'copied' for CSS.",
      },
    },
  },
  argTypes: {
    value: { control: "text", description: "Text written to the clipboard" },
    resetMs: {
      control: { type: "number", min: 500, max: 5000, step: 250 },
      description: "How long the confirmation stays (ms)",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringCopyButton>;

/**
 * ## Two skins, one implementation
 *
 * Click either button to see the self-resetting confirmation. The viglet skin
 * flips to an emerald→teal gradient via `[data-turing-copy-button="copied"]`.
 */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringCopyButton
          value="Copied from the Turing react-ui Storybook ✨"
          classNames={copyButtonClassNames[skin]}
          icons={ICONS}
          labels={{ copy: "Copy", copied: "Copied!" }}
        />
      )}
    </SkinShowcase>
  ),
};

/**
 * ## Text-only (no icons)
 *
 * Omit `icons` and the button falls back to its label text — proving the
 * library has no icon-library dependency.
 */
export const TextOnly: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringCopyButton
          value="Plain-text copy button"
          classNames={copyButtonClassNames[skin]}
        />
      )}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { value: "Edit me in Controls", resetMs: 2000 },
  render: (args) => (
    <TuringCopyButton
      {...args}
      classNames={copyButtonClassNames.shadcn}
      icons={ICONS}
    />
  ),
};
