import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringCodeBlock } from "../TuringCodeBlock";
import { SkinShowcase, codeBlockClassNames } from "./skins";

/**
 * # TuringCodeBlock
 *
 * Headless fenced code block with a built-in {@link TuringCopyButton}. It does
 * NOT highlight syntax itself — the host markdown pipeline styles the `<code>`
 * children; the `code` slot carries any `language-xxx` class a highlighter
 * expects. Two skins below from one implementation.
 */
const SAMPLE = `function greet(name) {
  return \`Hello, \${name}!\`;
}

console.log(greet("Turing"));`;

const meta: Meta<typeof TuringCodeBlock> = {
  title: "Headless UI/TuringCodeBlock",
  component: TuringCodeBlock,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "`<pre><code>` plus an optional header with a language tag and a copy button.",
      },
    },
  },
  argTypes: {
    code: { control: "text", description: "Source to display and copy" },
    language: { control: "text", description: "Language tag / `language-xxx` hint" },
    showCopy: { control: "boolean", description: "Show the copy button" },
  },
};

export default meta;
type Story = StoryObj<typeof TuringCodeBlock>;

/** ## Two skins, one implementation */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringCodeBlock
          code={SAMPLE}
          language="javascript"
          classNames={codeBlockClassNames[skin]}
          copyIcons={{ copy: "⧉", copied: "✓" }}
        />
      )}
    </SkinShowcase>
  ),
};

/** ## No language / no copy */
export const Minimal: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringCodeBlock
          code={"$ pnpm --filter @viglet/turing-react-ui storybook"}
          showCopy={false}
          classNames={codeBlockClassNames[skin]}
        />
      )}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { code: SAMPLE, language: "javascript", showCopy: true },
  render: (args) => (
    <TuringCodeBlock
      {...args}
      classNames={codeBlockClassNames.shadcn}
      copyIcons={{ copy: "⧉", copied: "✓" }}
    />
  ),
};
