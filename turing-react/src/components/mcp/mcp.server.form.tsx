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
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import { TurMcpServerService } from "@/services/mcp/mcp-server.service"
import { IconPlug, IconServer2, IconSettings, IconTerminal2, IconToggleLeft } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"

const turMcpServerService = new TurMcpServerService();
const urlBase = ROUTES.MCP_INSTANCE

interface Props {
  value: TurMcpServer;
  isNew: boolean;
}

export const McpServerForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurMcpServer>({
    defaultValues: value
  });
  const [open, setOpen] = useState(false);
  const selectedConnectionType = form.watch("connectionType");
  const navigate = useNavigate()

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      url: value.url ?? "",
      command: value.command ?? "",
      args: value.args ?? "",
      type: value.type ?? "SYNC",
      connectionType: value.connectionType ?? "HTTP",
    });
  }, [value])

  async function onSubmit(mcpServer: TurMcpServer) {
    const payload: TurMcpServer = {
      ...mcpServer,
      url: mcpServer.url?.trim() || undefined,
      command: mcpServer.command?.trim() || undefined,
      args: mcpServer.args?.trim() || undefined,
    }

    try {
      if (isNew) {
        const result = await turMcpServerService.create(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("mcp.title") }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("mcp.title") }));
        }
      }
      else {
        const result = await turMcpServerService.update(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("mcp.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("mcp.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function onDelete() {
    try {
      if (await turMcpServerService.delete(value)) {
        toast.success(t("forms.common.deleted", { name: value.title, feature: t("mcp.title") }));
        navigate(urlBase);
      }
      else {
        toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("mcp.title") }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("mcp.title") }));
    }
    setOpen(false);
  }

  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Card className="mx-auto md:max-w-2xl border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl max-md:**:data-[slot=card-header]:px-2 max-md:**:data-[slot=card-content]:px-2 max-md:**:data-[slot=card-content]:pt-0">
        <CardHeader className="hidden md:grid">
          <CardTitle className="text-2xl">{isNew ? t("mcp.newMcpServer") : t("mcp.title")}</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="MCP server" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            {t("forms.mcp.settings")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 max-w-3xl mx-auto py-2 md:py-8">
              {/* General Information Section */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.mcp.generalDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="title"
                      rules={{ required: t("forms.mcp.titleRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.common.title")}</FormLabel>
                          <FormDescription>
                            {t("forms.mcp.titleDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder={t("forms.mcp.titlePlaceholder")}
                              type="text"
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormControl>
                            <SmartDescription
                              value={field.value}
                              onChange={field.onChange}
                              placeholder={t("forms.mcp.descriptionPlaceholder")}
                              maxLength={500}
                              title={form.watch("title")}
                              entityType={t("mcp.title")}
                            >
                              <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                              <SmartDescription.Description>
                                {t("forms.mcp.descriptionDesc")}
                              </SmartDescription.Description>
                            </SmartDescription>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="icon"
                      render={({ field }) => (
                        <FormItem>
                          <FormControl>
                            <IconPicker
                              value={field.value}
                              onChange={(icon) => field.onChange(icon)}
                              onRemove={() => field.onChange(null)}
                              title={form.watch("title")}
                              description={form.watch("description")}
                            >
                              <IconPicker.Label>{t("forms.common.icon")}</IconPicker.Label>
                            </IconPicker>
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Connection Section */}
              <SectionCard variant="cyan">
                <SectionCard.Header icon={IconPlug} title={t("forms.mcp.connection")} description={t("forms.mcp.connectionDesc")} />
                <SectionCard.Content>
                  <div className="w-full flex flex-row justify-between items-center gap-4">
                    <div className="flex flex-col">
                      <FormLabel>{t("forms.mcp.connectionType")}</FormLabel>
                      <FormDescription>
                        {t("forms.mcp.connectionTypeDesc")}
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="connectionType"
                        rules={{ required: t("forms.mcp.connectionTypeRequired") }}
                        render={({ field }) => (
                          <FormItem className="mb-0">
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                            >
                              <FormControl>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder={t("forms.mcp.selectConnectionType")} />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                <SelectItem key="HTTP" value="HTTP">
                                  {t("forms.mcp.http")}
                                </SelectItem>
                                <SelectItem key="COMMAND" value="COMMAND">
                                  {t("forms.mcp.command")}
                                </SelectItem>
                              </SelectContent>
                            </Select>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>
                  <div className="w-full flex flex-row justify-between items-center gap-4">
                    <div className="flex flex-col">
                      <FormLabel>{t("forms.mcp.type")}</FormLabel>
                      <FormDescription>
                        {t("forms.mcp.typeDesc")}
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="type"
                        rules={{ required: t("forms.mcp.typeRequired") }}
                        render={({ field }) => (
                          <FormItem className="mb-0">
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                            >
                              <FormControl>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder={t("forms.mcp.selectType")} />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                <SelectItem key="SYNC" value="SYNC">
                                  {t("forms.mcp.sync")}
                                </SelectItem>
                                <SelectItem key="ASYNC" value="ASYNC">
                                  {t("forms.mcp.async")}
                                </SelectItem>
                              </SelectContent>
                            </Select>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Endpoint Section */}
              <SectionCard variant="violet">
                <SectionCard.Header icon={selectedConnectionType === "COMMAND" ? IconTerminal2 : IconServer2} title={t("forms.mcp.endpointSection")} description={selectedConnectionType === "COMMAND" ? t("forms.mcp.endpointDescCommand") : t("forms.mcp.endpointDescHttp")} />
                <SectionCard.Content>
                  {selectedConnectionType === "HTTP" && (
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="url"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>{t("forms.mcp.url")}</FormLabel>
                            <FormDescription>
                              {t("forms.mcp.urlDesc")}
                            </FormDescription>
                            <FormControl>
                              <Input
                                placeholder={t("forms.mcp.urlPlaceholder")}
                                type="text"
                                {...field}
                                value={field.value ?? ""}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  )}
                  {selectedConnectionType === "COMMAND" && (
                    <>
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="command"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>{t("forms.mcp.commandLabel")}</FormLabel>
                              <FormDescription>
                                {t("forms.mcp.commandDesc")}
                              </FormDescription>
                              <FormControl>
                                <Input
                                  placeholder={t("forms.mcp.commandPlaceholder")}
                                  type="text"
                                  {...field}
                                  value={field.value ?? ""}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                      <div className="w-full">
                        <FormField
                          control={form.control}
                          name="args"
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>{t("forms.mcp.arguments")}</FormLabel>
                              <FormDescription>
                                {t("forms.mcp.argumentsDesc")}
                              </FormDescription>
                              <FormControl>
                                <Input
                                  placeholder={t("forms.mcp.argumentsPlaceholder")}
                                  type="text"
                                  {...field}
                                  value={field.value ?? ""}
                                />
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

              {/* Status Section */}
              <SectionCard variant="slate">
                <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.mcp.statusDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            {t("forms.mcp.enabledDesc")}
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
              <FormActions>
                {!isNew && <FormActions.Delete onClick={() => setOpen(true)} />}
                <FormActions.Cancel onClick={() => navigate(urlBase)} />
                <FormActions.Submit />
              </FormActions>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
