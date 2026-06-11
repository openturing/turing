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
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { StickyPageHeader } from "../sticky-page-header"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

interface Props {
  value: TurSNSite;
  isNew: boolean;
}

export const SNSiteBehaviorForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurSNSite>({
    defaultValues: value
  });
  const urlBase = ROUTES.SN_INSTANCE;
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

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 px-4 lg:px-6 pb-8"
      >
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconScale}
            feature={t("sn.behavior.title")}
            description={t("sn.behavior.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>

        {/* General */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconSettings} title={t("forms.snBehavior.general")} description={t("forms.snBehavior.generalDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* API Access — T233 / §VII.6.h: public vs API-key for the visitor-facing API */}
        <SectionCard variant="slate">
          <SectionCard.Header icon={IconLock} title={t("forms.snBehavior.apiAccess")} description={t("forms.snBehavior.apiAccessDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Wildcard */}
        <SectionCard variant="violet">
          <SectionCard.Header icon={IconCodeAsterisk} title={t("forms.snBehavior.wildcard")} description={t("forms.snBehavior.wildcardDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Facets */}
        <SectionCard variant="emerald">
          <SectionCard.Header icon={IconLayoutListFilled} title={t("forms.snBehavior.facets")} description={t("forms.snBehavior.facetsDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Highlighting */}
        <SectionCard variant="amber">
          <SectionCard.Header icon={IconHighlight} title={t("forms.snBehavior.highlighting")} description={t("forms.snBehavior.highlightingDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Spelling Suggestions */}
        <SectionCard variant="rose">
          <SectionCard.Header icon={IconProgressHelp} title={t("forms.snBehavior.spellingSuggestions")} description={t("forms.snBehavior.spellingSuggestionsDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* More Like This */}
        <SectionCard variant="cyan">
          <SectionCard.Header icon={IconCopy} title={t("forms.snBehavior.mlt")} description={t("forms.snBehavior.mltDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Spotlight */}
        <SectionCard variant="orange">
          <SectionCard.Header icon={IconSpeakerphone} title={t("forms.snBehavior.spotlightSection")} description={t("forms.snBehavior.spotlightDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

        {/* Default Fields */}
        <SectionCard variant="slate" defaultOpen={false}>
          <SectionCard.Header icon={IconListDetails} title={t("forms.snBehavior.defaultFields")} description={t("forms.snBehavior.defaultFieldsDesc")} />
          <SectionCard.Content>
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
          </SectionCard.Content>
        </SectionCard>

      </form>
    </Form>
  )
}
