"use client"
import { useCreateStoreInstance, useUpdateStoreInstance } from "@/api/queries/store-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import type { TurStoreVendor } from "@/models/store/store-vendor.model.ts"
import { TurStoreVendorService } from "@/services/store/store-vendor.service"
import { IconAdjustments, IconBuildingStore, IconPlug } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turStoreVendorService = new TurStoreVendorService()
const urlBase = ROUTES.BENTO_STORE_INSTANCE

interface Props {
  value: TurStoreInstance
  isNew: boolean
  readOnly?: boolean
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_STORE_FORM_ID = "bento-store-instance-form"

type StoreProviderOptionsDraft = {
  tenantName: string
  databaseName: string
  initializeSchema: boolean
  embeddingDimension: string
  metricType: string
  indexType: string
  indexParameters: string
  schemaName: string
  pgUsername: string
  pgPassword: string
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
  key: StoreTextFieldKey
  labelKey: string
  descriptionKey: string
  type: "number" | "text" | "password"
  fullWidth?: boolean
}

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
  if (!jsonValue?.trim()) return undefined
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
  if (!parsed) return { draft, unknown }
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
    if (!KNOWN_STORE_OPTION_KEYS.has(key)) unknown[key] = value
  }
  return { draft, unknown }
}

const parseNumber = (value: string) => {
  const normalized = value.trim()
  if (!normalized) return undefined
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

const buildStoreProviderOptionsFromDraft = (vendorId: string | undefined, draft: StoreProviderOptionsDraft) => {
  const options: Record<string, unknown> = {}
  const putText = (key: string, value: string) => {
    const normalized = value.trim()
    if (normalized) options[key] = normalized
  }
  const putNumber = (key: string, value: string) => {
    const parsed = parseNumber(value)
    if (parsed !== undefined) options[key] = parsed
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

const hasStoreVisualOptions = (vendorId?: string) =>
  vendorId === "CHROMA" || vendorId === "MILVUS" || vendorId === "PGVECTOR"

export const BentoStoreInstanceForm: React.FC<Props> = ({ value, isNew, readOnly = false, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurStoreInstance>({ defaultValues: value })
  const [storeProviderOptionsDraft, setStoreProviderOptionsDraft] = useState<StoreProviderOptionsDraft>(emptyStoreProviderOptionsDraft())
  const [preservedOptions, setPreservedOptions] = useState<Record<string, unknown>>({})
  const [vendors, setVendors] = useState<TurStoreVendor[]>([])
  const selectedVendorId = form.watch("turStoreVendor.id")
  const navigate = useNavigate()

  const createMutation = useCreateStoreInstance()
  const updateMutation = useUpdateStoreInstance()

  useEffect(() => {
    turStoreVendorService.query().then(setVendors)
  }, [])

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset({
      ...value,
      collectionName: value.collectionName ?? "",
      credential: "",
      providerOptionsJson: value.providerOptionsJson ?? "",
    }, { keepDirtyValues: true })
    const { draft, unknown } = parseStoreProviderOptions(value.providerOptionsJson)
    setStoreProviderOptionsDraft(draft)
    setPreservedOptions(unknown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  const applyVendorDefaults = (vendorId: string) => {
    const draft = emptyStoreProviderOptionsDraft()
    if (vendorId === "CHROMA") {
      form.setValue("url", "http://localhost:8000", { shouldDirty: true })
      form.setValue("collectionName", "turing", { shouldDirty: true })
      draft.tenantName = "default_tenant"
      draft.databaseName = "default_database"
    }
    if (vendorId === "MILVUS") {
      form.setValue("url", "http://localhost:19530", { shouldDirty: true })
      form.setValue("collectionName", "turing", { shouldDirty: true })
      draft.databaseName = "default"
      draft.embeddingDimension = "1024"
      draft.metricType = "COSINE"
      draft.indexType = "HNSW"
    }
    if (vendorId === "PGVECTOR") {
      form.setValue("url", "jdbc:postgresql://localhost:5432/turing", { shouldDirty: true })
      form.setValue("collectionName", "vector_store", { shouldDirty: true })
      draft.schemaName = "public"
      draft.pgUsername = "postgres"
      draft.embeddingDimension = "1536"
      draft.metricType = "COSINE_DISTANCE"
      draft.indexType = "HNSW"
    }
    setStoreProviderOptionsDraft(draft)
  }

  const setStoreDraftValue = (key: keyof StoreProviderOptionsDraft, fieldValue: string | boolean) => {
    setStoreProviderOptionsDraft((prev) => ({ ...prev, [key]: fieldValue }))
  }

  async function onSubmit(storeInstance: TurStoreInstance) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.store.titleRequired"))
      return
    }
    const visualProviderOptions = buildStoreProviderOptionsFromDraft(selectedVendorId, storeProviderOptionsDraft)
    const mergedProviderOptions = { ...preservedOptions, ...visualProviderOptions }
    const providerOptionsJson = Object.keys(mergedProviderOptions).length > 0
      ? JSON.stringify(mergedProviderOptions, null, 2)
      : undefined

    const payload: TurStoreInstance = {
      ...storeInstance,
      ...staged,
      collectionName: storeInstance.collectionName?.trim() || undefined,
      credential: storeInstance.credential?.trim() || undefined,
      providerOptionsJson,
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("store.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("store.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("store.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("store.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  const vendorLabel = vendors.find((v) => v.id === selectedVendorId)?.title
  const showSaveBar = !readOnly && (isNew || form.formState.isDirty)

  return (
    <Form {...form}>
      <form id={BENTO_STORE_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("store.newInstance") : t("store.title"))}
                disabled={titleMissing}
                loading={isSubmitting}
                badges={vendorLabel && <BentoSaveBar.Badge>{vendorLabel}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* Vendor Section */}
        <BentoFormSection
          icon={IconBuildingStore}
          tone="violet"
          title={t("forms.store.vendorSection")}
          description={t("forms.store.vendorDesc")}
        >
          <FormField
            control={form.control}
            name="turStoreVendor.id"
            rules={{ required: t("forms.store.vendorRequired") }}
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.common.vendor")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.store.vendorSelectDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <Select
                      onValueChange={(nextValue) => {
                        field.onChange(nextValue)
                        applyVendorDefaults(nextValue)
                      }}
                      value={field.value}
                      disabled={readOnly}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.common.selectVendor")} />
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
        </BentoFormSection>

        {/* Endpoint Section */}
        <BentoFormSection
          icon={IconPlug}
          tone="blue"
          title={t("forms.common.endpoint")}
          description={t("forms.store.endpointDesc")}
        >
          <FormField
            control={form.control}
            name="url"
            rules={{ required: t("forms.store.endpointRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.common.endpoint")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.store.endpointUrlDesc")}</p>
                <FormControl>
                  <Input placeholder={t("forms.store.endpointPlaceholder")} type="text" className="w-full" readOnly={readOnly} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="collectionName"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.store.collectionName")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.store.collectionNameDesc")}</p>
                <FormControl>
                  <Input placeholder="e.g., turing" type="text" className="w-full" readOnly={readOnly} {...field} value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="credential"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.store.credential")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.store.credentialDesc")}</p>
                <FormControl>
                  <Input placeholder={t("forms.store.credentialPlaceholder")} type="password" autoComplete="new-password" className="w-full" readOnly={readOnly} {...field} value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Advanced / vendor options */}
        {hasStoreVisualOptions(selectedVendorId) && (
          <BentoFormSection
            icon={IconAdjustments}
            tone="amber"
            title={t("forms.common.advancedOptions")}
            description={t("forms.store.advancedDesc")}
          >
            <div className="w-full space-y-4">
              <div>
                <div className="text-sm font-medium">{t("forms.store.vendorOptions")}</div>
                <div className="mt-1 text-sm text-muted-foreground">{t("forms.store.vendorOptionsDesc")}</div>
              </div>
              {(STORE_VENDOR_FIELDS[selectedVendorId ?? ""] ?? []).map((spec) => (
                <div key={spec.key} className="w-full">
                  <FormLabel>{t(spec.labelKey)}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t(spec.descriptionKey)}</p>
                  <Input
                    className="mt-2 w-full"
                    type={spec.type}
                    autoComplete={spec.type === "password" ? "new-password" : undefined}
                    readOnly={readOnly}
                    value={storeProviderOptionsDraft[spec.key]}
                    onChange={(event) => setStoreDraftValue(spec.key, event.target.value)}
                  />
                </div>
              ))}
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.store.initializeSchema")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.store.initializeSchemaDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <GradientSwitch
                    checked={storeProviderOptionsDraft.initializeSchema}
                    disabled={readOnly}
                    onCheckedChange={(checked) => setStoreDraftValue("initializeSchema", checked)}
                  />
                </FormItemTwoColumns.Right>
              </FormItemTwoColumns>
            </div>
          </BentoFormSection>
        )}
      </form>
    </Form>
  )
}
