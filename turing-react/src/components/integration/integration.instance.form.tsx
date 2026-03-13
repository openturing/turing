"use client"
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
import {
  Textarea
} from "@/components/ui/textarea"
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts"
import { TurIntegrationInstanceService } from "@/services/integration/integration.service"
import { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { SectionCard } from "../ui/section-card"
import { FormActions } from "../ui/form-actions"
import { IconSettings, IconPlug } from "@tabler/icons-react"

const turIntegrationInstanceService = new TurIntegrationInstanceService();
interface Props {
  value: TurIntegrationInstance;
  isNew: boolean;
}

export const IntegrationInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurIntegrationInstance>({
    defaultValues: value
  });
  const navigate = useNavigate()
  useEffect(() => {
    form.reset(value);
  }, [value]);


  function onSubmit(integrationInstance: TurIntegrationInstance) {
    try {
      if (isNew) {
        turIntegrationInstanceService.create(integrationInstance);
        toast.success(`The ${integrationInstance.title} Integration Instance was saved`);
        navigate(ROUTES.INTEGRATION_INSTANCE);
      }
      else {
        turIntegrationInstanceService.update(integrationInstance);
        toast.success(`The ${integrationInstance.title} Integration Instance was updated`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <div className="px-6 py-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            {/* General Information Section */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title="General Information" description="Name and description for this integration." />
              <SectionCard.Content>
                {/* Title */}
                <FormField
                  control={form.control}
                  name="title"
                  rules={{ required: "Please give your integration a name." }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Integration Name</FormLabel>
                      <FormDescription>
                        Choose a name that helps you and your team quickly recognize this integration.
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="e.g. Marketing AEM Connector"
                          type="text"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {/* Description */}
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Short Description</FormLabel>
                      <FormDescription>
                        Briefly explain what this integration does or why you’re setting it up.
                      </FormDescription>
                      <FormControl>
                        <Textarea
                          placeholder="Describe the integration’s purpose or what it connects."
                          className="resize-none"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Connection Details Section */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconPlug} title="Connection Details" description="Vendor type and endpoint configuration." />
              <SectionCard.Content>
                {/* Vendor (50/50 row) */}
                <FormField
                  control={form.control}
                  name="vendor"
                  rules={{ required: "Please select a vendor." }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Integration Type</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          Pick the platform or technology you want to connect with. This helps us tailor the setup for you.
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Select a type..." />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem key="AEM" value="AEM">Adobe AEM</SelectItem>
                              <SelectItem key="WEB_CRAWLER" value="WEB_CRAWLER">Web Crawler</SelectItem>
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
                {/* Endpoint */}
                <FormField
                  control={form.control}
                  name="endpoint"
                  rules={{ required: "Please enter the endpoint URL." }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Endpoint URL</FormLabel>
                      <FormDescription>
                        Enter the web address where Turing should connect to your system. Make sure it’s accessible from this environment.
                      </FormDescription>
                      <FormControl>
                        <Input
                          placeholder="https://your-system.example.com/api"
                          type="url"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(ROUTES.INTEGRATION_INSTANCE)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form >
    </div>
  )
}

