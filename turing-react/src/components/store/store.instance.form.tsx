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
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import type { TurStoreVendor } from "@/models/store/store-vendor.model.ts"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { TurStoreVendorService } from "@/services/store/store-vendor.service"
import { IconAdjustments, IconBuildingStore, IconPlug, IconSettings, IconToggleLeft } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@openviglet/viglet-design-system"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"
const turStoreInstanceService = new TurStoreInstanceService();
const turStoreVendorService = new TurStoreVendorService();
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
  // PGVector-specific
  schemaName: string;
  pgUsername: string;
  pgPassword: string;
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
  indexParameters: "",
  schemaName: "",
  pgUsername: "",
  pgPassword: "",
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
    embeddingDimension: toText(parsed.embeddingDimension ?? parsed.dimensions),
    metricType: toText(parsed.metricType ?? parsed.distanceType),
    indexType: toText(parsed.indexType),
    indexParameters: toText(parsed.indexParameters),
    schemaName: toText(parsed.schemaName),
    pgUsername: toText(parsed.username),
    pgPassword: "",
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

  options.initializeSchema = draft.initializeSchema

  if (vendorId === "CHROMA") {
    putText("baseUrl", draft.baseUrl)
    putText("collectionName", draft.collectionName)
    putText("databaseName", draft.databaseName)
    putText("tenantName", draft.tenantName)
    putText("keyToken", draft.keyToken)
    putText("basicUsername", draft.basicUsername)
    putText("basicPassword", draft.basicPassword)
  }

  if (vendorId === "MILVUS") {
    putText("baseUrl", draft.baseUrl)
    putText("collectionName", draft.collectionName)
    putText("databaseName", draft.databaseName)
    putText("token", draft.token)
    putNumber("embeddingDimension", draft.embeddingDimension)
    putText("metricType", draft.metricType)
    putText("indexType", draft.indexType)
    putText("indexParameters", draft.indexParameters)
  }

  if (vendorId === "PGVECTOR") {
    putText("jdbcUrl", draft.baseUrl)
    putText("tableName", draft.collectionName)
    putText("schemaName", draft.schemaName)
    putText("username", draft.pgUsername)
    putText("password", draft.pgPassword)
    putNumber("dimensions", draft.embeddingDimension)
    putText("distanceType", draft.metricType)
    putText("indexType", draft.indexType)
  }

  return options
}

