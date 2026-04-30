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
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import type { TurStoreVendor } from "@/models/store/store-vendor.model.ts"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { TurStoreVendorService } from "@/services/store/store-vendor.service"
import { IconAdjustments, IconBuildingStore, IconDeviceFloppy, IconPlug, IconSettings, IconToggleLeft, IconX } from "@tabler/icons-react"
import { GradientButton } from "../ui/gradient-button"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "../sticky-page-header"
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
  tenantName: string;
  databaseName: string;
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
  tenantName: "",
  databaseName: "",
  initializeSchema: true,
  embeddingDimension: "",
  metricType: "",
  indexType: "",
  indexParameters: "",
  schemaName: "",
  pgUsername: "",
  pgPassword: "",
})

// Keys the visual draft owns. Everything else in an existing providerOptionsJson is
// preserved as-is on save so legacy/custom options aren't lost.
const KNOWN_STORE_OPTION_KEYS = new Set<string>([
  "tenantName", "databaseName", "initializeSchema",
  "embeddingDimension", "dimensions",
  "metricType", "distanceType",
  "indexType", "indexParameters",
  "schemaName", "username", "password",
])

type StoreTextFieldKey = Exclude<keyof StoreProviderOptionsDraft, "initializeSchema">

type StoreVendorFieldSpec = {
  key: StoreTextFieldKey;
  labelKey: string;
  descriptionKey: string;
  type: "number" | "text" | "password";
  fullWidth?: boolean;
}

// PGVector does not use databaseName (the DB is encoded in the JDBC URL), so the
// field only appears for Chroma and Milvus, which scope collections by database.
const STORE_VENDOR_FIELDS: Record<string, StoreVendorFieldSpec[]> = {
  CHROMA: [
    { key: "tenantName", labelKey: "forms.store.tenantName", descriptionKey: "forms.store.tenantNameDesc", type: "text" },
    { key: "databaseName", labelKey: "forms.store.databaseName", descriptionKey: "forms.store.databaseNameDesc", type: "text" },
  ],
  MILVUS: [
    { key: "databaseName", labelKey: "forms.store.databaseName", descriptionKey: "forms.store.databaseNameDesc", type: "text" },
    { key: "embeddingDimension", labelKey: "forms.store.embeddingDimension", descriptionKey: "forms.store.embeddingDimensionDesc", type: "number" },
    { key: "metricType", labelKey: "forms.store.metricType", descriptionKey: "forms.store.metricTypeDesc", type: "text" },
    { key: "indexType", labelKey: "forms.store.indexType", descriptionKey: "forms.store.indexTypeDesc", type: "text" },
    { key: "indexParameters", labelKey: "forms.store.indexParameters", descriptionKey: "forms.store.indexParametersDesc", type: "text", fullWidth: true },
  ],
  PGVECTOR: [
    { key: "pgUsername", labelKey: "forms.store.pgUsername", descriptionKey: "forms.store.pgUsernameDesc", type: "text" },
    { key: "pgPassword", labelKey: "forms.store.pgPassword", descriptionKey: "forms.store.pgPasswordDesc", type: "password" },
    { key: "schemaName", labelKey: "forms.store.schemaName", descriptionKey: "forms.store.schemaNameDesc", type: "text" },
    { key: "embeddingDimension", labelKey: "forms.store.embeddingDimension", descriptionKey: "forms.store.embeddingDimensionDesc", type: "number" },
    { key: "metricType", labelKey: "forms.store.metricType", descriptionKey: "forms.store.metricTypeDesc", type: "text" },
    { key: "indexType", labelKey: "forms.store.indexType", descriptionKey: "forms.store.indexTypeDesc", type: "text" },
  ],
}

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

