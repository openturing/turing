import { Button } from "@/components/ui/button";
import type { TurChatForm } from "@viglet/turing-react-sdk";
import { useState } from "react";

/**
 * Renders a native multi-field chat form (T107). On submit it hands the
 * collected slot values back to the chat hook's `submitForm`, which writes each
 * to its slot and streams the next assistant turn.
 */
export function NativeForm({
  form,
  disabled,
  onSubmit,
}: Readonly<{
  form: TurChatForm;
  disabled?: boolean;
  onSubmit: (values: Record<string, string>) => void;
}>) {
  const [values, setValues] = useState<Record<string, string>>({});
  const set = (name: string, v: string) => setValues((prev) => ({ ...prev, [name]: v }));

  const missingRequired = form.fields.some(
    (f) => f.required !== false && !(values[f.name] ?? "").trim()
  );

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (!missingRequired) onSubmit(values);
      }}
      className="mt-2 space-y-3 rounded-xl border border-primary/30 bg-primary/5 p-3"
    >
      {form.title && <p className="text-sm font-semibold">{form.title}</p>}
      {form.fields.map((field) => {
        const id = `nf-${field.name}`;
        const label = field.label ?? field.name;
        const common = {
          id,
          value: values[field.name] ?? "",
          required: field.required !== false,
          placeholder: field.placeholder,
          disabled,
          onChange: (e: { target: { value: string } }) => set(field.name, e.target.value),
          className:
            "w-full rounded-lg border border-border bg-background px-3 py-1.5 text-sm focus:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary/20",
        };
        return (
          <div key={field.name} className="space-y-1">
            <label htmlFor={id} className="text-xs font-medium text-muted-foreground">
              {label}
              {field.required !== false && <span className="text-destructive"> *</span>}
            </label>
            {field.type === "textarea" ? (
              <textarea rows={3} {...common} />
            ) : field.type === "select" && field.options ? (
              <select {...common}>
                <option value="">—</option>
                {field.options.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            ) : (
              <input type={field.type ?? "text"} {...common} />
            )}
          </div>
        );
      })}
      <Button type="submit" size="sm" disabled={disabled || missingRequired} className="w-full">
        Submit
      </Button>
    </form>
  );
}
