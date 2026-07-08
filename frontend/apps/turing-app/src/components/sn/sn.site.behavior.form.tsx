import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import {
  Input
} from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { useCreateSnSite, useUpdateSnSite } from "@/api/queries/sn-site.queries"
import type { TurSNSite } from "@/models/sn/sn-site.model.ts"
import {
  IconCodeAsterisk,
  IconCopy,
  IconDeviceFloppy,
  IconHighlight,
  IconLayoutListFilled,
  IconListDetails,
  IconLock,
  IconProgressHelp,
  IconScale,
  IconSettings,
  IconSpeakerphone,
  IconX,
} from "@tabler/icons-react"
import { GradientButton } from "../ui/gradient-button"
import { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { StickyPageHeader } from "../sticky-page-header"
import { GradientSwitch } from "../ui/gradient-switch"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import { SNFormSection, type SNFormChrome } from "@/components/sn/sn-form-section"

interface Props {
  value: TurSNSite;
  isNew: boolean;
  /** SN instance base route for save/cancel navigation. Defaults to the
   *  console; the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
  baseRoute?: string;
  /** Which shell chrome to render. `console` (default) uses the sticky page
   *  header + collapsible SectionCards; `bento` uses the BentoHero + frosted
   *  BentoFormSection tiles so the page matches the /bento standard. */
  chrome?: SNFormChrome;
}

export const SNSiteBehaviorForm: React.FC<Props> = ({ value, isNew, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
  const { t } = useTranslation();
  const form = useForm<TurSNSite>({
    defaultValues: value
  });
  const isBento = chrome === "bento";
  const urlBase = baseRoute;
  // In bento the section is reached from the SN instance detail, so cancel /
  // back returns there; in console it falls back to the SN instance list.
  const backRoute = isBento && value.id ? `${baseRoute}/${value.id}` : urlBase;
  const navigate = useNavigate()
  const createMutation = useCreateSnSite();
  const updateMutation = useUpdateSnSite();
  useEffect(() => {
    form.reset(value);
  }, [value]);

  function onSubmit(snSite: TurSNSite) {
    try {
      if (isNew) {
        createMutation.mutate(snSite);
        toast.success(t("sn.siteSaved", { name: snSite.name }));
        navigate(urlBase);
      }
      else {
        updateMutation.mutate(snSite);
        toast.success(t("sn.siteUpdated", { name: snSite.name }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("sn.formSubmitFailed"));
    }
  }

  const actions = (
    <>
      <GradientButton type="submit" size="sm">
        <IconDeviceFloppy className="size-4" />
        {t("forms.formActions.saveChanges")}
      </GradientButton>
      <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(backRoute)}>
        <IconX className="size-4" />
        {t("forms.formActions.cancel")}
      </GradientButton>
    </>
  );

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className={isBento ? "flex flex-col gap-5" : "space-y-4 px-4 lg:px-6 pb-8"}
      >
        {isBento ? (
          <>
          <BentoHero
            eyebrow={
              <Link to={backRoute} className="transition-colors hover:text-foreground">
                {value.name ?? t("sn.title")}
              </Link>
            }
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-600 to-teal-600 text-white shadow-md">
                <IconScale size={24} />
              </span>
            }
            title={t("sn.behavior.title")}
            subtitle={t("sn.behavior.description")}
            trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
          />
          <BentoScrollSaveBar onCancel={() => navigate(backRoute)} />
          </>
        ) : (
          <StickyPageHeader>
            <StickyPageHeader.Title
              icon={IconScale}
              feature={t("sn.behavior.title")}
              description={t("sn.behavior.description")}
            />
            <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
          </StickyPageHeader>
        )}

        {/* General */}
        <SNFormSection chrome={chrome} icon={IconSettings} tone="blue" title={t("forms.snBehavior.general")} description={t("forms.snBehavior.generalDesc")}>
          <FormField
            control={form.control}
            name="rowsPerPage"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("forms.snBehavior.resultsPerPage")}</FormLabel>
                <FormDescription>
                  {t("forms.snBehavior.resultsPerPageDesc")}
                </FormDescription>
                <FormControl>
                  <Input
                    className="max-w-xs"
                    {...field}
                    placeholder="e.g. 10"
                    type="number"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="exactMatch"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.exactMatch")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.exactMatchDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
        </SNFormSection>

        {/* API Access — T233 / §VII.6.h: public vs API-key for the visitor-facing API */}
        <SNFormSection chrome={chrome} icon={IconLock} tone="slate" title={t("forms.snBehavior.apiAccess")} description={t("forms.snBehavior.apiAccessDesc")}>
          <FormField
            control={form.control}
            name="apiAuthMode"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("forms.snBehavior.apiAuthMode")}</FormLabel>
                <FormDescription>
                  {field.value === "API_KEY"
                    ? t("forms.snBehavior.apiAuthModeApiKeyDesc")
                    : t("forms.snBehavior.apiAuthModePublicDesc")}
                </FormDescription>
                <FormControl>
                  <Select onValueChange={field.onChange} value={field.value ?? "PUBLIC"}>
                    <SelectTrigger className="max-w-xs w-full">
                      <SelectValue placeholder={t("forms.snBehavior.select")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PUBLIC">{t("forms.snBehavior.apiAuthModePublic")}</SelectItem>
                      <SelectItem value="API_KEY">{t("forms.snBehavior.apiAuthModeApiKey")}</SelectItem>
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </SNFormSection>

        {/* Wildcard */}
        <SNFormSection chrome={chrome} icon={IconCodeAsterisk} tone="violet" title={t("forms.snBehavior.wildcard")} description={t("forms.snBehavior.wildcardDesc")}>
          <FormField
            control={form.control}
            name="wildcardNoResults"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.addWildcardNoResults")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.addWildcardNoResultsDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="wildcardAlways"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.alwaysAddWildcard")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.alwaysAddWildcardDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
        </SNFormSection>

        {/* Facets */}
        <SNFormSection chrome={chrome} icon={IconLayoutListFilled} tone="emerald" title={t("forms.snBehavior.facets")} description={t("forms.snBehavior.facetsDesc")}>
          <FormField
            control={form.control}
            name="facet"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.enableFacets")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.enableFacetsDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="itemsPerFacet"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("forms.snBehavior.itemsPerFacet")}</FormLabel>
                <FormDescription>
                  {t("forms.snBehavior.itemsPerFacetDesc")}
                </FormDescription>
                <FormControl>
                  <Input
                    className="max-w-xs"
                    {...field}
                    placeholder="e.g. 5"
                    type="number"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <FormField
              control={form.control}
              name="facetSort"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.sorting")}</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.snBehavior.select")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="COUNT">{t("forms.snBehavior.byCount")}</SelectItem>
                        <SelectItem value="ALPHABETICAL">{t("forms.snBehavior.alphabetical")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="facetType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.facetOperator")}</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.snBehavior.select")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="AND">{t("forms.snBehavior.and")}</SelectItem>
                        <SelectItem value="OR">{t("forms.snBehavior.or")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="facetItemType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.itemOperator")}</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.snBehavior.select")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="AND">{t("forms.snBehavior.and")}</SelectItem>
                        <SelectItem value="OR">{t("forms.snBehavior.or")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </SNFormSection>

        {/* Highlighting */}
        <SNFormSection chrome={chrome} icon={IconHighlight} tone="amber" title={t("forms.snBehavior.highlighting")} description={t("forms.snBehavior.highlightingDesc")}>
          <FormField
            control={form.control}
            name="hl"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.enableHighlighting")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.enableHighlightingDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <FormField
              control={form.control}
              name="hlPre"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.startTag")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.startTagDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. <mark>" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="hlPost"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.endTag")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.endTagDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. </mark>" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </SNFormSection>

        {/* Spelling Suggestions */}
        <SNFormSection chrome={chrome} icon={IconProgressHelp} tone="rose" title={t("forms.snBehavior.spellingSuggestions")} description={t("forms.snBehavior.spellingSuggestionsDesc")}>
          <FormField
            control={form.control}
            name="spellCheck"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.enableSuggestions")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.enableSuggestionsDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="spellCheckFixes"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.alwaysShowCorrected")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.alwaysShowCorrectedDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
        </SNFormSection>

        {/* More Like This */}
        <SNFormSection chrome={chrome} icon={IconCopy} tone="cyan" title={t("forms.snBehavior.mlt")} description={t("forms.snBehavior.mltDesc")}>
          <FormField
            control={form.control}
            name="mlt"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.enableSimilar")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.enableSimilarDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
        </SNFormSection>

        {/* Spotlight */}
        <SNFormSection chrome={chrome} icon={IconSpeakerphone} tone="orange" title={t("forms.snBehavior.spotlightSection")} description={t("forms.snBehavior.spotlightDesc")}>
          <FormField
            control={form.control}
            name="spotlightWithResults"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.snBehavior.showSpotlightWithResults")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    {t("forms.snBehavior.showSpotlightWithResultsDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => {
                        field.onChange(checked ? 1 : 0);
                      }}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            )}
          />
        </SNFormSection>

        {/* Default Fields */}
        <SNFormSection chrome={chrome} icon={IconListDetails} tone="slate" defaultOpen={false} title={t("forms.snBehavior.defaultFields")} description={t("forms.snBehavior.defaultFieldsDesc")}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">
            <FormField
              control={form.control}
              name="exactMatchField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.exactMatchField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.exactMatchFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. title" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.defaultField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.defaultFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. content" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultTitleField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.titleField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.titleFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. title" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultTextField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.textField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.textFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. body" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultDescriptionField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.descriptionField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.descriptionFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. description" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultDateField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.dateField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.dateFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. date" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultImageField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.imageField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.imageFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. image" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="defaultURLField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("forms.snBehavior.urlField")}</FormLabel>
                  <FormDescription>
                    {t("forms.snBehavior.urlFieldDesc")}
                  </FormDescription>
                  <FormControl>
                    <Input className="font-mono text-sm" {...field} placeholder="e.g. url" type="text" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </SNFormSection>

      </form>
    </Form>
  )
}
