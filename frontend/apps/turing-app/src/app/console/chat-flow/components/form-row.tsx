import { Label } from "@/components/ui/label";

/**
 * Layout helper that mirrors {@code FormItemTwoColumns} from the design
 * system without depending on `react-hook-form`'s FormProvider context. The
 * chat flow editor stores its values in plain `useState`, so the design
 * system primitive (which uses `useFormContext` internally) cannot be used
 * directly here.
 */
export function FormRow({
  htmlFor,
  label,
  description,
  children,
}: Readonly<{
  htmlFor: string;
  label: string;
  description?: string;
  children: React.ReactNode;
}>) {
  return (
    <div className="grid gap-3 md:grid-cols-3 md:gap-6 md:items-start">
      <div className="md:col-span-1">
        <Label htmlFor={htmlFor} className="text-sm font-medium leading-none">
          {label}
        </Label>
        {description && (
          <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{description}</p>
        )}
      </div>
      <div className="md:col-span-2">{children}</div>
    </div>
  );
}
