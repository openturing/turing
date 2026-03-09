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
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { DialogDelete } from "../dialog.delete"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "../ui/accordion"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
import { GradientSwitch } from "../ui/gradient-switch"
const turStoreInstanceService = new TurStoreInstanceService();
const urlBase = ROUTES.STORE_INSTANCE
interface Props {
  value: TurStoreInstance;
  isNew: boolean;
}

type StoreProviderOptionsDraft = {
  baseUrl: string;
  collectionName: string;
  tenantName: string;
  databaseName: string;
  keyToken: string;
  basicUsername: string;
  basicPassword: string;
  token: string;
  initializeSchema: boolean;
  embeddingDimension: string;
  metricType: string;
  indexType: string;
  indexParameters: string;
}

const emptyStoreProviderOptionsDraft = (): StoreProviderOptionsDraft => ({
  baseUrl: "",
  collectionName: "",
  tenantName: "",
  databaseName: "",
  keyToken: "",
  basicUsername: "",
  basicPassword: "",
  token: "",
  initializeSchema: true,
  embeddingDimension: "",
  metricType: "",
  indexType: "",
  indexParameters: ""
})

const toText = (value: unknown) => value == null ? "" : String(value)

const parseJsonObject = (jsonValue?: string) => {
  if (!jsonValue?.trim()) {
    return undefined
  }
  try {
    const parsed = JSON.parse(jsonValue) as unknown
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  } catch {
    // Keep the draft empty for invalid JSON; validation occurs on submit.
  }
  return undefined
}

const parseStoreProviderOptionsDraft = (jsonValue?: string): StoreProviderOptionsDraft => {
  const parsed = parseJsonObject(jsonValue)
  if (!parsed) {
    return emptyStoreProviderOptionsDraft()
  }
  return {
    baseUrl: toText(parsed.baseUrl),
    collectionName: toText(parsed.collectionName),
    tenantName: toText(parsed.tenantName),
    databaseName: toText(parsed.databaseName),
    keyToken: toText(parsed.keyToken),
    basicUsername: toText(parsed.basicUsername),
    basicPassword: toText(parsed.basicPassword),
    token: toText(parsed.token),
    initializeSchema: typeof parsed.initializeSchema === "boolean" ? parsed.initializeSchema : true,
    embeddingDimension: toText(parsed.embeddingDimension),
    metricType: toText(parsed.metricType),
    indexType: toText(parsed.indexType),
    indexParameters: toText(parsed.indexParameters)
  }
}

