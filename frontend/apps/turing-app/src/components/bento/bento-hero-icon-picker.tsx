import { IconPickerDialog } from "@/components/ui/icon-picker-dialog";
import { Icon } from "@iconify/react";
import { IconPalette, IconX } from "@tabler/icons-react";
import { useState, type ComponentType } from "react";
import { useTranslation } from "react-i18next";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoHeroIconPickerProps {
  /** Current Iconify icon string (e.g. "tabler:robot"). When null/undefined, the default icon renders. */
  value?: string | null;
  /** Persist a new icon. */
  onChange: (icon: string) => void;
  /** Optional clear callback. When provided, a hover-revealed × badge appears on the chip when an icon is set. */
  onClear?: () => void;
  /** Default icon when `value` is empty. */
  defaultIcon: ComponentType<{ size?: number }>;
  /** Tone — drives the chip gradient. */
  tone: BentoTone;
  /** Title of the entity (passed to the dialog for AI suggestions). */
  title?: string;
  /** Description of the entity (passed to the dialog for AI suggestions). */
  description?: string;
  /** Render a static chip (no picker dialog, no hover overlay / clear). */
  readOnly?: boolean;
}

/**
 * Bento-flavoured icon picker. Renders as the leading chip of a
 * `BentoHero` (h-12 w-12 rounded-2xl with the page tone gradient),
 * with a hover overlay that signals it's editable. Click opens the
 * shared icon-picker dialog; a hover-revealed × in the corner clears
 * the current icon without opening the dialog.
 *
 * Designed to be the standard pattern for editable entity icons
 * across all bento detail/header pages — replacing the in-form
 * IconPicker field with a single click target on the page header.
 */
export function BentoHeroIconPicker({
  value,
  onChange,
  onClear,
  defaultIcon: DefaultIcon,
  tone,
  title,
  description,
  readOnly = false,
}: Readonly<BentoHeroIconPickerProps>) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  // Read-only: a static tonal chip with no picker / hover / clear affordance.
  if (readOnly) {
    return (
      <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} text-white shadow-md`}>
        {value ? <Icon icon={value} className="size-6 text-white" /> : <DefaultIcon size={24} />}
      </span>
    );
  }

  return (
    <div className="group relative">
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={t("forms.iconPicker.chooseAnIcon")}
        className={`group/icon relative grid h-12 w-12 cursor-pointer place-items-center overflow-hidden rounded-2xl bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} text-white shadow-md outline-none transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-105 focus-visible:ring-2 focus-visible:ring-ring active:scale-95`}
      >
        {value ? <Icon icon={value} className="size-6 text-white" /> : <DefaultIcon size={24} />}

        {/* Hover overlay — palette icon signals "click to change" */}
        <span className="pointer-events-none absolute inset-0 grid place-items-center bg-black/40 opacity-0 transition-opacity duration-200 group-hover/icon:opacity-100">
          <IconPalette className="size-5 text-white" />
        </span>
      </button>

      {/* Hover-revealed clear badge. Only when an icon is set + onClear provided. */}
      {value && onClear && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onClear();
          }}
          aria-label={t("forms.common.remove")}
          title={t("forms.common.remove")}
          className="absolute -right-1.5 -top-1.5 grid h-5 w-5 cursor-pointer place-items-center rounded-full border border-border/60 bg-card/90 text-foreground opacity-0 shadow-md backdrop-blur transition-opacity duration-200 hover:bg-rose-500/15 hover:text-rose-600 focus-visible:opacity-100 group-hover:opacity-100 dark:hover:text-rose-400"
        >
          <IconX size={12} />
        </button>
      )}

      <IconPickerDialog
        open={open}
        onOpenChange={setOpen}
        value={value}
        onSelect={onChange}
        onClear={onClear}
        title={title}
        description={description}
      />
    </div>
  );
}
