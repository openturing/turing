import { IconDeviceFloppy, IconLoader2, IconTrash, IconX } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { GradientButton } from "./gradient-button";

// ---------------------------------------------------------------------------
// FormActions (root)
// ---------------------------------------------------------------------------

interface FormActionsProps {
  children: ReactNode;
  className?: string;
}

function FormActions({ children, className }: Readonly<FormActionsProps>) {
  return (
    <div className={cn("flex items-center justify-end gap-3 pt-4 border-t", className)}>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cancel
// ---------------------------------------------------------------------------

interface CancelProps {
  onClick: () => void;
  children?: ReactNode;
  className?: string;
}

function Cancel({ onClick, children, className }: Readonly<CancelProps>) {
  const { t } = useTranslation();
  return (
    <GradientButton type="button" variant="outline" onClick={onClick} className={className}>
      <IconX className="size-4" />
      {children ?? t("forms.formActions.cancel")}
    </GradientButton>
  );
}

// ---------------------------------------------------------------------------
// Submit
// ---------------------------------------------------------------------------

interface SubmitProps {
  loading?: boolean;
  disabled?: boolean;
  children?: ReactNode;
  className?: string;
}

function Submit({ loading, disabled, children, className }: Readonly<SubmitProps>) {
  const { t } = useTranslation();
  return (
    <GradientButton type="submit" disabled={disabled || loading} className={className}>
      {loading ? (
        <IconLoader2 className="size-4 animate-spin" />
      ) : (
        <IconDeviceFloppy className="size-4" />
      )}
      {children ?? t("forms.formActions.saveChanges")}
    </GradientButton>
  );
}

// ---------------------------------------------------------------------------
// Delete (mobile only — shown alongside Cancel/Submit)
// ---------------------------------------------------------------------------

interface DeleteProps {
  onClick: () => void;
  children?: ReactNode;
  className?: string;
}

function Delete({ onClick, children, className }: Readonly<DeleteProps>) {
  const { t } = useTranslation();
  return (
    <GradientButton
      type="button"
      variant="outline"
      onClick={onClick}
      className={cn("md:hidden text-destructive border-destructive/30 hover:bg-destructive/10 mr-auto", className)}
    >
      <IconTrash className="size-4" />
      {children ?? t("forms.formActions.delete")}
    </GradientButton>
  );
}

// ---------------------------------------------------------------------------
// Composite export
// ---------------------------------------------------------------------------

FormActions.Cancel = Cancel;
FormActions.Submit = Submit;
FormActions.Delete = Delete;

export { FormActions };