const parseNumber = (value: string) => {
  const normalized = value.trim()
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

const buildStoreProviderOptionsFromDraft = (vendorId: string | undefined, draft: StoreProviderOptionsDraft) => {
  const options: Record<string, unknown> = {}
  const putText = (key: string, value: string) => {
    const normalized = value.trim()
    if (normalized) {
      options[key] = normalized
    }
  }
  const putNumber = (key: string, value: string) => {
    const parsed = parseNumber(value)
    if (parsed !== undefined) {
      options[key] = parsed
    }
  }

  putText("baseUrl", draft.baseUrl)
  putText("collectionName", draft.collectionName)
  putText("databaseName", draft.databaseName)
  options.initializeSchema = draft.initializeSchema

  if (vendorId === "CHROMA") {
    putText("tenantName", draft.tenantName)
    putText("keyToken", draft.keyToken)
    putText("basicUsername", draft.basicUsername)
    putText("basicPassword", draft.basicPassword)
  }

  if (vendorId === "MILVUS") {
    putText("token", draft.token)
    putNumber("embeddingDimension", draft.embeddingDimension)
    putText("metricType", draft.metricType)
    putText("indexType", draft.indexType)
    putText("indexParameters", draft.indexParameters)
  }

  return options
}

export const StoreInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurStoreInstance>({
    defaultValues: value
  });
  const [open, setOpen] = useState(false);
  const [storeProviderOptionsDraft, setStoreProviderOptionsDraft] = useState<StoreProviderOptionsDraft>(
    emptyStoreProviderOptionsDraft()
  )
  const selectedVendorId = form.watch("turStoreVendor.id");
  const navigate = useNavigate()

  useEffect(() => {
    form.reset({
      ...value,
      collectionName: value.collectionName ?? "",
      credential: "",
      providerOptionsJson: value.providerOptionsJson ?? ""
    });
    setStoreProviderOptionsDraft(parseStoreProviderOptionsDraft(value.providerOptionsJson))
  }, [value])
  const applyVendorDefaults = (vendorId: string) => {
    if (vendorId === "CHROMA") {
      form.setValue("url", "http://localhost:8000", { shouldDirty: true });
      form.setValue("collectionName", "turing", { shouldDirty: true });
    }
    if (vendorId === "MILVUS") {
      form.setValue("url", "http://localhost:19530", { shouldDirty: true });
      form.setValue("collectionName", "turing", { shouldDirty: true });
    }
  }

  const setStoreDraftValue = (key: keyof StoreProviderOptionsDraft, fieldValue: string | boolean) => {
    setStoreProviderOptionsDraft((prev) => ({
      ...prev,
      [key]: fieldValue
    }))
  }

  const getProviderOptionsPlaceholder = (vendorId?: string) => {
    if (vendorId === "MILVUS") {
      return '{\n  "baseUrl": "http://localhost:19530",\n  "token": "username:password",\n  "databaseName": "default",\n  "collectionName": "turing",\n  "initializeSchema": true,\n  "embeddingDimension": 1024,\n  "metricType": "COSINE",\n  "indexType": "HNSW",\n  "indexParameters": "{\\"M\\":16,\\"efConstruction\\":200}"\n}'
    }
    return '{\n  "baseUrl": "http://localhost:8000",\n  "collectionName": "turing",\n  "tenantName": "default_tenant",\n  "databaseName": "default_database",\n  "initializeSchema": true,\n  "keyToken": "token-value"\n}'
  }

  const normalizeJson = (jsonValue?: string) => {
    const normalized = jsonValue?.trim();
    if (!normalized) {
      return undefined;
    }
    try {
      JSON.parse(normalized);
      return normalized;
    } catch {
      toast.error("Provider Options must be valid JSON.");
      return null;
    }
  }

  async function onSubmit(storeInstance: TurStoreInstance) {
    const rawProviderOptionsJson = normalizeJson(storeInstance.providerOptionsJson)
    if (rawProviderOptionsJson === null) {
      return;
    }

    const visualProviderOptions = buildStoreProviderOptionsFromDraft(selectedVendorId, storeProviderOptionsDraft)
    const rawProviderOptions = parseJsonObject(rawProviderOptionsJson)
    const mergedProviderOptions = {
      ...visualProviderOptions,
      ...(rawProviderOptions ?? {})
    }
    const providerOptionsJson = Object.keys(mergedProviderOptions).length > 0
      ? JSON.stringify(mergedProviderOptions, null, 2)
      : undefined

    const payload: TurStoreInstance = {
      ...storeInstance,
      collectionName: storeInstance.collectionName?.trim() || undefined,
      credential: storeInstance.credential?.trim() || undefined,
      providerOptionsJson
    }

    try {
      if (isNew) {
        const result = await turStoreInstanceService.create(payload);
        if (result) {
          toast.success(`The ${payload.title} Embedding Store was saved`);
          navigate(urlBase);
        } else {
          toast.error(`The ${payload.title} Embedding Store was not saved`);
        }
      }
      else {
        const result = await turStoreInstanceService.update(payload);
        if (result) {
          toast.success(`The ${payload.title} Embedding Store was updated`);
        } else {
          toast.error(`The ${payload.title} Embedding Store was not updated`);
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
      if (await turStoreInstanceService.delete(value)) {
        toast.success(`The ${value.title} Embedding Store was deleted`);
        navigate(urlBase);
      }
      else {
        toast.error(`The ${value.title} Embedding Store was not deleted`);
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${value.title} Embedding Store was not deleted`);
    }
    setOpen(false);
  }
  return (
    <div className="flex min-h-[60vh] h-full w-full items-center justify-center px-4">
      <Card className="mx-auto max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">{isNew && (<span>New</span>)} Embedding Store</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="embedding store" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>
            Embedding store settings.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8 max-w-3xl mx-auto py-10">
              <Accordion
                type="multiple"
                defaultValue={["general", "vendor", "endpoint", "advanced", "status"]}
                className="w-full space-y-4"
              >
                {/* General Information Section */}
                <AccordionItem value="general" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">General Information</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="title"
                        rules={{ required: "A title is required for this embedding store." }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Title</FormLabel>
                            <FormDescription>
                              Provide a unique, descriptive name for this embedding store instance.
                            </FormDescription>
                            <FormControl>
                              <Input
                                {...field}
                                placeholder="Enter store title"
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
                              Summarize the purpose or intended use of this embedding store.
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
                  </AccordionContent>
                </AccordionItem>

                {/* Vendor Section - Strict Inline */}
                <AccordionItem value="vendor" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Vendor</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <div className="w-full flex flex-row justify-between items-center gap-4">
                      <div className="flex flex-col">
                        <FormLabel>Vendor</FormLabel>
                        <FormDescription>
                          Choose the backend technology powering this embedding store.
                        </FormDescription>
                      </div>
                      <div className="flex-1 max-w-xs">
                        <FormField
                          control={form.control}
                          name="turStoreVendor.id"
                          rules={{ required: "Please select a vendor." }}
                          render={({ field }) => (
                            <FormItem className="mb-0">
                              <Select
                                onValueChange={(nextValue) => {
                                  field.onChange(nextValue);
                                  applyVendorDefaults(nextValue);
                                }}
                                value={field.value}
                              >
                                <FormControl>
                                  <SelectTrigger className="w-full">
                                    <SelectValue placeholder="Select vendor..." />
                                  </SelectTrigger>
                                </FormControl>
                                <SelectContent>
                                  <SelectItem key="CHROMA" value="CHROMA">
                                    Chroma
                                  </SelectItem>
                                  <SelectItem key="MILVUS" value="MILVUS">
                                    Milvus
                                  </SelectItem>
                                </SelectContent>
                              </Select>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>
                    </div>
                  </AccordionContent>
                </AccordionItem>

                {/* Endpoint Section */}
                <AccordionItem value="endpoint" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Endpoint</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="url"
                        rules={{ required: "Endpoint URL is required." }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Endpoint</FormLabel>
                            <FormDescription>
                              Enter the base URL where this embedding store is accessible. Example: http://localhost:8000
                            </FormDescription>
                            <FormControl>
                              <Input
                                placeholder="Enter endpoint URL"
                                type="text"
                                {...field}
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
                        name="collectionName"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Collection Name</FormLabel>
                            <FormDescription>
                              Optional collection/index name used by the vector store.
                            </FormDescription>
                            <FormControl>
                              <Input
                                placeholder="e.g., turing"
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
                        name="credential"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Credential</FormLabel>
                            <FormDescription>
                              Optional token/credential. Leave blank to keep existing credential when editing.
                            </FormDescription>
                            <FormControl>
                              <Input
                                placeholder="Enter credential"
                                type="password"
                                autoComplete="new-password"
                                {...field}
                                value={field.value ?? ""}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </AccordionContent>
                </AccordionItem>

                {/* Advanced Section */}
                <AccordionItem value="advanced" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Advanced Options</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <div className="w-full rounded-md border p-4 space-y-4">
                      <div className="text-sm font-medium">Visual Provider Options</div>
                      <div className="text-muted-foreground text-sm">
                        Use these fields for common options. Any value in raw JSON below overrides visual values.
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Input placeholder="Base URL" value={storeProviderOptionsDraft.baseUrl} onChange={(event) => setStoreDraftValue("baseUrl", event.target.value)} />
                        <Input placeholder="Collection Name" value={storeProviderOptionsDraft.collectionName} onChange={(event) => setStoreDraftValue("collectionName", event.target.value)} />
                        <Input placeholder="Database Name" value={storeProviderOptionsDraft.databaseName} onChange={(event) => setStoreDraftValue("databaseName", event.target.value)} />
                      </div>
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>Initialize Schema</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            Enable automatic schema initialization for the vector store.
                          </FormItemTwoColumns.Description>
                        </FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Right>
                          <GradientSwitch
                            checked={storeProviderOptionsDraft.initializeSchema}
                            onCheckedChange={(checked) => setStoreDraftValue("initializeSchema", checked)}
                          />
                        </FormItemTwoColumns.Right>
                      </FormItemTwoColumns>

                      {selectedVendorId === "CHROMA" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Tenant Name" value={storeProviderOptionsDraft.tenantName} onChange={(event) => setStoreDraftValue("tenantName", event.target.value)} />
                          <Input placeholder="Key Token" type="password" autoComplete="new-password" value={storeProviderOptionsDraft.keyToken} onChange={(event) => setStoreDraftValue("keyToken", event.target.value)} />
                          <Input placeholder="Basic Username" value={storeProviderOptionsDraft.basicUsername} onChange={(event) => setStoreDraftValue("basicUsername", event.target.value)} />
                          <Input placeholder="Basic Password" type="password" autoComplete="new-password" value={storeProviderOptionsDraft.basicPassword} onChange={(event) => setStoreDraftValue("basicPassword", event.target.value)} />
                        </div>
                      )}

                      {selectedVendorId === "MILVUS" && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <Input placeholder="Token" type="password" autoComplete="new-password" value={storeProviderOptionsDraft.token} onChange={(event) => setStoreDraftValue("token", event.target.value)} />
                          <Input placeholder="Embedding Dimension" type="number" value={storeProviderOptionsDraft.embeddingDimension} onChange={(event) => setStoreDraftValue("embeddingDimension", event.target.value)} />
                          <Input placeholder="Metric Type (e.g. COSINE)" value={storeProviderOptionsDraft.metricType} onChange={(event) => setStoreDraftValue("metricType", event.target.value)} />
                          <Input placeholder="Index Type (e.g. HNSW)" value={storeProviderOptionsDraft.indexType} onChange={(event) => setStoreDraftValue("indexType", event.target.value)} />
                          <Input placeholder="Index Parameters (JSON string)" value={storeProviderOptionsDraft.indexParameters} onChange={(event) => setStoreDraftValue("indexParameters", event.target.value)} className="md:col-span-2" />
                        </div>
                      )}
                    </div>

                    <div className="w-full">
                      <FormField
                        control={form.control}
                        name="providerOptionsJson"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Provider Options (JSON)</FormLabel>
                            <FormDescription>
                              Optional provider-specific overrides. Use valid JSON.
                            </FormDescription>
                            <FormControl>
                              <Textarea
                                placeholder={getProviderOptionsPlaceholder(selectedVendorId)}
                                className="resize-y min-h-40"
                                rows={10}
                                {...field}
                                value={field.value ?? ""}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </AccordionContent>
                </AccordionItem>

                {/* Status Section - Strict Inline */}
                <AccordionItem value="status" className="border rounded-lg px-6">
                  <AccordionTrigger className="hover:no-underline">
                    <div className="flex items-center gap-2">
                      <span className="text-lg font-semibold">Status</span>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="flex flex-col gap-6 pt-4">
                    <FormField
                      control={form.control}
                      name="enabled"
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>Enabled</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description>
                              Toggle to activate or deactivate this embedding store instance.
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
                  </AccordionContent>
                </AccordionItem>
              </Accordion>
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
        </CardContent>
      </Card>
    </div>
  )
}

