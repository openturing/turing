import { BentoHero, BentoStickySaveBar } from "@/components/bento";
import { Form } from "@/components/ui/form";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { EmailSettingsSection } from "@/app/console/system/sections/email-settings.section";
import { GeneralSettingsSection } from "@/app/console/system/sections/general-settings.section";
import { LlmSettingsSection } from "@/app/console/system/sections/llm-settings.section";
import { PiiSlotSection } from "@/app/console/system/sections/pii-slot.section";
import { PythonSettingsSection } from "@/app/console/system/sections/python-settings.section";
import { RagRerankSection } from "@/app/console/system/sections/rag-rerank.section";
import { RagSettingsSection } from "@/app/console/system/sections/rag-settings.section";
import { UrlSigningSecretSection } from "@/app/console/system/sections/url-signing-secret.section";
import { useGlobalSettingsForm } from "@/app/console/system/use-global-settings-form";
import { IconSettings, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento global settings — T566. In the console, platform-formatting/email and
 * the Generative-AI runtime config live on two sibling pages (Settings +
 * Generative AI). The Bento shell has no separate "genai" surface, so this page
 * folds **both** into a single form: every section binds to the one shared
 * {@link useGlobalSettingsForm} (the same TurGlobalSettings record). It renders
 * in bento chrome — a {@link BentoHero}, a sticky {@link BentoSaveBar} that
 * stays pinned below the shell header while scrolling, and the shared sections
 * rendered as frosted {@link BentoFormSection} cards via the
 * `SectionCardChromeProvider`. A subheading separates the Generative-AI block.
 */
export default function BentoGlobalSettingsPage() {
  const { t } = useTranslation();
  const state = useGlobalSettingsForm();
  const { form, isLoading, isSaving, onSubmit } = state;

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5 pb-8">
        <BentoHero
          leading={
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
              <IconSettings size={22} />
            </span>
          }
          eyebrow={t("home.sections.management.label", { defaultValue: "Management" })}
          title={t("globalSettings.breadcrumb")}
          subtitle={t("globalSettings.description")}
        />

        <BentoStickySaveBar
          title={t("globalSettings.breadcrumb")}
          loading={isSaving}
          disabled={isLoading}
          dirty={form.formState.isDirty}
        />

        <SectionCardChromeProvider chrome="bento">
          <GeneralSettingsSection state={state} />
          <EmailSettingsSection state={state} />

          {/* Generative-AI block — merged from the console "Generative AI" page so
              all AI runtime/retrieval config is reachable in the Bento shell. */}
          <div className="flex items-center gap-3 pt-2">
            <span className="grid h-9 w-9 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
              <IconSparkles size={18} />
            </span>
            <div>
              <h2 className="text-lg font-semibold tracking-tight">{t("globalSettings.genAiTitle")}</h2>
              <p className="text-sm text-muted-foreground">{t("globalSettings.genAiDescription")}</p>
            </div>
          </div>

          <PythonSettingsSection state={state} />
          <UrlSigningSecretSection state={state} />
          <PiiSlotSection state={state} />
          <LlmSettingsSection state={state} />
          <RagSettingsSection state={state} />
          <RagRerankSection state={state} />
        </SectionCardChromeProvider>
      </form>
    </Form>
  );
}
