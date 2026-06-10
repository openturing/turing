import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringRichContent } from "../TuringRichContent";
import { TuringMarkdown } from "../TuringMarkdown";
import { TuringD2Diagram } from "../TuringD2Diagram";
import {
  SkinShowcase,
  htmlSandboxClassNames,
  d2ClassNames,
  proseClassName,
  type SkinName,
} from "./skins";

/**
 * # TuringRichContent
 *
 * Headless dispatcher for a rich assistant reply: splits the text into ordered
 * markdown / ` ```html ` / ` ```d2 ` segments and routes each to the right
 * widget — prose to the injected `markdown` component, ` ```html ` to a
 * {@link TuringHtmlSandbox}, ` ```d2 ` to the optional `d2` renderer. It carries
 * no markdown / styling dependency itself; the host plugs the renderers in.
 *
 * Both panels share this one dispatcher while keeping their own markdown setup,
 * html skin, and look — exactly the admin ⇄ viglet.com duplication it replaces.
 */
const SAMPLE = `Here's a quick overview, then a live widget and a diagram.

\`\`\`html
<button style="padding:8px 16px;border:0;border-radius:8px;background:#4f46e5;color:#fff;cursor:pointer"
  onclick="this.textContent='Clicked!'">Run me</button>
\`\`\`

And the request pipeline:

\`\`\`d2
search -> index -> results
\`\`\`

That's all — **markdown around the widgets** still renders as prose.`;

const fakeRender = (code: string) =>
  Promise.resolve(
    `<svg xmlns="http://www.w3.org/2000/svg" width="280" height="64" viewBox="0 0 280 64">
       <rect x="2" y="2" width="276" height="60" rx="10" fill="#f5f3ff" stroke="#c7d2fe"/>
       <text x="140" y="38" text-anchor="middle" font-family="ui-monospace" font-size="11" fill="#4f46e5">${code.replace(/\n/g, " · ")}</text>
     </svg>`,
  );

/** Builds the per-skin renderer set the dispatcher needs. */
function renderersFor(skin: SkinName) {
  const Markdown = ({ text }: { text: string }) => (
    <TuringMarkdown className={proseClassName[skin]}>{text}</TuringMarkdown>
  );
  const D2 = ({ code }: { code: string }) => (
    <TuringD2Diagram code={code} render={fakeRender} classNames={d2ClassNames[skin]} />
  );
  return { Markdown, D2 };
}

const meta: Meta<typeof TuringRichContent> = {
  title: "Headless UI/TuringRichContent",
  component: TuringRichContent,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Splits an assistant reply into markdown / html / d2 segments and dispatches each to an injected renderer.",
      },
    },
  },
  argTypes: {
    content: { control: "text", description: "Raw assistant reply" },
  },
};

export default meta;
type Story = StoryObj<typeof TuringRichContent>;

/** ## Two skins, one implementation */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => {
        const { Markdown, D2 } = renderersFor(skin);
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            <TuringRichContent
              content={SAMPLE}
              markdown={Markdown}
              d2={D2}
              htmlSandbox={{
                height: 96,
                classNames: htmlSandboxClassNames[skin],
                icons: { fullscreen: "⛶", close: "✕" },
              }}
            />
          </div>
        );
      }}
    </SkinShowcase>
  ),
};

/** ## Playground */
export const Playground: Story = {
  args: { content: SAMPLE },
  render: (args) => {
    const { Markdown, D2 } = renderersFor("shadcn");
    return (
      <TuringRichContent
        {...args}
        markdown={Markdown}
        d2={D2}
        htmlSandbox={{
          height: 96,
          classNames: htmlSandboxClassNames.shadcn,
          icons: { fullscreen: "⛶", close: "✕" },
        }}
      />
    );
  },
};
