import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringThinkingDots } from "../TuringThinkingDots";
import { SkinShowcase, thinkingDotsClassNames } from "./skins";

/**
 * # TuringThinkingDots
 *
 * Headless "assistant is thinking" typing indicator. The component only lays out
 * the dots and staggers each one's `animation-delay`; the bounce keyframe itself
 * is the host's (here `@keyframes sb-dot-bounce` in `skins.css`). The two skins
 * use grey dots vs a blue→indigo gradient.
 */
const meta: Meta<typeof TuringThinkingDots> = {
  title: "Headless UI/TuringThinkingDots",
  component: TuringThinkingDots,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Staggered typing-indicator dots. role=status + aria-label for screen readers; the keyframe is host-supplied.",
      },
    },
  },
  argTypes: {
    count: {
      control: { type: "number", min: 1, max: 6, step: 1 },
      description: "How many dots",
    },
    delayStep: {
      control: { type: "number", min: 0, max: 0.5, step: 0.02 },
      description: "Seconds added to each successive dot's animation-delay",
    },
    label: { control: "text", description: "Screen-reader label" },
  },
};

export default meta;
type Story = StoryObj<typeof TuringThinkingDots>;

/** ## Two skins, one implementation */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringThinkingDots
          classNames={thinkingDotsClassNames[skin]}
          label="Assistant is thinking…"
        />
      )}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { count: 3, delayStep: 0.18, label: "Thinking…" },
  render: (args) => (
    <TuringThinkingDots {...args} classNames={thinkingDotsClassNames.viglet} />
  ),
};