export const StoreInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurStoreInstance>({
    defaultValues: value
  });
  const [storeProviderOptionsDraft, setStoreProviderOptionsDraft] = useState<StoreProviderOptionsDraft>(
    emptyStoreProviderOptionsDraft()
  )
  const [vendors, setVendors] = useState<TurStoreVendor[]>([]);
  const selectedVendorId = form.watch("turStoreVendor.id");
  const navigate = useNavigate()

  useEffect(() => {
    turStoreVendorService.query().then(setVendors);
  }, []);

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
    if (vendorId === "PGVECTOR") {
      form.setValue("url", "jdbc:postgresql://localhost:5432/turing", { shouldDirty: true });
      form.setValue("collectionName", "vector_store", { shouldDirty: true });
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
    if (vendorId === "PGVECTOR") {
      return '{\n  "jdbcUrl": "jdbc:postgresql://localhost:5432/turing",\n  "username": "postgres",\n  "password": "password",\n  "tableName": "vector_store",\n  "schemaName": "public",\n  "dimensions": 1536,\n  "distanceType": "COSINE_DISTANCE",\n  "indexType": "HNSW",\n  "initializeSchema": true\n}'
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
      toast.error(t("forms.common.invalidJson"));
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
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("store.title") }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("store.title") }));
        }
      }
      else {
        const result = await turStoreInstanceService.update(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("store.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("store.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-6 px-6">
              {/* General Information Section */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.store.generalDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="title"
                      rules={{ required: t("forms.store.titleRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.common.title")}</FormLabel>
                          <FormDescription>
                            {t("forms.store.titleDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder={t("forms.store.titlePlaceholder")}
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
                              placeholder={t("forms.store.descriptionPlaceholder")}
                              maxLength={500}
                              title={form.watch("title")}
                              entityType={t("store.title")}
                            >
                              <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                              <SmartDescription.Description>
                                {t("forms.store.descriptionDesc")}
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

              {/* Vendor Section - Strict Inline */}
              <SectionCard variant="cyan">
                <SectionCard.Header icon={IconBuildingStore} title={t("forms.store.vendorSection")} description={t("forms.store.vendorDesc")} />
                <SectionCard.Content>
                  <div className="w-full flex flex-row justify-between items-center gap-4">
                    <div className="flex flex-col">
                      <FormLabel>{t("forms.common.vendor")}</FormLabel>
                      <FormDescription>
                        {t("forms.store.vendorSelectDesc")}
                      </FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="turStoreVendor.id"
                        rules={{ required: t("forms.store.vendorRequired") }}
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
                                  <SelectValue placeholder={t("forms.common.selectVendor")} />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {vendors.map((vendor) => (
                                  <SelectItem key={vendor.id} value={vendor.id}>
                                    {vendor.title}
                                  </SelectItem>
                                ))}
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
                <SectionCard.Header icon={IconPlug} title={t("forms.common.endpoint")} description={t("forms.store.endpointDesc")} />
                <SectionCard.Content>
                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="url"
                      rules={{ required: t("forms.store.endpointRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.common.endpoint")}</FormLabel>
                          <FormDescription>
                            {t("forms.store.endpointUrlDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              placeholder={t("forms.store.endpointPlaceholder")}
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
                          <FormLabel>{t("forms.store.collectionName")}</FormLabel>
                          <FormDescription>
                            {t("forms.store.collectionNameDesc")}
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
                          <FormLabel>{t("forms.store.credential")}</FormLabel>
                          <FormDescription>
                            {t("forms.store.credentialDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              placeholder={t("forms.store.credentialPlaceholder")}
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
                </SectionCard.Content>
              </SectionCard>

              {/* Advanced Section */}
              <SectionCard variant="amber">
                <SectionCard.Header icon={IconAdjustments} title={t("forms.common.advancedOptions")} description={t("forms.store.advancedDesc")} />
                <SectionCard.Content>
                  <div className="w-full rounded-md border p-4 space-y-4">
                    <div className="text-sm font-medium">{t("forms.store.visualOptions")}</div>
                    <div className="text-muted-foreground text-sm">
                      {t("forms.store.visualOptionsDesc")}
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <Input placeholder="Base URL" value={storeProviderOptionsDraft.baseUrl} onChange={(event) => setStoreDraftValue("baseUrl", event.target.value)} />
                      <Input placeholder="Collection Name" value={storeProviderOptionsDraft.collectionName} onChange={(event) => setStoreDraftValue("collectionName", event.target.value)} />
                      <Input placeholder="Database Name" value={storeProviderOptionsDraft.databaseName} onChange={(event) => setStoreDraftValue("databaseName", event.target.value)} />
                    </div>
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.store.initializeSchema")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.store.initializeSchemaDesc")}
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

                    {selectedVendorId === "PGVECTOR" && (
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Input placeholder="Username" value={storeProviderOptionsDraft.pgUsername} onChange={(event) => setStoreDraftValue("pgUsername", event.target.value)} />
                        <Input placeholder="Password" type="password" autoComplete="new-password" value={storeProviderOptionsDraft.pgPassword} onChange={(event) => setStoreDraftValue("pgPassword", event.target.value)} />
                        <Input placeholder="Schema Name (e.g. public)" value={storeProviderOptionsDraft.schemaName} onChange={(event) => setStoreDraftValue("schemaName", event.target.value)} />
                        <Input placeholder="Dimensions (e.g. 1536)" type="number" value={storeProviderOptionsDraft.embeddingDimension} onChange={(event) => setStoreDraftValue("embeddingDimension", event.target.value)} />
                        <Input placeholder="Distance Type (e.g. COSINE_DISTANCE)" value={storeProviderOptionsDraft.metricType} onChange={(event) => setStoreDraftValue("metricType", event.target.value)} />
                        <Input placeholder="Index Type (e.g. HNSW)" value={storeProviderOptionsDraft.indexType} onChange={(event) => setStoreDraftValue("indexType", event.target.value)} />
                      </div>
                    )}
                  </div>

                  <div className="w-full">
                    <FormField
                      control={form.control}
                      name="providerOptionsJson"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.store.providerOptionsJson")}</FormLabel>
                          <FormDescription>
                            {t("forms.store.providerOptionsJsonDesc")}
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
                </SectionCard.Content>
              </SectionCard>

              {/* Status Section - Strict Inline */}
              <SectionCard variant="slate">
                <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.store.statusDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>
                            {t("forms.store.enabledDesc")}
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
  )
}

