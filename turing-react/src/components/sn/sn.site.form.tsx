"use client"
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
import {
  Textarea
} from "@/components/ui/textarea"
import type { TurSEInstance } from "@/models/se/se-instance.model"
import type { TurSNSite } from "@/models/sn/sn-site.model.ts"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "../ui/accordion"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
import { Skeleton } from "../ui/skeleton"
const turSNSiteService = new TurSNSiteService();
const turSEInstanceService = new TurSEInstanceService();
interface Props {
  value: TurSNSite;
  isNew: boolean;
}
export const SNSiteForm: React.FC<Props> = ({ value, isNew }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [seInstances, setSeInstances] = useState<TurSEInstance[]>([]);
  const form = useForm<TurSNSite>({
    defaultValues: value
  });
  const urlBase = "/admin/sn/instance";
  const navigate = useNavigate()

  useEffect(() => {
    turSEInstanceService.query().then(setSeInstances);
    form.reset(value);
    setIsLoading(false);
  }, [value])

  async function onSubmit(snSite: TurSNSite) {
    setIsLoading(true);
    try {
      if (isNew) {
        const result = await turSNSiteService.create(snSite);
        setIsLoading(false)
        if (result) {
          toast.success(`The ${snSite.name} SN Site was saved`);
          navigate(urlBase);
        } else {
          toast.error(`The ${snSite.name} SN Site was not saved`);
        }
      }
      else {
        const result = await turSNSiteService.update(snSite);
        setIsLoading(false)
        if (result) {
          toast.success(`The ${snSite.name} SN Site was updated`);
        } else {
          toast.error(`The ${snSite.name} SN Site was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
      setIsLoading(false);
    }
  }
  return (
    <>
      {
        isLoading ? (
          <div className="flex w-full max-w-xs flex-col gap-7 px-6">
            <div className="flex flex-col gap-3">
              <Skeleton className="h-4 w-20" />
              <Skeleton className="h-8 w-full" />
            </div>
            <div className="flex flex-col gap-3">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-8 w-full" />
            </div>
            <Skeleton className="h-8 w-24" />
          </div>
        ) : (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8 py-8 px-6">
              <Accordion
                type="multiple"
                defaultValue={["section-general", "section-search"]}
                className="w-full space-y-4"
              >
                {/* General Information Section */}
                <AccordionItem value="section-general" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">General Information</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    {/* Name Field */}
                    <FormField
                      control={form.control}
                      name="name"
                      rules={{
                        required: "Site name is required.",
                        pattern: {
                          value: /^[a-zA-Z0-9_-]+$/,
                          message: "Only letters, numbers, underscores, and hyphens are allowed.",
                        },
                      }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Name</FormLabel>
                          <FormDescription>
                            Enter a unique identifier for this semantic navigation site. Use only letters, numbers, underscores, or hyphens. This name is used for API access and will appear in the site list.
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder="Site Name" type="text" />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />

                    {/* Description Field */}
                    <FormField
                      control={form.control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Description</FormLabel>
                          <FormDescription>
                            Provide a concise summary describing the purpose or scope of this semantic navigation site. This helps users and agents understand its content focus.
                          </FormDescription>
                          <FormControl>
                            <Textarea
                              placeholder="Describe this site"
                              className="resize-none"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </AccordionContent>
                </AccordionItem>

                {/* Search Engine Section */}
                <AccordionItem value="section-search" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Search Engine</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    {/* Search Engine Instance (Select) */}
                    <FormField
                      control={form.control}
                      name="turSEInstance.id"
                      rules={{ required: "Search engine instance is required." }}
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>Search Engine Instance</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description>
                              Select the search engine instance powering this semantic navigation site. This determines which backend is used for indexing and searching content.
                            </FormItemTwoColumns.Description>
                          </FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Right>
                            <FormControl>
                              <Select onValueChange={field.onChange} value={field.value}>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder="Choose..." />
                                </SelectTrigger>
                                <SelectContent>
                                  {seInstances.map((seInstance) => (
                                    <SelectItem key={seInstance.id} value={seInstance.id}>
                                      {seInstance.title}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </FormControl>
                          </FormItemTwoColumns.Right>
                          <FormMessage />
                        </FormItemTwoColumns>
                      )}
                    />
                  </AccordionContent>
                </AccordionItem>
              </Accordion>

              {/* Action Footer */}
              <div className="flex justify-end gap-3 pt-4">
                <GradientButton
                  type="button"
                  variant="outline"
                  onClick={() => navigate(urlBase)}
                >
                  Cancel
                </GradientButton>
                <GradientButton type="submit">
                  Save Changes
                </GradientButton>
              </div>
            </form>
          </Form>
        )}
    </>
  )
}
