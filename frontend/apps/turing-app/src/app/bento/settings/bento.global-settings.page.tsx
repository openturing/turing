import { StickyPageHeader } from "@/components/sticky-page-header";
import { Form } from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { EmailSettingsSection } from "@/app/console/system/sections/email-settings.section";
import { GeneralSettingsSection } from "@/app/console/system/sections/general-settings.section";
import { LlmSettingsSection } from "@/app/console/system/sections/llm-settings.section";
import { PiiSlotSection } from "@/app/console/system/sections/pii-slot.section";
import { PythonSettingsSection } from "@/app/console/system/sections/python-settings.section";
import { RagRerankSection } from "@/app/console/system/sections/rag-rerank.section";
import { RagSettingsSection } from "@/app/console/system/sections/rag-settings.section";
import { UrlSigningSecretSection } from "@/app/console/system/sections/url-signing-secret.section";
import { useGlobalSettingsForm } from "@/app/console/system/use-global-settings-form";
import { IconDeviceFloppy, IconSettings, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento global settings — T566. In the console, platform-formatting/email and
 * the Generative-AI runtime config live on two sibling pages (Settings +
 * Generative AI). The Bento shell has no separate "genai" surface, so this page
 * folds **both** into a single form: every section binds to the one shared
 * {@link useGlobalSettingsForm} (the same TurGlobalSettings record), under one
 * sticky header + save. A subheading separates the Generative-AI block.
 */
export default function BentoGlobalSettingsPage() {
  const { t } = useTranslation();
  const state = useGlobalSettingsForm();
  const { form, isLoading, isSaving, onSubmit } = state;

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("globalSettings.breadcrumb")}
            description={t("globalSettings.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm" loading={isSaving} disabled={isLoading}>
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>

        <GeneralSettingsSection state={state} />
        <EmailSettingsSection state={state} />

        {/* Generative-AI block — merged from the console "Generative AI" page so
            all AI runtime/retrieval config is reachable in the Bento shell. */}
        <div className="flex items-center gap-2 pt-4">
          <IconSparkles className="size-5 text-indigo-500" />
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
      </form>
    </Form>
  );
}
