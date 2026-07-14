import { BentoFormHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import { Form } from "@/components/ui/form";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { ContentIngestionSection } from "@/app/console/system/sections/content-ingestion.section";
import { EmailSettingsSection } from "@/app/console/system/sections/email-settings.section";
import { GeneralSettingsSection } from "@/app/console/system/sections/general-settings.section";
import { LlmSettingsSection } from "@/app/console/system/sections/llm-settings.section";
import { PiiSlotSection } from "@/app/console/system/sections/pii-slot.section";
import { PythonSettingsSection } from "@/app/console/system/sections/python-settings.section";
import { RagRerankSection } from "@/app/console/system/sections/rag-rerank.section";
import { RagSettingsSection } from "@/app/console/system/sections/rag-settings.section";
import { TranscriptionSection } from "@/app/console/system/sections/transcription.section";
import { UrlSigningSecretSection } from "@/app/console/system/sections/url-signing-secret.section";
import { useGlobalSettingsForm } from "@/app/console/system/use-global-settings-form";
import { IconAdjustments, IconCode, IconSettings, IconSparkles, type Icon as TablerIcon } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

type TabId = "platform" | "genai" | "code";

/**
 * Bento global settings — T566, tabbed in Block AS. In the console,
 * platform-formatting/email and the Generative-AI runtime config lived on two
 * sibling pages; the first bento port folded everything into one long scrolling
 * form. That scroll grew unwieldy (the Generative-AI block alone stacks Python,
 * secrets, PII, LLM, RAG and reranking — and keeps growing), so the sections are
 * now split across a frosted **pill tab-bar** (the bento multi-section pattern).
 *
 * Crucially it stays a **single form**: every section still binds to the one
 * shared {@link useGlobalSettingsForm} (the same TurGlobalSettings record) and
 * the {@link BentoSaveBar} saves the whole record regardless of the active tab.
 * All panels stay mounted (toggled with `hidden`) so switching tabs never drops
 * an in-progress edit from react-hook-form.
 */
export default function BentoGlobalSettingsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const state = useGlobalSettingsForm();
  const { form, isLoading, isSaving, onSubmit } = state;
  const [activeTab, setActiveTab] = useState<TabId>("platform");

  const tabs: {
    id: TabId;
    label: string;
    description: string;
    icon: TablerIcon;
  }[] = [
    {
      id: "platform",
      label: t("globalSettings.tabPlatform", { defaultValue: "Platform" }),
      description: t("globalSettings.tabPlatformDesc", {
        defaultValue: "Formatting, email, security and privacy.",
      }),
      icon: IconAdjustments,
    },
    {
      id: "genai",
      label: t("globalSettings.tabGenAi", { defaultValue: "Generative AI" }),
      description: t("globalSettings.tabGenAiDesc", {
        defaultValue: "Models, retrieval and reranking.",
      }),
      icon: IconSparkles,
    },
    {
      id: "code",
      label: t("globalSettings.tabCodeTools", { defaultValue: "Code & Tools" }),
      description: t("globalSettings.tabCodeToolsDesc", {
        defaultValue: "Python and the Code Interpreter sandbox.",
      }),
      icon: IconCode,
    },
  ];

  const activeDescription = tabs.find((tab) => tab.id === activeTab)?.description;

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5 pb-8">
        <BentoFormHero
          leading={
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
              <IconSettings size={22} />
            </span>
          }
          backTo={ROUTES.BENTO_AREA_MANAGEMENT}
          backLabel={t("home.sections.management.label", { defaultValue: "Management" })}
          title={t("globalSettings.breadcrumb")}
          subtitle={t("globalSettings.description")}
          stickyTitle={t("globalSettings.breadcrumb")}
          onCancel={() => navigate(ROUTES.BENTO_AREA_MANAGEMENT)}
          loading={isSaving}
          saveDisabled={isLoading}
          dirty={form.formState.isDirty}
        />

        {/* Pill tab-bar — a single form behind three frosted section groups. */}
        <nav role="tablist" aria-label={t("globalSettings.breadcrumb")} className="flex flex-wrap gap-2">
          {tabs.map((tab) => {
            const isActive = tab.id === activeTab;
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                id={`global-settings-tab-${tab.id}`}
                aria-selected={isActive}
                aria-controls={`global-settings-panel-${tab.id}`}
                onClick={() => setActiveTab(tab.id)}
                className={`bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                  isActive
                    ? "border-primary/40 bg-primary text-primary-foreground"
                    : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
                }`}
              >
                <tab.icon size={16} />
                {tab.label}
              </button>
            );
          })}
        </nav>
        {activeDescription && (
          <p className="-mt-2 text-sm text-muted-foreground">{activeDescription}</p>
        )}

        <SectionCardChromeProvider chrome="bento">
          <div
            role="tabpanel"
            id="global-settings-panel-platform"
            aria-labelledby="global-settings-tab-platform"
            hidden={activeTab !== "platform"}
            className="flex flex-col gap-5"
          >
            <GeneralSettingsSection state={state} />
            <ContentIngestionSection state={state} />
            <EmailSettingsSection state={state} />
            <UrlSigningSecretSection state={state} />
            <PiiSlotSection state={state} />
          </div>

          <div
            role="tabpanel"
            id="global-settings-panel-genai"
            aria-labelledby="global-settings-tab-genai"
            hidden={activeTab !== "genai"}
            className="flex flex-col gap-5"
          >
            <LlmSettingsSection state={state} />
            <RagSettingsSection state={state} />
            <RagRerankSection state={state} />
            <TranscriptionSection state={state} />
          </div>

          <div
            role="tabpanel"
            id="global-settings-panel-code"
            aria-labelledby="global-settings-tab-code"
            hidden={activeTab !== "code"}
            className="flex flex-col gap-5"
          >
            <PythonSettingsSection state={state} />
          </div>
        </SectionCardChromeProvider>
      </form>
    </Form>
  );
}
