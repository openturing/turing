import { useFeatures } from "@/api/queries/features.queries";
import { useSnChatEnabled } from "@/api/queries/sn-chat-enabled.queries";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import {
  buildSnSearchLaunchers,
  type SnLauncher,
} from "@/components/sn/sn-search-launchers";
import { bentoChipClass, type BentoTone } from "@/components/bento";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { IconAtom, IconChevronDown, IconExternalLink, IconSearch, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

const TILE = "bento-tile bento-tile-clickable bento-glass inline-flex items-center gap-2.5 rounded-2xl px-4 py-2.5 text-sm font-medium";

function LauncherTile({
  launcher,
  icon: Icon,
  tone,
}: Readonly<{ launcher: SnLauncher; icon: TablerIcon; tone: BentoTone }>) {
  const chip = (
    <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-xl ${bentoChipClass(tone)} text-white shadow-sm`}>
      <Icon size={16} />
    </span>
  );

  // Single locale → a plain external link. Multi-locale → a dropdown whose
  // first item is the default (all-locales) URL, then one item per locale.
  if (launcher.items.length === 0) {
    return (
      <a href={launcher.url} target="_blank" rel="noopener noreferrer" className={TILE}>
        {chip}
        {launcher.label}
        <IconExternalLink size={14} className="text-muted-foreground" />
      </a>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={TILE}>
        {chip}
        {launcher.label}
        <IconChevronDown size={14} className="text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem asChild>
          <a href={launcher.url} target="_blank" rel="noopener noreferrer">
            <IconExternalLink className="mr-2 size-4" />
            {launcher.label}
          </a>
        </DropdownMenuItem>
        {launcher.items.map((item) => (
          <DropdownMenuItem key={item.url} asChild>
            <a href={item.url} target="_blank" rel="noopener noreferrer">
              {item.label}
            </a>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * A launcher whose gate is closed: the tile stays visible but inert, naming the
 * condition to fix. T853 — a tile that silently disappears teaches an operator
 * nothing; the backend probe already knows *why* the site cannot answer.
 */
function DisabledTile({
  label,
  reason,
  icon: Icon,
}: Readonly<{ label: string; reason: string; icon: TablerIcon }>) {
  return (
    <span
      className={`${TILE} cursor-not-allowed opacity-60`}
      title={reason}
      aria-disabled="true"
    >
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-muted text-muted-foreground">
        <Icon size={16} />
      </span>
      <span className="flex flex-col items-start leading-tight">
        {label}
        <span className="text-[11px] font-normal text-muted-foreground">{reason}</span>
      </span>
    </span>
  );
}

/**
 * Bento SN "launch" bar — the visitor-facing search launchers the console grid
 * cards carry (Open Search + ANN Search + AI Answer), surfaced on the bento
 * detail page where "Configure" is redundant (the page itself is the config).
 * External links open in a new tab; multi-locale sites get a per-locale dropdown.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function SnLaunchBar({ site }: Readonly<{ site: TurSNSite }>) {
  const { t } = useTranslation();
  const { data: features } = useFeatures();
  // T853 / §LXIV.2 — the answer lens is gated by the BACKEND readiness probe, not
  // by a second frontend derivation: only `diagnoseAgentReadiness` knows that a
  // VECTORLESS_STRUCTURED site answers through the catalog copilot with no
  // embedding model and no vector store.
  const { data: chatEnabled, isLoading: probing } = useSnChatEnabled(site.name);

  // ANN is available when the site's OWN agent is RAG-ready, or — when the site
  // declares no agent — the global Default AI Agent is (T622 fallback, surfaced
  // by /api/features). Mirrors the backend gate in TurDefaultAgentResolver.isRagReady.
  // T790 — a site explicitly in VECTORLESS_STRUCTURED has no vectors by design, so
  // ANN is never available there, regardless of the (own or default) agent's RAG
  // flag. Folded in as the single gate so the tile tracks the runtime exactly.
  const ownAgent = site.turSNSiteGenAi?.turAIAgent;
  const ownAgentReady = ownAgent?.enabled === 1 && ownAgent?.ragEnabled === true;
  const vectorModeActive =
    site.turSNSiteGenAi?.knowledgeBaseMode !== "VECTORLESS_STRUCTURED";
  const annEnabled =
    vectorModeActive &&
    (ownAgentReady || (!ownAgent && Boolean(features?.defaultAiAgentRagEnabled)));

  const { search, ann, answer } = buildSnSearchLaunchers(
    {
      name: site.name,
      annEnabled,
      answerEnabled: chatEnabled?.enabled === true,
      storageEnabled: features?.storageEnabled ?? false,
      searchTemplate: site.searchTemplate,
      locales: site.turSNSiteLocales ?? [],
    },
    (key) => t(key),
  );

  // The probe's reason code names the one condition to fix. `NONE` alongside
  // `enabled: false` should not happen, but read it as the generic hint rather
  // than rendering a tile whose subtitle claims everything is fine.
  const reason = chatEnabled?.reason && chatEnabled.reason !== "NONE" ? chatEnabled.reason : null;

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconSearch size={16} className="text-emerald-500" />
        {t("sn.launch.title", { defaultValue: "Live search" })}
      </h2>
      <div className="flex flex-wrap gap-3">
        <LauncherTile launcher={search} icon={IconSearch} tone="emerald" />
        {ann && <LauncherTile launcher={ann} icon={IconAtom} tone="indigo" />}
        {answer && (
          <LauncherTile launcher={answer} icon={IconSparkles} tone="blue" />
        )}
        {!answer && !probing && (
          <DisabledTile
            label={t("answer.openAnswerSearch")}
            reason={t(`answer.disabled.${reason ?? "UNKNOWN"}`)}
            icon={IconSparkles}
          />
        )}
      </div>
    </section>
  );
}
