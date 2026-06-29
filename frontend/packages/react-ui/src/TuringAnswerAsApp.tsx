import { useState } from "react";
import type {
  TuringGenerativeComponentProps,
  TuringGenerativeRegistry,
} from "./TuringGenerativeContent";

/**
 * T442 / §XXIII.1 — "answer-as-an-app": the headless generative-UI components the
 * agent renders from a typed SN search result set instead of replying with prose.
 *
 * <p>Three components, keyed by the built-in client-tool names the backend
 * advertises ({@code comparison_table} / {@code spec_card} / {@code configurator},
 * see {@code TurAnswerAsAppClientTools}). Each receives the agent-supplied
 * {@code props} and a {@code respond} callback (T439/T440): a comparison table or
 * spec card responds when the user picks a row / clicks an action; a configurator
 * responds with the chosen control values for a follow-up search.
 *
 * <p>Like every `@viglet/turing-react-ui` primitive these ship ZERO styling and
 * zero runtime deps — skin via the per-slot `classNames`. Field formatting is
 * driven by the {@code type} the agent set on each column/field from the manifest
 * (T386): {@code currency} / {@code date} / {@code number} / {@code boolean} /
 * {@code image} / {@code url} / {@code string}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** Field/column type tags the agent sets from the manifest (T386). */
export type TuringAppFieldType =
  | "string"
  | "number"
  | "currency"
  | "date"
  | "boolean"
  | "image"
  | "url";

/** Control kinds a configurator can render. */
export type TuringAppControlType = "slider" | "range" | "select" | "toggle" | "text";

export interface TuringAppColumn {
  key: string;
  label: string;
  type?: TuringAppFieldType;
}

export interface TuringAppField {
  label: string;
  value: unknown;
  type?: TuringAppFieldType;
}

export interface TuringAppAction {
  label: string;
  value: string;
}

export interface TuringAppControl {
  key: string;
  label: string;
  type: TuringAppControlType;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  options?: string[];
}

/**
 * Format a typed value for display. Locale-aware for currency/date when the
 * value is parseable; otherwise falls back to the raw string. Intl is built into
 * every modern browser, so this stays zero-dependency.
 */
export function formatTypedValue(
  value: unknown,
  type: TuringAppFieldType | undefined,
  locale?: string,
): string {
  if (value === null || value === undefined) return "";
  switch (type) {
    case "currency": {
      // Accept "150.00,BRL" (Turing currency payload) or a plain number.
      const raw = String(value);
      const [amount, code] = raw.includes(",") ? raw.split(",") : [raw, undefined];
      const num = Number(amount);
      if (Number.isNaN(num)) return raw;
      try {
        return new Intl.NumberFormat(locale, {
          style: code ? "currency" : "decimal",
          currency: code || undefined,
        }).format(num);
      } catch {
        return raw;
      }
    }
    case "date": {
      const d = new Date(String(value));
      return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString(locale);
    }
    case "number": {
      const num = Number(value);
      return Number.isNaN(num) ? String(value) : num.toLocaleString(locale);
    }
    case "boolean":
      return value ? "✓" : "✗";
    default:
      return String(value);
  }
}

export interface TuringComparisonTableClassNames {
  container?: string;
  title?: string;
  table?: string;
  headerRow?: string;
  headerCell?: string;
  row?: string;
  cell?: string;
  image?: string;
  link?: string;
  selectButton?: string;
}

export interface TuringComparisonTableProps extends TuringGenerativeComponentProps {
  classNames?: TuringComparisonTableClassNames;
  /** Locale for currency/date formatting (defaults to the browser locale). */
  locale?: string;
  /** Override the per-row "select" button label (default: "Select"). */
  selectLabel?: string;
}

function renderCell(
  col: TuringAppColumn,
  value: unknown,
  cn: TuringComparisonTableClassNames | undefined,
  locale: string | undefined,
) {
  if (col.type === "image" && value) {
    return <img className={cn?.image} src={String(value)} alt={col.label} />;
  }
  if (col.type === "url" && value) {
    return (
      <a className={cn?.link} href={String(value)} target="_blank" rel="noreferrer">
        {String(value)}
      </a>
    );
  }
  return formatTypedValue(value, col.type, locale);
}

