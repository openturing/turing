import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringMarkdown } from "../TuringMarkdown";
import { SkinShowcase, proseClassName } from "./skins";

/**
 * # TuringMarkdown
 *
 * Headless `react-markdown` wrapper — one markdown contract for every Turing
 * surface. It adds a `sandbox:` artifact `urlTransform`, space-escaping for URLs
 * with spaces, and injectable remark/rehype plugins + component overrides.
 * `react-markdown` is an OPTIONAL peer the host supplies. Prose styling (the
 * `prose` wrapper) is the host's — shown two ways below.
 */
const SAMPLE = `## Markdown renders the same, styled differently

The library owns **structure**, the host owns the **look**.

- semantic search
- generative answers
- a link with spaces: [the chart](sandbox:/api/artifacts/chart 1.png)

\`\`\`ts
const sdk = createTuringClient({ baseUrl: "/api" });
\`\`\`

> Skin the \`prose\` wrapper per app — nothing else changes.`;

const meta: Meta<typeof TuringMarkdown> = {
  title: "Headless UI/TuringMarkdown",
  component: TuringMarkdown,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "react-markdown wrapper with a built-in sandbox: urlTransform and URL space-escaping.",
      },
    },
  },
  argTypes: {
    children: { control: "text", description: "Markdown source" },
    baseUrl: {
      control: "text",
      description: "Turing host base for absolute `sandbox:` / `/api` URLs",
    },
    encodeUrlSpaces: {
      control: "boolean",
      description: "Angle-bracket-escape spaces in link/image URLs",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringMarkdown>;

/** ## Two skins, one implementation */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <TuringMarkdown className={proseClassName[skin]}>
          {SAMPLE}
        </TuringMarkdown>
      )}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { children: SAMPLE, baseUrl: "", encodeUrlSpaces: true },
  render: (args) => (
    <TuringMarkdown {...args} className={proseClassName.viglet} />
  ),
};
