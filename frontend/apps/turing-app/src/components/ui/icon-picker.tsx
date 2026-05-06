"use client"
import { Icon } from "@iconify/react"
import { IconPalette, IconSearch, IconTrash } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { IconPickerDialog } from "./icon-picker-dialog"

interface IconPickerProps {
  value?: string | null
  onChange: (icon: string) => void
  onRemove?: () => void
  /** Title of the entity — used for AI suggestion context */
  title?: string
  /** Description of the entity — used for AI suggestion context */
  description?: string
  children?: React.ReactNode
}

function IconPickerComponent({ value, onChange, onRemove, title, description, children }: IconPickerProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  return (
    <div className="flex items-center gap-4">
      {/* Preview circle — same style as AvatarField */}
      <div className="relative group">
        <div className="size-16 rounded-full bg-linear-to-br from-blue-600 to-indigo-600 dark:from-blue-500 dark:to-indigo-500 flex items-center justify-center shadow-sm">
          {value ? (
            <Icon icon={value} className="size-7 text-white" />
          ) : (
            <IconSearch className="size-6 text-white/60" />
          )}
        </div>
        <button
          type="button"
          title={t("forms.iconPicker.chooseIcon")}
          onClick={() => setOpen(true)}
          className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
        >
          <IconPalette className="size-5 text-white" />
        </button>
      </div>

      {/* Label + actions — same style as AvatarField */}
      <div>
        {children}
        {value ? (
          <span className="text-xs text-muted-foreground font-mono">{value}</span>
        ) : (
          <span className="text-sm text-muted-foreground">{t("forms.iconPicker.noIconSelected")}</span>
        )}
        <div className="flex items-center gap-2 mt-1">
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="text-xs text-primary hover:underline cursor-pointer"
          >
            {t("forms.iconPicker.chooseIcon")}
          </button>
          {value && onRemove && (
            <button
              type="button"
              onClick={onRemove}
              className="text-xs text-destructive hover:underline cursor-pointer flex items-center gap-0.5"
            >
              <IconTrash className="size-3" />
              {t("forms.common.remove")}
            </button>
          )}
        </div>
      </div>

      <IconPickerDialog
        open={open}
        onOpenChange={setOpen}
        value={value}
        onSelect={onChange}
        onClear={onRemove}
        title={title}
        description={description}
      />
    </div>
  )
}

/* ── Composite sub-components ── */

function IconPickerLabel({ children }: { children: React.ReactNode }) {
  return <div className="text-sm font-medium leading-none">{children}</div>
}

function IconPickerDescription({ children }: { children: React.ReactNode }) {
  return <p className="text-xs text-muted-foreground mt-0.5">{children}</p>
}

export const IconPicker = Object.assign(IconPickerComponent, {
  Label: IconPickerLabel,
  Description: IconPickerDescription,
})
