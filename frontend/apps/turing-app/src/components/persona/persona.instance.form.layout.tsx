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
  IconTrash,
  IconUserCircle,
  IconUsers,
} from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useForm, type UseFormReturn } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { NavLink, Outlet, useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { BentoActionsMenu, BentoFormHero } from "@/components/bento"
import { DialogDelete } from "@/components/dialog.delete"
import { SectionCardChromeProvider } from "@/components/ui/section-card"
import { SubPage } from "../sub.page"
import { StickySaveBar } from "../ui/sticky-save-bar"

const turStoreInstanceService = new TurStoreInstanceService()
const turMcpServerService = new TurMcpServerService()

interface Props {
  value: TurPersona
  isNew: boolean
  /**
   * Base list route for cancel/after-save/delete navigation and the section
   * sub-nav links. Defaults to the console persona route; the Bento shell
   * passes its own base so the editor's section links stay inside Bento (T556).
   */
  baseRoute?: string
  /**
   * Chrome around the shared form: the console `SubPage` sidebar (default) or
   * the Bento hero + pill tab-bar (matches the AI-agent surface, no sidebar).
   */
  chrome?: "console" | "bento"
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
export const PersonaInstanceFormLayout: React.FC<Props> = ({ value, isNew, baseRoute = ROUTES.PERSONA_INSTANCE, chrome = "console" }) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const urlBase = baseRoute
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
      // Notebook + content-fit report need a saved persona (FK). In the Bento
      // shell they're reached through the "Validate content" launch surface
      // (T584), so we don't duplicate them as editor tabs there; the legacy
      // console editor keeps them inline.
      if (!isNew && value.id && chrome === "console") {
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
  }, [isNew, showAudience, value.id, chrome, t])

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
        {chrome === "bento" ? (
          <>
            {/*
             * Single-component "hero → sticky save bar" morph (matches the LLM
             * instance surface): Save/Cancel live in the hero and fade away as
             * you scroll; a fixed frosted bar fades in below the shell header.
             * Rendered inside the <form>, so both Save buttons submit it.
             */}
            <BentoFormHero
              backTo={isNew ? baseRoute : instanceUrlBase}
              backLabel={isNew ? t("persona.title") : (value.name || t("persona.title"))}
              leading={
                <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
                  <IconUserCircle size={24} />
                </span>
              }
              // Match the "Configure persona" dashboard card that opens this
              // editor: the persona name is the eyebrow breadcrumb (back to the
              // dashboard), so the title is the section — not the name again
              // (which read as a duplicate of the eyebrow).
              title={isNew ? t("persona.newInstance") : t("persona.dashboard.configureTitle")}
              subtitle={t("persona.dashboard.configureHint")}
              trailing={
                !isNew ? (
                  <BentoActionsMenu
                    actions={[
                      {
                        label: t("forms.formActions.delete"),
                        icon: IconTrash,
                        tone: "destructive",
                        onSelect: () => setDeleteOpen(true),
                      },
                    ]}
                  />
                ) : undefined
              }
              onCancel={() => navigate(urlBase)}
              loading={form.formState.isSubmitting}
              dirty={isNew || form.formState.isDirty}
              titleMissing={!watchedName?.trim()}
              stickyTitle={watchedName || value.name || (isNew ? t("persona.newInstance") : t("persona.title"))}
            />

            <nav className="mb-6 flex flex-wrap gap-2">
              {navData.navMain.map((item) => {
                const ItemIcon = item.icon
                return (
                  <NavLink
                    key={item.url}
                    to={`${instanceUrlBase}${item.url}`}
                    className={({ isActive }) =>
                      `bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                        isActive
                          ? "border-primary/40 bg-primary text-primary-foreground"
                          : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
                      }`
                    }
                  >
                    <ItemIcon size={16} />
                    {item.title}
                  </NavLink>
                )
              })}
            </nav>

            <SectionCardChromeProvider chrome="bento">
              <Outlet context={outletContext} />
            </SectionCardChromeProvider>

            {!isNew && (
              <DialogDelete
                feature={t("persona.title")}
                name={value.name}
                onDelete={onDelete}
                open={deleteOpen}
                setOpen={setDeleteOpen}
                trigger={<span className="hidden" aria-hidden />}
              />
            )}
          </>
        ) : (
          <>
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
          </>
        )}
      </form>
    </Form>
  )
}
