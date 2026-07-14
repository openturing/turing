import { ROUTES } from "@/app/routes.const";
import { BentoFormHero, BentoHero, BENTO_TONE_GRADIENTS, type BentoTone } from "@/components/bento";
import { type Icon as TablerIcon } from "@tabler/icons-react";
import type { ReactNode } from "react";

export interface BentoAgentSubHeroProps {
  /** Agent id — the eyebrow links back to this agent's bento dashboard. */
  agentId: string;
  /** Agent title shown as the eyebrow (breadcrumb back-link). */
  agentTitle: ReactNode;
  icon: TablerIcon;
  tone: BentoTone;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  /**
   * Form mode. When `onCancel` is provided the sub-hero renders as a
   * {@link BentoFormHero}: Save/Cancel live in the hero and fade into a fixed
   * sticky save-bar on scroll (the standard morph), instead of the page
   * rendering a separate save bar below the header. Render it as the first
   * child **inside the `<form>`** it submits (the Save button is a plain
   * `type="submit"`). Omit for the read-only sub-pages (lists, viewers).
   */
  onCancel?: () => void;
  loading?: boolean;
  dirty?: boolean;
  titleMissing?: boolean;
  saveDisabled?: boolean;
}

/**
 * Shared hero for the AI agent's bento sub-sections (settings, LLM, tools,
 * MCP, custom tools, history, intents, chat flows). Renders the standard
 * {@link BentoHero} with a tonal gradient icon chip and an eyebrow that links
 * back to the agent's bento dashboard — so every sub-page reads as "inside"
 * the agent without any console chrome. Form sub-pages pass the save props to
 * get the hero → sticky-save-bar morph (see {@link BentoAgentSubHeroProps.onCancel}).
 */
export function BentoAgentSubHero({
  agentId,
  agentTitle,
  icon: Icon,
  tone,
  title,
  subtitle,
  trailing,
  onCancel,
  loading,
  dirty,
  titleMissing,
  saveDisabled,
}: Readonly<BentoAgentSubHeroProps>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const backTo = `${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agentId}`;
  const leading = (
    <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
      <Icon size={24} />
    </span>
  );

  // Form mode → the standard BentoFormHero (Save/Cancel in the hero + sticky
  // save-bar morph). Read-only mode → a plain BentoHero.
  if (onCancel) {
    return (
      <BentoFormHero
        backTo={backTo}
        backLabel={agentTitle}
        leading={leading}
        title={title}
        subtitle={subtitle}
        trailing={trailing}
        onCancel={onCancel}
        loading={loading}
        dirty={dirty}
        titleMissing={titleMissing}
        saveDisabled={saveDisabled}
        stickyTitle={typeof title === "string" ? title : undefined}
      />
    );
  }

  return (
    <BentoHero
      backTo={backTo}
      backLabel={agentTitle}
      leading={leading}
      title={title}
      subtitle={subtitle}
      trailing={trailing}
    />
  );
}
