import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringChatMessage } from "../TuringChatMessage";
import { TuringMarkdown } from "../TuringMarkdown";
import { TuringCopyButton } from "../TuringCopyButton";
import { TuringThinkingDots } from "../TuringThinkingDots";
import {
  SkinShowcase,
  chatMessageClassNames,
  copyButtonClassNames,
  thinkingDotsClassNames,
  chipClassName,
  type SkinName,
} from "./skins";

/**
 * # TuringChatMessage
 *
 * Headless chat-message shell — the slot-driven bubble (avatar · author ·
 * status · attachments · content · actions · footer) every Turing chat surface
 * rebuilds by hand. It only lays the slots out and tags the row with
 * `data-role`; each slot's content is the host's. Any unset slot renders
 * nothing (and skips its wrapper), so a bare user message collapses to content.
 *
 * Below: an assistant turn and a user turn, in both skins, composed from the
 * other react-ui atoms (markdown, copy button, thinking dots, chips).
 */
const ANSWER = `Turing combines **semantic search** with generative answers. Ask in natural language and it routes the query, retrieves, and synthesizes a cited reply.`;

function AssistantTurn({ skin }: { skin: SkinName }) {
  return (
    <TuringChatMessage
      role="assistant"
      classNames={chatMessageClassNames[skin]}
      avatar="T"
      name={skin === "viglet" ? "Viglet Turing ES" : "Assistant"}
      actions={
        <TuringCopyButton
          value={ANSWER}
          classNames={copyButtonClassNames[skin]}
          icons={{ copy: "⧉", copied: "✓" }}
        />
      }
      footer={
        <>
          <button type="button" className={chipClassName[skin]}>
            How does ranking work?
          </button>
          <button type="button" className={chipClassName[skin]}>
            Show me an example
          </button>
        </>
      }
    >
      <TuringMarkdown
        className={chatMessageClassNames[skin].content}
      >
        {ANSWER}
      </TuringMarkdown>
    </TuringChatMessage>
  );
}

function UserTurn({ skin }: { skin: SkinName }) {
  return (
    <TuringChatMessage
      role="user"
      classNames={chatMessageClassNames[skin]}
      avatar="You"
      name="You"
    >
      What is Viglet Turing?
    </TuringChatMessage>
  );
}

function StreamingTurn({ skin }: { skin: SkinName }) {
  return (
    <TuringChatMessage
      role="assistant"
      classNames={chatMessageClassNames[skin]}
      avatar="T"
      name={skin === "viglet" ? "Viglet Turing ES" : "Assistant"}
      status={
        <TuringThinkingDots
          classNames={thinkingDotsClassNames[skin]}
          label="Assistant is thinking…"
        />
      }
    >
      <span style={{ opacity: 0.6 }}>Generating a response…</span>
    </TuringChatMessage>
  );
}

const meta: Meta<typeof TuringChatMessage> = {
  title: "Headless UI/TuringChatMessage",
  component: TuringChatMessage,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Slot-driven message bubble (avatar · name · status · attachments · content · actions · footer). data-role drives CSS.",
      },
    },
  },
  argTypes: {
    role: {
      control: "inline-radio",
      options: ["assistant", "user"],
      description: "Which side of the conversation",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringChatMessage>;

/** ## A short conversation, two skins */
export const TwoSkins: Story = {
  render: () => (
    <SkinShowcase>
      {(skin) => (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
          <UserTurn skin={skin} />
          <AssistantTurn skin={skin} />
        </div>
      )}
    </SkinShowcase>
  ),
};

/**
 * ## Streaming state
 *
 * The `status` slot carries a {@link TuringThinkingDots} while a reply streams.
 */
export const Streaming: Story = {
  render: () => (
    <SkinShowcase>{(skin) => <StreamingTurn skin={skin} />}</SkinShowcase>
  ),
};

/**
 * ## Playground
 *
 * Toggle `role` in Controls. Unset slots simply don't render.
 */
export const Playground: Story = {
  args: { role: "assistant" },
  render: (args) => (
    <TuringChatMessage
      {...args}
      classNames={chatMessageClassNames.shadcn}
      avatar={args.role === "user" ? "You" : "T"}
      name={args.role === "user" ? "You" : "Assistant"}
    >
      {args.role === "user"
        ? "What is Viglet Turing?"
        : "Turing is an enterprise search intelligence platform."}
    </TuringChatMessage>
  ),
};
