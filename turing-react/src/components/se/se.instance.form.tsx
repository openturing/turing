"use client"
import { ROUTES } from "@/app/routes.const"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import type { TurSEInstance } from "@/models/se/se-instance.model.ts"
import type { TurSEVendor } from "@/models/se/se-vendor.model.ts"
import { TurSEInstanceService, TurSEVendorService } from "@/services/se/se.service"
import { useEffect, useState } from "react"
import {
  useForm, useWatch
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "../ui/accordion"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
const turSEInstanceService = new TurSEInstanceService();
const turSEVendorService = new TurSEVendorService();

const VENDOR_DEFAULT_URLS: Record<string, string> = {
  SOLR: "http://localhost:8983/solr",
  LUCENE: "/data/turing/lucene",
  ES: "http://localhost:9200",
};
interface Props {
  value: TurSEInstance;
  isNew: boolean;
}

export const SEInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurSEInstance>({
    defaultValues: value
  });
  const { control } = form;
  const [open, setOpen] = useState(false);
  const [vendors, setVendors] = useState<TurSEVendor[]>([]);
  const navigate = useNavigate()
  const selectedVendorId = useWatch({ control, name: "turSEVendor.id" });

  useEffect(() => {
    form.reset(value);
  }, [value])

  useEffect(() => {
    turSEVendorService.query().then(setVendors);
  }, [])

  useEffect(() => {
    if (!isNew) return;
    const defaultUrl = VENDOR_DEFAULT_URLS[selectedVendorId];
    if (defaultUrl) form.setValue("endpointUrl", defaultUrl);
  }, [selectedVendorId])

  async function onSubmit(seInstance: TurSEInstance) {
    try {
      if (isNew) {
        const result = await turSEInstanceService.create(seInstance);
        if (result) {
          toast.success(`The ${seInstance.title} Search Engine was saved`);
          navigate(ROUTES.SE_INSTANCE);
        } else {
          toast.error(`The ${seInstance.title} Search Engine was not saved`);
        }
      }
      else {
        const result = await turSEInstanceService.update(seInstance);
        if (result) {
          toast.success(`The ${seInstance.title} Search Engine was updated`);
        } else {
          toast.error(`The ${seInstance.title} Search Engine was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  async function onDelete() {
    console.log("delete");
    try {
      if (await turSEInstanceService.delete(value)) {
        toast.success(`The ${value.title} Search Engine was deleted`);
        navigate(ROUTES.SE_INSTANCE);
      }
      else {
        toast.error(`The ${value.title} Search Engine was not deleted`);
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${value.title} Search Engine was not deleted`);
    }
    setOpen(false);
  }
  return (
    <div className="flex min-h-[60vh] h-full w-full items-center justify-center px-4">
      <Card className="mx-auto max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">{isNew && (<span>New</span>)} Search Engine</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="Search Engine" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            Search engine settings.
          </CardDescription>
        </CardHeader >
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8 max-w-3xl mx-auto py-10">
              <Accordion
                type="multiple"
                defaultValue={["general", "connection"]}
                className="w-full space-y-4"
              >
                {/* General Section */}
                <AccordionItem value="general" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">General Information</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <FormField
                      control={control}
                      name="title"
                      rules={{ required: "Title is required." }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Title</FormLabel>
                          <FormDescription>
                            Enter a unique, descriptive name for this search engine instance.
                          </FormDescription>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder="e.g. Enterprise Search"
                              type="text"
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Description</FormLabel>
                          <FormDescription>
                            Briefly describe the purpose or scope of this search engine.
                          </FormDescription>
                          <FormControl>
                            <Textarea
                              placeholder="Add a description"
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

                {/* Connection Section */}
                <AccordionItem value="connection" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Connection Settings</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <FormField
                      control={control}
                      name="turSEVendor.id"
                      rules={{ required: "Vendor is required." }}
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>Vendor</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description>
                              Choose the backend technology powering this search engine.
                            </FormItemTwoColumns.Description>
                          </FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Right>
                            <FormControl>
                              <Select onValueChange={field.onChange} value={field.value}>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder="Select vendor..." />
                                </SelectTrigger>
                                <SelectContent>
                                  {vendors.map((vendor) => (
                                    <SelectItem key={vendor.id} value={vendor.id}>{vendor.title}</SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </FormControl>
                          </FormItemTwoColumns.Right>
                          <FormMessage />
                        </FormItemTwoColumns>
                      )}
                    />
                    <FormField
                      control={control}
                      name="endpointUrl"
                      rules={{ required: "Endpoint URL is required." }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Endpoint URL</FormLabel>
                          <FormDescription>
                            Enter the full URL of the search engine (e.g., <code>http://localhost:8983/solr</code>).
                          </FormDescription>
                          <FormControl>
                            <Input
                              placeholder="e.g. http://localhost:8983/solr"
                              type="url"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </AccordionContent>
                </AccordionItem>
              </Accordion>

              {/* Action Footer */}
              <div className="flex justify-end gap-3 pt-8">
                <GradientButton
                  type="button"
                  variant="outline"
                  onClick={() => navigate(ROUTES.SE_INSTANCE)}
                >
                  Cancel
                </GradientButton>
                <GradientButton type="submit">
                  Save Changes
                </GradientButton>
              </div>
            </form>
          </Form>
        </CardContent>
      </Card >
    </div >
  )
}

