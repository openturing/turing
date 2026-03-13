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
  Textarea
} from "@/components/ui/textarea"
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts"
import { TurTokenInstanceService } from "@/services/token/token.service"
import { IconKey, IconNotes } from "@tabler/icons-react"
import { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
const turTokenInstanceService = new TurTokenInstanceService();
interface Props {
  value: TurTokenInstance;
  isNew: boolean;
}

export const TokenInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurTokenInstance>({
    defaultValues: value
  });
  const navigate = useNavigate()
  useEffect(() => {
    form.reset(value);
  }, [value]);

  async function onSubmit(tokenInstance: TurTokenInstance) {
    try {
      if (isNew) {
        const result = await turTokenInstanceService.create(tokenInstance);
        if (result) {
          toast.success(`The ${tokenInstance.title} API Token was saved`);
          navigate(ROUTES.ADMIN_TOKENS);
        } else {
          toast.error(`The ${tokenInstance.title} API Token was not saved`);
        }
      }
      else {
        const result = await turTokenInstanceService.update(tokenInstance);
        if (result) {
          toast.success(`The ${tokenInstance.title} API Token was updated`);
        } else {
          toast.error(`The ${tokenInstance.title} API Token was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value.token);
      toast.success("Token API copied!");
    } catch (err) {
      toast.error("Failed to copy token API");
      console.error("Failed to copy text: ", err);
    }
  };

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 py-6 px-6"
      >
        {/* General */}
        <SectionCard variant="blue">
          <SectionCard.Header icon={IconNotes} title="General" description="Basic information about the API token." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="title"
              rules={{ required: "Title is required." }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Title</FormLabel>
                  <FormDescription>A short name to identify this token.</FormDescription>
                  <FormControl>
                    <Input
                      {...field}
                      placeholder="e.g. CI/CD Pipeline"
                      type="text"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormDescription>Optional notes about how this token is used.</FormDescription>
                  <FormControl>
                    <Textarea
                      placeholder="e.g. Used by the deployment pipeline to index content."
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

        {/* Token */}
        {!isNew && <SectionCard variant="violet">
          <SectionCard.Header icon={IconKey} title="Token" description="The generated API token value." />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="token"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>API Token</FormLabel>
                  <FormDescription>Use this token in the Authorization header for API requests.</FormDescription>
                  <FormControl>
                    <div className="flex items-center space-x-2">
                      <Input
                        placeholder="API Token"
                        type="text"
                        readOnly
                        className="font-mono text-sm"
                        {...field} />
                      <GradientButton type="button" onClick={handleCopy}>Copy</GradientButton>
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>}

        {/* Footer */}
        <FormActions>
          <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_TOKENS)} />
          <FormActions.Submit />
        </FormActions>
      </form>
    </Form>
  )
}