const parseStoreProviderOptions = (jsonValue?: string): { draft: StoreProviderOptionsDraft; unknown: Record<string, unknown> } => {
  const parsed = parseJsonObject(jsonValue)
  const draft = emptyStoreProviderOptionsDraft()
  const unknown: Record<string, unknown> = {}
  if (!parsed) {
    return { draft, unknown }
  }
  draft.tenantName = toText(parsed.tenantName)
  draft.databaseName = toText(parsed.databaseName)
  draft.initializeSchema = typeof parsed.initializeSchema === "boolean" ? parsed.initializeSchema : true
  draft.embeddingDimension = toText(parsed.embeddingDimension ?? parsed.dimensions)
  draft.metricType = toText(parsed.metricType ?? parsed.distanceType)
  draft.indexType = toText(parsed.indexType)
  draft.indexParameters = toText(parsed.indexParameters)
  draft.schemaName = toText(parsed.schemaName)
  draft.pgUsername = toText(parsed.username)
  draft.pgPassword = ""
  for (const [key, value] of Object.entries(parsed)) {
    if (!KNOWN_STORE_OPTION_KEYS.has(key)) {
      unknown[key] = value
    }
  }
  return { draft, unknown }
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
    putText("databaseName", draft.databaseName)
    putText("tenantName", draft.tenantName)
  }

  if (vendorId === "MILVUS") {
    putText("databaseName", draft.databaseName)
    putNumber("embeddingDimension", draft.embeddingDimension)
    putText("metricType", draft.metricType)
    putText("indexType", draft.indexType)
    putText("indexParameters", draft.indexParameters)
  }

  if (vendorId === "PGVECTOR") {
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
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
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
    const { draft, unknown } = parseStoreProviderOptions(value.providerOptionsJson)
    setStoreProviderOptionsDraft(draft)
    setPreservedOptions(unknown)
  }, [value])
  const applyVendorDefaults = (vendorId: string) => {
    const draft = emptyStoreProviderOptionsDraft()
    if (vendorId === "CHROMA") {
      form.setValue("url", "http://localhost:8000", { shouldDirty: true });
      form.setValue("collectionName", "turing", { shouldDirty: true });
      draft.tenantName = "default_tenant"
      draft.databaseName = "default_database"
    }
    if (vendorId === "MILVUS") {
      form.setValue("url", "http://localhost:19530", { shouldDirty: true });
      form.setValue("collectionName", "turing", { shouldDirty: true });
      draft.databaseName = "default"
      draft.embeddingDimension = "1024"
      draft.metricType = "COSINE"
      draft.indexType = "HNSW"
    }
    if (vendorId === "PGVECTOR") {
      form.setValue("url", "jdbc:postgresql://localhost:5432/turing", { shouldDirty: true });
      form.setValue("collectionName", "vector_store", { shouldDirty: true });
      draft.schemaName = "public"
      draft.pgUsername = "postgres"
      draft.embeddingDimension = "1536"
      draft.metricType = "COSINE_DISTANCE"
      draft.indexType = "HNSW"
    }
    setStoreProviderOptionsDraft(draft)
  }

  const setStoreDraftValue = (key: keyof StoreProviderOptionsDraft, fieldValue: string | boolean) => {
    setStoreProviderOptionsDraft((prev) => ({
      ...prev,
      [key]: fieldValue
    }))
  }

  const hasStoreVisualOptions = (vendorId?: string) =>
    vendorId === "CHROMA" || vendorId === "MILVUS" || vendorId === "PGVECTOR"

  async function onSubmit(storeInstance: TurStoreInstance) {
    const visualProviderOptions = buildStoreProviderOptionsFromDraft(selectedVendorId, storeProviderOptionsDraft)
    // Merge with any unknown keys from the original JSON so legacy/custom values are preserved.
    const mergedProviderOptions = { ...preservedOptions, ...visualProviderOptions }
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
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("store.title")}
            description={t("store.detail.description")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
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
                              enableMetaPrompt
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
                  {hasStoreVisualOptions(selectedVendorId) && (
                    <div className="w-full rounded-md border p-4 space-y-4">
                      <div>
                        <div className="text-sm font-medium">{t("forms.store.vendorOptions")}</div>
                        <div className="text-muted-foreground text-sm mt-1">
                          {t("forms.store.vendorOptionsDesc")}
                        </div>
                      </div>
                      {(STORE_VENDOR_FIELDS[selectedVendorId ?? ""] ?? []).map((spec) => (
                        <div key={spec.key} className="w-full">
                          <FormLabel>{t(spec.labelKey)}</FormLabel>
                          <FormDescription>{t(spec.descriptionKey)}</FormDescription>
                          <Input
                            className="w-full mt-2"
                            type={spec.type}
                            autoComplete={spec.type === "password" ? "new-password" : undefined}
                            value={storeProviderOptionsDraft[spec.key]}
                            onChange={(event) => setStoreDraftValue(spec.key, event.target.value)}
                          />
                        </div>
                      ))}
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
                    </div>
                  )}
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
      </form>
    </Form>
  )
}