export function TuringComparisonTable({
  props,
  respond,
  classNames: cn,
  locale,
  selectLabel = "Select",
}: Readonly<TuringComparisonTableProps>) {
  const title = props.title as string | undefined;
  const columns = (props.columns as TuringAppColumn[] | undefined) ?? [];
  const rows = (props.rows as Array<Record<string, unknown>> | undefined) ?? [];
  return (
    <div className={cn?.container} data-turing-app="comparison_table">
      {title ? <div className={cn?.title}>{title}</div> : null}
      <table className={cn?.table}>
        <thead>
          <tr className={cn?.headerRow}>
            {columns.map((col) => (
              <th key={col.key} className={cn?.headerCell}>
                {col.label}
              </th>
            ))}
            <th className={cn?.headerCell} aria-label="select" />
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className={cn?.row}>
              {columns.map((col) => (
                <td key={col.key} className={cn?.cell}>
                  {renderCell(col, row[col.key], cn, locale)}
                </td>
              ))}
              <td className={cn?.cell}>
                <button
                  type="button"
                  className={cn?.selectButton}
                  onClick={() => respond({ selectedRow: row, index: i })}
                >
                  {selectLabel}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export interface TuringSpecCardClassNames {
  container?: string;
  image?: string;
  title?: string;
  subtitle?: string;
  fieldList?: string;
  fieldRow?: string;
  fieldLabel?: string;
  fieldValue?: string;
  actions?: string;
  actionButton?: string;
}

export interface TuringSpecCardProps extends TuringGenerativeComponentProps {
  classNames?: TuringSpecCardClassNames;
  locale?: string;
}

export function TuringSpecCard({
  props,
  respond,
  classNames: cn,
  locale,
}: Readonly<TuringSpecCardProps>) {
  const title = props.title as string | undefined;
  const subtitle = props.subtitle as string | undefined;
  const imageUrl = props.imageUrl as string | undefined;
  const fields = (props.fields as TuringAppField[] | undefined) ?? [];
  const actions = (props.actions as TuringAppAction[] | undefined) ?? [];
  return (
    <div className={cn?.container} data-turing-app="spec_card">
      {imageUrl ? <img className={cn?.image} src={imageUrl} alt={title ?? ""} /> : null}
      {title ? <div className={cn?.title}>{title}</div> : null}
      {subtitle ? <div className={cn?.subtitle}>{subtitle}</div> : null}
      <dl className={cn?.fieldList}>
        {fields.map((f, i) => (
          <div key={i} className={cn?.fieldRow}>
            <dt className={cn?.fieldLabel}>{f.label}</dt>
            <dd className={cn?.fieldValue}>
              {f.type === "url" && f.value ? (
                <a href={String(f.value)} target="_blank" rel="noreferrer">
                  {String(f.value)}
                </a>
              ) : (
                formatTypedValue(f.value, f.type, locale)
              )}
            </dd>
          </div>
        ))}
      </dl>
      {actions.length > 0 ? (
        <div className={cn?.actions}>
          {actions.map((a, i) => (
            <button
              key={i}
              type="button"
              className={cn?.actionButton}
              onClick={() => respond({ action: a.value })}
            >
              {a.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export interface TuringConfiguratorClassNames {
  container?: string;
  title?: string;
  controlRow?: string;
  controlLabel?: string;
  controlInput?: string;
  unit?: string;
  submitButton?: string;
}

export interface TuringConfiguratorProps extends TuringGenerativeComponentProps {
  classNames?: TuringConfiguratorClassNames;
  submitLabel?: string;
}

function initialControlValue(control: TuringAppControl): unknown {
  switch (control.type) {
    case "toggle":
      return false;
    case "slider":
      return control.min ?? 0;
    case "range":
      return [control.min ?? 0, control.max ?? 100];
    case "select":
      return control.options?.[0] ?? "";
    default:
      return "";
  }
}

export function TuringConfigurator({
  props,
  respond,
  classNames: cn,
  submitLabel,
}: Readonly<TuringConfiguratorProps>) {
  const title = props.title as string | undefined;
  const controls = (props.controls as TuringAppControl[] | undefined) ?? [];
  const submit = (props.submitLabel as string | undefined) ?? submitLabel ?? "Apply";
  const [values, setValues] = useState<Record<string, unknown>>(() => {
    const initial: Record<string, unknown> = {};
    for (const c of controls) initial[c.key] = initialControlValue(c);
    return initial;
  });

  const update = (key: string, value: unknown) =>
    setValues((prev) => ({ ...prev, [key]: value }));

  return (
    <div className={cn?.container} data-turing-app="configurator">
      {title ? <div className={cn?.title}>{title}</div> : null}
      {controls.map((c) => (
        <div key={c.key} className={cn?.controlRow}>
          <label className={cn?.controlLabel} htmlFor={`cfg-${c.key}`}>
            {c.label}
          </label>
          {renderControl(c, values[c.key], (v) => update(c.key, v), cn)}
          {c.unit ? <span className={cn?.unit}>{c.unit}</span> : null}
        </div>
      ))}
      <button
        type="button"
        className={cn?.submitButton}
        onClick={() => respond({ values })}
      >
        {submit}
      </button>
    </div>
  );
}

function renderControl(
  control: TuringAppControl,
  value: unknown,
  onChange: (value: unknown) => void,
  cn: TuringConfiguratorClassNames | undefined,
) {
  const id = `cfg-${control.key}`;
  switch (control.type) {
    case "slider":
      return (
        <input
          id={id}
          type="range"
          className={cn?.controlInput}
          min={control.min}
          max={control.max}
          step={control.step}
          value={Number(value ?? control.min ?? 0)}
          onChange={(e) => onChange(Number(e.target.value))}
        />
      );
    case "toggle":
      return (
        <input
          id={id}
          type="checkbox"
          className={cn?.controlInput}
          checked={Boolean(value)}
          onChange={(e) => onChange(e.target.checked)}
        />
      );
    case "select":
      return (
        <select
          id={id}
          className={cn?.controlInput}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        >
          {(control.options ?? []).map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      );
    default:
      return (
        <input
          id={id}
          type="text"
          className={cn?.controlInput}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}

/**
 * The default answer-as-app registry: the built-in client-tool names mapped to
 * their unstyled components. Pass to `useGenerativeUI` (React SDK) and to
 * `TuringGenerativeContent`. Wrap a component to inject `classNames` for skinning.
 */
export const ANSWER_AS_APP_COMPONENTS: TuringGenerativeRegistry = {
  comparison_table: TuringComparisonTable,
  spec_card: TuringSpecCard,
  configurator: TuringConfigurator,
};
