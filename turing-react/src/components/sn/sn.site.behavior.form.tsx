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
import type { TurSNSite } from "@/models/sn/sn-site.model.ts"
import { TurSNSiteService } from "@/services/sn/sn.service"
import {
  IconCodeAsterisk,
  IconCopy,
  IconHighlight,
  IconLayoutListFilled,
  IconListDetails,
  IconProgressHelp,
  IconSettings,
  IconSpeakerphone,
} from "@tabler/icons-react"
import { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

const turSNSiteService = new TurSNSiteService();
interface Props {
  value: TurSNSite;
  isNew: boolean;
}

export const SNSiteBehaviorForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurSNSite>({
    defaultValues: value
  });
  const urlBase = ROUTES.SN_INSTANCE;
  const navigate = useNavigate()
  useEffect(() => {
    form.reset(value);
  }, [value]);

  function onSubmit(snSite: TurSNSite) {
    try {
      if (isNew) {
        turSNSiteService.create(snSite);
        toast.success(`The ${snSite.name} SN Site was saved`);
        navigate(urlBase);
      }
      else {
        turSNSiteService.update(snSite);
        toast.success(`The ${snSite.name} SN Site was updated`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 py-6 px-6"
      >
        {/* General */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconSettings} title="General" description="Core search behavior and pagination." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="rowsPerPage"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Results per Page</FormLabel>
                <FormDescription>
                  Set the number of search results displayed per page.
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
                  <FormItemTwoColumns.Label>Exact Match</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Enable precise search for quoted terms using the default field.
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

        {/* Wildcard */}
        <SectionCard variant="violet">
          <SectionCard.Header icon={IconCodeAsterisk} title="Wildcard" description="Automatic wildcard behavior for broader matching." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="wildcardNoResults"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Add Wildcard on No Results</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    If no results are found, automatically append a wildcard to the search term.
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
                  <FormItemTwoColumns.Label>Always Add Wildcard</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Always append a wildcard to each search term.
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
          <SectionCard.Header icon={IconLayoutListFilled} title="Facets" description="Filtering options and faceted navigation settings." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="facet"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Enable Facets</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Allow filtering results by categories or attributes.
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
                <FormLabel>Items per Facet</FormLabel>
                <FormDescription>
                  Maximum number of items displayed per facet category.
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
                  <FormLabel>Sorting</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="COUNT">By Count</SelectItem>
                        <SelectItem value="ALPHABETICAL">Alphabetical</SelectItem>
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
                  <FormLabel>Facet Operator</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="AND">AND</SelectItem>
                        <SelectItem value="OR">OR</SelectItem>
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
                  <FormLabel>Item Operator</FormLabel>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="AND">AND</SelectItem>
                        <SelectItem value="OR">OR</SelectItem>
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
          <SectionCard.Header icon={IconHighlight} title="Highlighting" description="How search terms are visually emphasized in results." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="hl"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Enable Highlighting</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Highlight search terms in the results.
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
                  <FormLabel>Start Tag</FormLabel>
                  <FormDescription>
                    Inserted before the highlighted term.
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
                  <FormLabel>End Tag</FormLabel>
                  <FormDescription>
                    Inserted after the highlighted term.
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
          <SectionCard.Header icon={IconProgressHelp} title="Spelling Suggestions" description="Did-you-mean and automatic correction behavior." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="spellCheck"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Enable Suggestions</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Suggest spelling corrections for user queries.
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
                  <FormItemTwoColumns.Label>Always Show Corrected Term</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Automatically display results for the corrected term if a typo is detected.
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
          <SectionCard.Header icon={IconCopy} title="More Like This (MLT)" description="Recommend similar documents based on content." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="mlt"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Enable Similar Recommendations</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Recommend documents similar to the current result.
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
          <SectionCard.Header icon={IconSpeakerphone} title="Spotlight" description="Featured content promotion in search results." />
          <SectionCard.Content>
          <FormField
            control={form.control}
            name="spotlightWithResults"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>Show Spotlight with Results</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>
                    Display featured content alongside search results.
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
          <SectionCard.Header icon={IconListDetails} title="Default Fields" description="Field mappings for search, display, and content extraction." />
          <SectionCard.Content>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">
            <FormField
              control={form.control}
              name="exactMatchField"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Exact Match Field</FormLabel>
                  <FormDescription>
                    Field used for quoted searches.
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
                  <FormLabel>Default Field</FormLabel>
                  <FormDescription>
                    Field used for general searches.
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
                  <FormLabel>Title Field</FormLabel>
                  <FormDescription>
                    Field for document titles.
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
                  <FormLabel>Text Field</FormLabel>
                  <FormDescription>
                    Field for main content.
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
                  <FormLabel>Description Field</FormLabel>
                  <FormDescription>
                    Field for document descriptions.
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
                  <FormLabel>Date Field</FormLabel>
                  <FormDescription>
                    Field for document dates.
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
                  <FormLabel>Image Field</FormLabel>
                  <FormDescription>
                    Field for document images.
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
                  <FormLabel>URL Field</FormLabel>
                  <FormDescription>
                    Field for document URLs.
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

        {/* Footer */}
        <FormActions>
          <FormActions.Cancel onClick={() => navigate(urlBase)} />
          <FormActions.Submit />
        </FormActions>
      </form>
    </Form>
  )
}
