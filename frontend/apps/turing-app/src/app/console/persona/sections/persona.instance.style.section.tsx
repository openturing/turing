"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import { SectionCard, useSectionChrome } from "@/components/ui/section-card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { useSnSites } from "@/api/queries/sn-site.queries"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconDatabaseSearch, IconMoodSmile, IconPalette } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

const GROUNDING_SOURCES = ["NONE", "NOTEBOOK", "SN_SITE"] as const

const NONE_VALUE = "__none__"
const TONES = ["FORMAL", "CASUAL", "TECHNICAL", "EXECUTIVE"] as const
const LANGUAGE_STYLES = [
  "NEUTRAL",
  "DIRECT",
  "NARRATIVE",
  "PERSUASIVE",
  "INSTRUCTIONAL",
] as const

/** T717 — the five Big Five (OCEAN) trait field names on the persona form. */
const OCEAN_TRAITS = [
  "openness",
  "conscientiousness",
  "extraversion",
  "agreeableness",
  "neuroticism",
] as const
const TRAIT_DEFAULT = 50

/**
 * "Diretrizes de Estilo" section of the persona editor — tone, verbosity and
 * language style.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceStyleSection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.style"))

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
      <SectionCard variant="emerald">
        <SectionCard.Header
          icon={IconPalette}
          title={t("forms.persona.styleGuidelines")}
          description={t("forms.persona.styleGuidelinesDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="tone"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.tone")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder={t("forms.persona.tonePlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.tonePlaceholder")}
                      </SelectItem>
                      {TONES.map((tone) => (
                        <SelectItem key={tone} value={tone}>
                          {t(`persona.tones.${tone}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="verbosity"
            rules={{
              min: { value: 1, message: t("forms.persona.verbosityRange") },
              max: { value: 5, message: t("forms.persona.verbosityRange") },
            }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.verbosity")}</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    type="number"
                    min={1}
                    max={5}
                    value={field.value ?? 3}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="languageStyle"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.languageStyle")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.languageStylePlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.languageStylePlaceholder")}
                      </SelectItem>
                      {LANGUAGE_STYLES.map((style) => (
                        <SelectItem key={style} value={style}>
                          {t(`persona.languageStyles.${style}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="calibrateModelParams"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>
                    {t("forms.persona.calibrateModelParams")}
                  </FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                    {t("forms.persona.calibrateModelParamsDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={Number(field.value) === 1}
                      onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
        </SectionCard.Content>
      </SectionCard>

      <PersonalitySection />
      <GroundingSection />
    </div>
  )
}

/**
 * T718 / §XLVI.1 — knowledge grounding. Binds the persona to a knowledge source so
 * its answers come from proprietary content (a Semantic Navigation site's indexed
 * corpus, or this persona's own notebook) instead of the model's priors. `NONE`
 * (default) keeps the persona ungrounded. The SN-site option surfaces a site picker
 * populated from the real site catalog.
 */
function GroundingSection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()
  const { data: sites } = useSnSites()

  const source = form.watch("groundingSource") ?? "NONE"

  return (
    <SectionCard variant="emerald">
      <SectionCard.Header
        icon={IconDatabaseSearch}
        title={t("forms.persona.grounding")}
        description={t("forms.persona.groundingDesc")}
      />
      <SectionCard.Content>
        <FormField
          control={form.control}
          name="groundingSource"
          render={({ field }) => (
            <FormItem className="w-full">
              <FormLabel>{t("forms.persona.groundingSource")}</FormLabel>
              <FormControl>
                <Select
                  value={field.value ?? "NONE"}
                  onValueChange={(v) => field.onChange(v)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {GROUNDING_SOURCES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {t(`forms.persona.groundingSources.${s}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormControl>
            </FormItem>
          )}
        />

        {source === "SN_SITE" && (
          <FormField
            control={form.control}
            name="groundingSnSite"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.groundingSnSite")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) =>
                      field.onChange(v === NONE_VALUE ? null : v)
                    }
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.groundingSnSitePlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.groundingSnSitePlaceholder")}
                      </SelectItem>
                      {(sites ?? []).map((site) => (
                        <SelectItem key={site.id} value={site.name}>
                          {site.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
      </SectionCard.Content>
    </SectionCard>
  )
}

/**
 * T717 / §XLVI.1 — opt-in Big Five (OCEAN) personality traits. A master switch
 * decides whether the persona carries a personality: off = all five traits unset
 * (null, byte-for-byte legacy voice); on = five 0–100 sliders that shape both the
 * prompt and — when calibration is enabled — the sampling temperature. Toggling on
 * seeds unset traits to the neutral midpoint; toggling off clears them to null.
 */
function PersonalitySection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()

  const traitValues = form.watch(OCEAN_TRAITS)
  const enabled = traitValues.some((v) => v !== null && v !== undefined)

  const onToggle = (checked: boolean) => {
    OCEAN_TRAITS.forEach((trait, i) => {
      form.setValue(trait, checked ? (traitValues[i] ?? TRAIT_DEFAULT) : null, {
        shouldDirty: true,
      })
    })
  }

  return (
    <SectionCard variant="emerald">
      <SectionCard.Header
        icon={IconMoodSmile}
        title={t("forms.persona.personality")}
        description={t("forms.persona.personalityDesc")}
      />
      <SectionCard.Content>
        <FormItemTwoColumns>
          <FormItemTwoColumns.Left>
            <FormItemTwoColumns.Label>
              {t("forms.persona.personalityEnable")}
            </FormItemTwoColumns.Label>
          </FormItemTwoColumns.Left>
          <FormItemTwoColumns.Right>
            <GradientSwitch checked={enabled} onCheckedChange={onToggle} />
          </FormItemTwoColumns.Right>
        </FormItemTwoColumns>

        {enabled &&
          OCEAN_TRAITS.map((trait) => (
            <FormField
              key={trait}
              control={form.control}
              name={trait}
              render={({ field }) => (
                <FormItem className="w-full">
                  <div className="flex items-center justify-between">
                    <FormLabel>{t(`forms.persona.traits.${trait}`)}</FormLabel>
                    <span className="text-sm text-muted-foreground tabular-nums">
                      {field.value ?? TRAIT_DEFAULT}
                    </span>
                  </div>
                  <FormControl>
                    <Slider
                      min={0}
                      max={100}
                      step={1}
                      value={[Number(field.value ?? TRAIT_DEFAULT)]}
                      onValueChange={(v) => field.onChange(v[0])}
                    />
                  </FormControl>
                  <div className="flex justify-between text-xs text-muted-foreground">
                    <span>{t(`forms.persona.traits.${trait}Low`)}</span>
                    <span>{t(`forms.persona.traits.${trait}High`)}</span>
                  </div>
                </FormItem>
              )}
            />
          ))}
      </SectionCard.Content>
    </SectionCard>
  )
}
