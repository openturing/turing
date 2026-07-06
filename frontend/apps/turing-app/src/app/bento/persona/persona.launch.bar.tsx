import { CHAT_INITIAL_PROMPT_KEY } from "@/app/console/chat/chat.types";
import { ROUTES } from "@/app/routes.const";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "@/components/bento";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import {
  IconFileSearch,
  IconMasksTheater,
  IconMessageChatbot,
  IconRocket,
  type Icon as TablerIcon,
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

interface LaunchAction {
  key: string;
  label: string;
  icon: TablerIcon;
  tone: BentoTone;
  /** Live action handler; omitted for "soon" affordances. */
  onSelect?: () => void;
  /** Renders disabled with a "Soon" badge to communicate the roadmap. */
  soon?: boolean;
}

/**
 * Persona Launchpad — Block AI / §XXXII.6 (T583). Mirrors the
 * {@link AiAgentLaunchBar} but is built as an **extensible** launch bar from day
 * one: **Open chat** is live (navigates to the deterministic, shareable
 * `/bento/chat/persona/:id` route, T580) while future launch actions —
 * content-fit validation (T584) and persona↔persona dialogue (T585) — render as
 * disabled "soon" affordances so the surface communicates the roadmap without
 * shipping half-features. New actions dock here by appending to `actions`.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function PersonaLaunchBar({ persona }: Readonly<{ persona: TurPersona }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  function openChat() {
    sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY);
    navigate(`${ROUTES.BENTO_CHAT_PERSONA}/${persona.id}`);
  }

  const actions: LaunchAction[] = [
    {
      key: "chat",
      label: t("persona.launch.openChat", { defaultValue: "Open chat" }),
      icon: IconMessageChatbot,
      tone: "blue",
      onSelect: openChat,
    },
    {
      key: "validate",
      label: t("persona.launch.validateContent", { defaultValue: "Validate content" }),
      icon: IconFileSearch,
      tone: "emerald",
      soon: true,
    },
    {
      key: "dialogue",
      label: t("persona.launch.personaDialogue", { defaultValue: "Persona dialogue" }),
      icon: IconMasksTheater,
      tone: "rose",
      soon: true,
    },
  ];

  return (
    <section className="mb-6">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconRocket size={16} className="text-blue-500" />
        {t("persona.launch.title", { defaultValue: "Launch" })}
      </h2>
      <div className="flex flex-wrap gap-2.5">
        {actions.map((action) => (
          <LaunchButton key={action.key} action={action} soonLabel={t("persona.launch.soon", { defaultValue: "Soon" })} />
        ))}
      </div>
    </section>
  );
}

function LaunchButton({ action, soonLabel }: Readonly<{ action: LaunchAction; soonLabel: string }>) {
  const Icon = action.icon;
  const chip: ReactNode = (
    <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-linear-to-br ${BENTO_TONE_GRADIENTS[action.tone]} text-white shadow-sm`}>
      <Icon size={16} />
    </span>
  );

  if (action.soon) {
    return (
      <span
        className="bento-glass inline-flex cursor-not-allowed items-center gap-2.5 rounded-2xl px-4 py-2.5 text-sm font-medium opacity-60"
        aria-disabled="true"
        title={soonLabel}
      >
        {chip}
        {action.label}
        <span className="rounded-full border border-border/60 bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wider text-muted-foreground">
          {soonLabel}
        </span>
      </span>
    );
  }

  return (
    <button
      type="button"
      onClick={action.onSelect}
      className="bento-tile bento-tile-clickable bento-glass inline-flex items-center gap-2.5 rounded-2xl px-4 py-2.5 text-sm font-medium"
    >
      {chip}
      {action.label}
    </button>
  );
}
