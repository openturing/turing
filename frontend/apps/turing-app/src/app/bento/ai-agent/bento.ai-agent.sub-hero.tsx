import { ROUTES } from "@/app/routes.const";
import { BentoHero, BENTO_TONE_GRADIENTS, type BentoTone } from "@/components/bento";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

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
}

/**
 * Shared hero for the AI agent's bento sub-sections (settings, LLM, tools,
 * MCP, custom tools, history, intents, chat flows). Renders the standard
 * {@link BentoHero} with a tonal gradient icon chip and an eyebrow that links
 * back to the agent's bento dashboard — so every sub-page reads as "inside"
 * the agent without any console chrome.
 */
export function BentoAgentSubHero({
  agentId,
  agentTitle,
  icon: Icon,
  tone,
  title,
  subtitle,
  trailing,
}: Readonly<BentoAgentSubHeroProps>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];
  return (
    <BentoHero
      eyebrow={
        <Link to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agentId}`} className="hover:text-foreground">
          {agentTitle}
        </Link>
      }
      leading={
        <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
          <Icon size={24} />
        </span>
      }
      title={title}
      subtitle={subtitle}
      trailing={trailing}
    />
  );
}
