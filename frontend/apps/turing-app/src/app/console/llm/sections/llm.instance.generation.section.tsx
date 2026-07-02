"use client"
import type { LLMFormContext } from "@/components/llm/llm.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { SectionCard } from "@/components/ui/section-card"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconAdjustments, IconSparkles } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Parâmetros de Geração" section — generation parameters plus the advanced
 * options block. Max-tokens is routed per vendor by the parent layout.
 *
 * @since 2026.3.4
 */
export default function LLMInstanceGenerationSection() {
  const { t } = useTranslation();
  const { form, selectedVendorId, maxTokensInput, setMaxTokensInput } =
    useOutletContext<LLMFormContext>();
  useSubPageBreadcrumb(t("llm.sidebar.generation"));

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      {/* Generation Section */}
      <SectionCard variant="emerald">
        <SectionCard.Header icon={IconSparkles} title={t("forms.llm.genParams")} description={t("forms.llm.genParamsDesc")} />
        <SectionCard.Content>
          {/* Temperature — all vendors */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="temperature"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.temperature")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.temperatureDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., 0.8" type="number" step="0.01" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Top P — all vendors */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="topP"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.topP")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.topPDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., 0.9" type="number" step="0.01" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Seed — OLLAMA, OPENAI */}
          {(selectedVendorId === "OLLAMA" || selectedVendorId === "OPENAI") && (
            <div className="w-full">
              <FormField
                control={form.control}
                name="seed"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.llm.seed")}</FormLabel>
                    <div className="text-muted-foreground text-sm font-normal mt-1">
                      {t("forms.llm.seedDesc")}
                    </div>
                    <FormControl>
                      <Input placeholder="e.g., 42" type="number" className="w-full" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          )}
          {/* Top K — OLLAMA, ANTHROPIC, GEMINI */}
          {(selectedVendorId === "OLLAMA" || selectedVendorId === "ANTHROPIC" || selectedVendorId === "GEMINI") && (
            <div className="w-full">
              <FormField
                control={form.control}
                name="topK"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.llm.topK")}</FormLabel>
                    <div className="text-muted-foreground text-sm font-normal mt-1">
                      {t("forms.llm.topKDesc")}
                    </div>
                    <FormControl>
                      <Input placeholder="e.g., 40" type="number" className="w-full" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          )}
          {/* Max Tokens — all vendors. Routed to numPredict for Ollama, providerOptions.maxTokens otherwise. */}
          {selectedVendorId && (
            <div className="w-full">
              <FormLabel>{t("forms.llm.maxTokens")}</FormLabel>
              <div className="text-muted-foreground text-sm font-normal mt-1">
                {t("forms.llm.maxTokensDesc")}
              </div>
              <Input
                placeholder="e.g., 1024"
                type="number"
                className="w-full mt-2"
                value={maxTokensInput}
                onChange={(event) => setMaxTokensInput(event.target.value)}
              />
            </div>
          )}
          {/* Repeat Penalty, Stop — OLLAMA only */}
          {selectedVendorId === "OLLAMA" && (
            <>
              <div className="w-full">
                <FormField
                  control={form.control}
                  name="repeatPenalty"
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>{t("forms.llm.repeatPenalty")}</FormLabel>
                      <div className="text-muted-foreground text-sm font-normal mt-1">
                        {t("forms.llm.repeatPenaltyDesc")}
                      </div>
                      <FormControl>
                        <Input placeholder="e.g., 1.1" type="number" step="0.01" className="w-full" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <div className="w-full">
                <FormField
                  control={form.control}
                  name="stop"
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>{t("forms.llm.stop")}</FormLabel>
                      <div className="text-muted-foreground text-sm font-normal mt-1">
                        {t("forms.llm.stopDesc")}
                      </div>
                      <FormControl>
                        <Input placeholder="e.g., END,STOP" type="text" className="w-full" {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </>
          )}
        </SectionCard.Content>
      </SectionCard>

      {/* Advanced Section */}
      <SectionCard variant="amber">
        <SectionCard.Header icon={IconAdjustments} title={t("forms.common.advancedOptions")} description={t("forms.llm.advancedDesc")} />
        <SectionCard.Content>
          {/* Response Format */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="responseFormat"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.responseFormat")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.responseFormatDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., JSON" type="text" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Supported Capabilities */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="supportedCapabilities"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.capabilities")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.capabilitiesDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., RESPONSE_FORMAT_JSON_SCHEMA" type="text" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Timeout */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="timeout"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.timeout")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.timeoutDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., PT60S" type="text" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Max Retries */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="maxRetries"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.maxRetries")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.maxRetriesDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder="e.g., 3" type="number" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </SectionCard.Content>
      </SectionCard>
    </div>
  )
}
