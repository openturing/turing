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
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { FormActions } from "../ui/form-actions"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
import { TooltipProvider } from "../ui/tooltip"
const turTokenInstanceService = new TurTokenInstanceService();
interface Props {
  value: TurTokenInstance;
  isNew: boolean;
}

export const TokenInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurTokenInstance>();
  const { setValue } = form;
  const [open, setOpen] = useState(false);
  const navigate = useNavigate()
  useEffect(() => {
    setValue("id", value.id)
    setValue("title", value.title);
    setValue("description", value.description);
    setValue("token", value.token);
  }, [setValue, value]);


  async function onSubmit(seInstance: TurTokenInstance) {
    try {
      if (isNew) {
        const result = await turTokenInstanceService.create(seInstance);
        if (result) {
          toast.success(`The ${seInstance.title} API Token was saved`);
          navigate(ROUTES.TOKEN_INSTANCE);
        } else {
          toast.error(`The ${seInstance.title} API Token was not saved`);
        }
      }
      else {
        const result = await turTokenInstanceService.update(seInstance);
        if (result) {
          toast.success(`The ${seInstance.title} API Token was updated`);
        } else {
          toast.error(`The ${seInstance.title} API Token was not updated`);
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
      if (await turTokenInstanceService.delete(value)) {
        toast.success(`The ${value.title} API Token was deleted`);
        navigate(ROUTES.TOKEN_INSTANCE);
      }
      else {
        toast.error(`The ${value.title} API Token was not deleted`);
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${value.title} API Token was not deleted`);
    }
    setOpen(false);
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
    <div className="flex min-h-[60vh] h-full w-full items-center justify-center px-4">
      <TooltipProvider>
        <div className="mx-auto w-full max-w-md space-y-4">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-2xl font-bold">{isNew && (<span>New </span>)}API Token</h2>
            {!isNew && <DialogDelete feature="API token" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </div>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
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
                        <FormControl>
                          <Input
                            {...field}
                            placeholder="Title"
                            type="text"
                          />
                        </FormControl>
                        <FormDescription>API Token title will appear on API Token list.</FormDescription>
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
                        <FormControl>
                          <Textarea
                            placeholder="Description"
                            className="resize-none"
                            {...field}
                          />
                        </FormControl>
                        <FormDescription>API Token description will appear on API Token list.</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {!isNew && <SectionCard variant="violet">
                <SectionCard.Header icon={IconKey} title="Token" description="The generated API token value." />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="token"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>API Token</FormLabel>
                        <FormControl>
                          <div className="flex items-center space-x-2">
                            <Input
                              placeholder="API Token"
                              type="text"
                              readOnly
                              {...field} />
                            <GradientButton type="button" onClick={handleCopy}>Copy</GradientButton>
                          </div>
                        </FormControl>
                        <FormDescription>API Token instance host will be connected.</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>}

              <FormActions>
                <FormActions.Cancel onClick={() => navigate(ROUTES.TOKEN_INSTANCE)} />
                <FormActions.Submit />
              </FormActions>
            </form>
          </Form>
        </div>
      </TooltipProvider >
    </div >
  )
}
