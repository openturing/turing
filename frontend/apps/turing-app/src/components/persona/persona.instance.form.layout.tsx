"use client"
import {
  useCreatePersona,
  useDeletePersona,
  useUpdatePersona,
} from "@/api/queries/persona.queries"
import { ROUTES } from "@/app/routes.const"
import { Form } from "@/components/ui/form"
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import type { TurPersona } from "@/models/persona/persona.model.ts"
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import { TurMcpServerService } from "@/services/mcp/mcp-server.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import {
  IconBook2,
  IconNotebook,
  IconPalette,
  IconReportAnalytics,
  IconSettings,
  IconSparkles,
  IconUserCircle,
  IconUsers,
} from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useForm, type UseFormReturn } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { SubPage } from "../sub.page"
import { StickySaveBar } from "../ui/sticky-save-bar"

const turStoreInstanceService = new TurStoreInstanceService()
const turMcpServerService = new TurMcpServerService()
const urlBase = ROUTES.PERSONA_INSTANCE

interface Props {
  value: TurPersona
  isNew: boolean
}

/**
 * Form fields are joined back into the persona payload on submit:
 * - termsToLines / linesToTerms convert between newline-friendly UI text
 *   and the pipe-separated DB format.
 * - fewShotStore / brandContextMcpServer are stored as full entity refs in
 *   form state, but the API only needs the {id} — handled in onSubmit.
 */
export type PersonaFormShape = Omit<
  TurPersona,
  "mandatoryTerms" | "forbiddenTerms"
> & {
  mandatoryTermsText: string
  forbiddenTermsText: string
}

/**
 * Context shared by the persona editor's section sub-pages. A single
 * `useForm` instance lives in this layout and is threaded to each section
 * through the router `<Outlet context={...}>`, so the sticky save bar persists
 * one payload regardless of which section is on screen.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface PersonaFormContext {
  form: UseFormReturn<PersonaFormShape>
  value: TurPersona
  isNew: boolean
  stores: TurStoreInstance[]
  mcpServers: TurMcpServer[]
  onDelete: () => void
  deleteOpen: boolean
  setDeleteOpen: (open: boolean) => void
}

const termsToLines = (raw?: string | null) =>
  (raw ?? "")
    .split("|")
    .map((term) => term.trim())
    .filter((term) => term.length > 0)
    .join("\n")

const linesToTerms = (text: string) =>
  text
    .split(/\r?\n/)
    .map((term) => term.trim())
    .filter((term) => term.length > 0)
    .join("|") || null

/**
 * Sidebar-based editor for a single persona. Mirrors the LLM instance editor:
 * one react-hook-form instance owns the whole entity, section sub-pages read it
 * via `useOutletContext`, and the sticky save bar submits the merged payload no
 * matter which section is on screen.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export const PersonaInstanceFormLayout: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [stores, setStores] = useState<TurStoreInstance[]>([])
  const [mcpServers, setMcpServers] = useState<TurMcpServer[]>([])

  const form = useForm<PersonaFormShape>({
    defaultValues: {
      ...value,
      mandatoryTermsText: termsToLines(value.mandatoryTerms),
      forbiddenTermsText: termsToLines(value.forbiddenTerms),
    },
  })

  const createMutation = useCreatePersona()
  const updateMutation = useUpdatePersona()
  const deleteMutation = useDeletePersona()

  useEffect(() => {
    turStoreInstanceService.query().then(setStores).catch(() => setStores([]))
    turMcpServerService.query().then(setMcpServers).catch(() => setMcpServers([]))
  }, [])

  useEffect(() => {
    form.reset({
      ...value,
      mandatoryTermsText: termsToLines(value.mandatoryTerms),
      forbiddenTermsText: termsToLines(value.forbiddenTerms),
    })
  }, [value, form])

  async function onSubmit(data: PersonaFormShape) {
    const { mandatoryTermsText, forbiddenTermsText, ...rest } = data
    const payload: TurPersona = {
      ...rest,
      mandatoryTerms: linesToTerms(mandatoryTermsText),
      forbiddenTerms: linesToTerms(forbiddenTermsText),
      verbosity: Number(rest.verbosity ?? 3),
      enabled: Number(rest.enabled ?? 1),
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(
            t("forms.common.saved", { name: payload.name, feature: t("persona.title") })
          )
          navigate(urlBase)
        } else {
          toast.error(
            t("forms.common.notSaved", { name: payload.name, feature: t("persona.title") })
          )
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(
            t("forms.common.updated", { name: payload.name, feature: t("persona.title") })
          )
        } else {
          toast.error(
            t("forms.common.notUpdated", { name: payload.name, feature: t("persona.title") })
          )
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(value)) {
        toast.success(
          t("forms.common.deleted", { name: value.name, feature: t("persona.title") })
        )
        navigate(urlBase)
      } else {
        toast.error(
          t("forms.common.notDeleted", { name: value.name, feature: t("persona.title") })
        )
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(
        t("forms.common.notDeleted", { name: value.name, feature: t("persona.title") })
      )
    }
    setDeleteOpen(false)
  }

  const watchedName = form.watch("name")
  const watchedKind = form.watch("personaKind") ?? "SPEAKER"
  const showAudience = watchedKind === "AUDIENCE" || watchedKind === "BOTH"
  const instanceUrlBase = `${urlBase}/${isNew ? "new" : value.id}`

  const navData = useMemo(() => {
    const navMain: {
      title: string
      url: string
      icon: React.ElementType
      showOnNew: boolean
    }[] = [
      {
        title: t("persona.sidebar.general"),
        url: "/general",
        icon: IconSettings,
        showOnNew: true,
      },
      {
        title: t("persona.sidebar.systemInstruction"),
        url: "/system-instruction",
        icon: IconSparkles,
        showOnNew: true,
      },
      {
        title: t("persona.sidebar.style"),
        url: "/style",
        icon: IconPalette,
        showOnNew: true,
      },
    ]
    if (showAudience) {
      navMain.push({
        title: t("persona.sidebar.audience"),
        url: "/audience",
        icon: IconUsers,
        showOnNew: true,
      })
      // Notebook + content-fit report need a saved persona (FK).
      if (!isNew && value.id) {
        navMain.push({
          title: t("persona.sidebar.notebook"),
          url: "/notebook",
          icon: IconNotebook,
          showOnNew: false,
        })
        navMain.push({
          title: t("persona.sidebar.fitReport"),
          url: "/fit-report",
          icon: IconReportAnalytics,
          showOnNew: false,
        })
      }
    }
    navMain.push({
      title: t("persona.sidebar.guidelines"),
      url: "/guidelines",
      icon: IconBook2,
      showOnNew: true,
    })
    return { navMain }
  }, [isNew, showAudience, value.id, t])

  const outletContext: PersonaFormContext = {
    form,
    value,
    isNew,
    stores,
    mcpServers,
    onDelete,
    deleteOpen,
    setDeleteOpen,
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 md:space-y-6"
        autoComplete="off"
      >
        <StickySaveBar
          title={watchedName || (isNew ? t("persona.newInstance") : t("persona.title"))}
          onCancel={() => navigate(urlBase)}
        />

        <SubPage
          icon={IconUserCircle}
          feature={t("persona.title")}
          name={watchedName || value.name || t("persona.title")}
          urlBase={instanceUrlBase}
          isNew={isNew}
          data={navData}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          onDelete={onDelete}
          outletContext={outletContext}
        />
      </form>
    </Form>
  )
}
