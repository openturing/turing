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
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import { TurMcpServerService } from "@/services/mcp/mcp-server.service"
import { IconPlug, IconServer2, IconSettings, IconTerminal2, IconToggleLeft } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

const turMcpServerService = new TurMcpServerService();
const urlBase = ROUTES.MCP_INSTANCE

interface Props {
  value: TurMcpServer;
  isNew: boolean;
}

export const McpServerForm: React.FC<Props> = ({ value, isNew }) => {
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
          toast.success(`The ${payload.title} MCP Server was saved`);
          navigate(urlBase);
        } else {
          toast.error(`The ${payload.title} MCP Server was not saved`);
        }
      }
      else {
        const result = await turMcpServerService.update(payload);
        if (result) {
          toast.success(`The ${payload.title} MCP Server was updated`);
        } else {
          toast.error(`The ${payload.title} MCP Server was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  async function onDelete() {
    try {
      if (await turMcpServerService.delete(value)) {
        toast.success(`The ${value.title} MCP Server was deleted`);
        navigate(urlBase);
      }
      else {
        toast.error(`The ${value.title} MCP Server was not deleted`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${value.title} MCP Server was not deleted`);
    }
    setOpen(false);
  }

  return (
    <div className="flex min-h-[60vh] h-full w-full items-center justify-center px-4">
      <Card className="mx-auto max-w-2xl">
        <CardHeader>
          <CardTitle className="text-2xl">{isNew && (<span>New</span>)} MCP Server</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="MCP server" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            MCP server settings.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 max-w-3xl mx-auto py-8">
              {/* General Information Section */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title="General Information" description="Title and description for this MCP server." />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="title"
                      rules={{ required: "A title is required for this MCP server." }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Title</FormLabel>
                          <FormDescription>
                            Provide a unique, descriptive name for this MCP server.
                          </FormDescription>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder="Enter server title"
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
                          <FormLabel>Description</FormLabel>
                          <FormDescription>
                            Summarize the purpose or intended use of this MCP server.
                          </FormDescription>
                          <FormControl>
                            <Textarea
                              placeholder="Enter a brief description"
                              className="resize-none"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Connection Section */}
              <SectionCard variant="cyan">
                <SectionCard.Header icon={IconPlug} title="Connection" description="How to connect to this MCP server." />
                <SectionCard.Content>
                  <div className="w-full flex flex-row justify-between items-center gap-4">
                    <div className="flex flex-col">
                      <FormLabel>Connection Type</FormLabel>
                      <FormDescription>
                        Choose how this MCP server is accessed.
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="connectionType"
                        rules={{ required: "Please select a connection type." }}
                        render={({ field }) => (
                          <FormItem className="mb-0">
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                            >
                              <FormControl>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder="Select connection type..." />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                <SelectItem key="HTTP" value="HTTP">
                                  HTTP
                                </SelectItem>
                                <SelectItem key="COMMAND" value="COMMAND">
                                  Command
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
                      <FormLabel>Type</FormLabel>
                      <FormDescription>
                        Define if this server runs synchronously or asynchronously.
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="type"
                        rules={{ required: "Please select a type." }}
                        render={({ field }) => (
                          <FormItem className="mb-0">
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                            >
                              <FormControl>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder="Select type..." />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                <SelectItem key="SYNC" value="SYNC">
                                  Sync
                                </SelectItem>
                                <SelectItem key="ASYNC" value="ASYNC">
                                  Async
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
                <SectionCard.Header icon={selectedConnectionType === "COMMAND" ? IconTerminal2 : IconServer2} title="Endpoint" description={selectedConnectionType === "COMMAND" ? "Command and arguments to start the server." : "URL to reach the MCP server."} />
                <SectionCard.Content>
                  {selectedConnectionType === "HTTP" && (
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="url"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>URL</FormLabel>
                            <FormDescription>
                              Enter the base URL where this MCP server is accessible. Example: http://localhost:3000/mcp
                            </FormDescription>
                            <FormControl>
                              <Input
                                placeholder="Enter endpoint URL"
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
                              <FormLabel>Command</FormLabel>
                              <FormDescription>
                                The command to execute the MCP server. Example: npx, python, node
                              </FormDescription>
                              <FormControl>
                                <Input
                                  placeholder="e.g., npx"
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
                              <FormLabel>Arguments</FormLabel>
                              <FormDescription>
                                Arguments to pass to the command. Example: -y @modelcontextprotocol/server-filesystem /path/to/dir
                              </FormDescription>
                              <FormControl>
                                <Input
                                  placeholder="e.g., -y @modelcontextprotocol/server-filesystem /tmp"
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
                <SectionCard.Header icon={IconToggleLeft} title="Status" description="Enable or disable this MCP server." />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>Enabled</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Toggle to activate or deactivate this MCP server instance.
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
