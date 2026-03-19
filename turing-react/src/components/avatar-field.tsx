import { IconPalette, IconTrash } from "@tabler/icons-react"
import { useCallback, useState } from "react"

import { DiceBearAvatarPicker } from "@/components/dicebear-avatar-picker"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar"
import { cn } from "@/lib/utils"

interface AvatarFieldProps {
  avatarUrl?: string
  initials: string
  seed: string
  size?: "sm" | "md" | "lg"
  onSelect: (url: string) => void | Promise<void>
  onRemove?: () => void | Promise<void>
  children?: React.ReactNode
  className?: string
}

const sizeMap = {
  sm: "size-10",
  md: "size-16",
  lg: "size-24",
} as const

const textSizeMap = {
  sm: "text-xs",
  md: "text-lg",
  lg: "text-2xl",
} as const

const iconSizeMap = {
  sm: "size-3.5",
  md: "size-5",
  lg: "size-7",
} as const

export function AvatarField({
  avatarUrl,
  initials,
  seed,
  size = "md",
  onSelect,
  onRemove,
  children,
  className,
}: AvatarFieldProps) {
  const [pickerOpen, setPickerOpen] = useState(false)

  const handleSelect = useCallback(async (url: string) => {
    await onSelect(url)
  }, [onSelect])

  return (
    <div className={cn("flex items-center gap-4", className)}>
      <AvatarField.Preview
        avatarUrl={avatarUrl}
        initials={initials}
        size={size}
        onOpenPicker={() => setPickerOpen(true)}
      />

      <div>
        {children}
        <AvatarField.Actions
          avatarUrl={avatarUrl}
          onOpenPicker={() => setPickerOpen(true)}
          onRemove={onRemove}
        />
      </div>

      <DiceBearAvatarPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={handleSelect}
        defaultSeed={seed}
        currentAvatarUrl={avatarUrl}
      />
    </div>
  )
}

/* ── Sub-components (composite pattern) ── */

interface PreviewProps {
  avatarUrl?: string
  initials: string
  size?: "sm" | "md" | "lg"
  alt?: string
  onOpenPicker: () => void
}

AvatarField.Preview = function AvatarFieldPreview({
  avatarUrl,
  initials,
  size = "md",
  alt,
  onOpenPicker,
}: PreviewProps) {
  return (
    <div className="relative group">
      <GradientAvatar className={sizeMap[size]}>
        {avatarUrl ? <GradientAvatarImage src={avatarUrl} alt={alt ?? initials} /> : null}
        <GradientAvatarFallback className={textSizeMap[size]}>{initials}</GradientAvatarFallback>
      </GradientAvatar>
      <button
        type="button"
        title="Choose avatar"
        onClick={onOpenPicker}
        className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
      >
        <IconPalette className={cn("text-white", iconSizeMap[size])} />
      </button>
    </div>
  )
}

interface ActionsProps {
  avatarUrl?: string
  onOpenPicker: () => void
  onRemove?: () => void | Promise<void>
}

AvatarField.Actions = function AvatarFieldActions({
  avatarUrl,
  onOpenPicker,
  onRemove,
}: ActionsProps) {
  return (
    <div className="flex items-center gap-2 mt-1">
      <button
        type="button"
        onClick={onOpenPicker}
        className="text-xs text-primary hover:underline cursor-pointer"
      >
        Choose avatar
      </button>
      {avatarUrl && onRemove && (
        <button
          type="button"
          onClick={onRemove}
          className="text-xs text-destructive hover:underline cursor-pointer flex items-center gap-0.5"
        >
          <IconTrash className="size-3" />
          Remove
        </button>
      )}
    </div>
  )
}
