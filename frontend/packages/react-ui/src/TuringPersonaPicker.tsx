import type { ReactNode } from "react";

/**
 * A persona the caller may offer for selection — the structural mirror of the
 * SDK's `TurPersonaOption` (T635), redeclared here so `@viglet/turing-react-ui`
 * stays zero-runtime-dependency (it must not import `@viglet/turing-sdk`).
 */
export interface TuringPersonaOption {
  id: string;
  name: string;
  /** Optional one-line hint, surfaced as the option's `title`. */
  description?: string;
}

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringPersonaPickerClassNames {
  /** The wrapping radiogroup container. */
  container?: string;
  /** The heading / prompt above the options. */
  label?: string;
  /** The list of option buttons. */
  list?: string;
  /** Each persona option `<button>`. */
  option?: string;
  /** Appended to the currently-selected option. */
  optionActive?: string;
  /** Appended to the synthetic "default voice" option (when shown). */
  optionDefault?: string;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringPersonaPickerLabels {
  /** Prompt above the options. Default `"Answer as"`. */
  heading?: string;
  /** Label for the "no persona / agent default" option. Default `"Default"`. */
  defaultOption?: string;
}

/** Optional icon nodes; when omitted each option shows text only (no icon dep). */
export interface TuringPersonaPickerIcons {
  /** Leading icon on every persona option. */
  persona?: ReactNode;
}

export interface TuringPersonaPickerProps {
  /** The personas to offer (typically the site agent's catalog). */
  options: ReadonlyArray<TuringPersonaOption>;
  /** Selected persona id; `null`/`undefined` means the agent's default voice. */
  value?: string | null;
  /** Fired with the chosen persona id, or `null` for the default option. */
  onChange: (personaId: string | null) => void;
  /**
   * When `true`, prepend a synthetic option that selects `null` (the agent's
   * default voice) — useful for "same question, different eyes" where the
   * baseline is one of the choices. Default `false`.
   */
  includeDefault?: boolean;
  /** Convenience alias for {@link TuringPersonaPickerClassNames.container}. */
  className?: string;
  classNames?: TuringPersonaPickerClassNames;
  labels?: TuringPersonaPickerLabels;
  icons?: TuringPersonaPickerIcons;
}

/**
 * Headless persona selector (T636) — "same question, different eyes". Renders a
 * radiogroup of the personas the host offers (plus an optional default-voice
 * option) and reports the selected persona id via {@link TuringPersonaPickerProps.onChange};
 * the caller then passes that id to the SDK's `postChatConversation`/content-fit
 * calls.
 *
 * <p>Pure renderer: it takes `options` + `value` + `onChange` via props and
 * touches no hook or API, so it carries no SDK/axios coupling. Like the other
 * `@viglet/turing-react-ui` primitives it ships no styling — skin every slot via
 * `className`/`classNames`, localize via `labels`, and plug in `icons` from
 * whatever set the host uses. The admin console and the public demo share this
 * one implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringPersonaPicker({
  options,
  value,
  onChange,
  includeDefault = false,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringPersonaPickerProps>) {
  if (options.length === 0 && !includeDefault) return null;

  const headingLabel = labels?.heading ?? "Answer as";
  const defaultLabel = labels?.defaultOption ?? "Default";
  const isDefaultActive = value == null;

  return (
    <div
      className={className ?? classNames?.container}
      data-turing-persona-picker=""
      role="radiogroup"
      aria-label={headingLabel}
    >
      <div className={classNames?.label}>{headingLabel}</div>
      <div className={classNames?.list}>
        {includeDefault && (
          <button
            type="button"
            role="radio"
            aria-checked={isDefaultActive}
            className={
              [classNames?.option, classNames?.optionDefault,
                isDefaultActive ? classNames?.optionActive : undefined]
                .filter(Boolean)
                .join(" ") || undefined
            }
            data-active={isDefaultActive ? "" : undefined}
            onClick={() => onChange(null)}
          >
            {defaultLabel}
          </button>
        )}
        {options.map((persona) => {
          const active = value === persona.id;
          const optionClass = [classNames?.option, active ? classNames?.optionActive : undefined]
            .filter(Boolean)
            .join(" ");
          return (
            <button
              key={persona.id}
              type="button"
              role="radio"
              aria-checked={active}
              className={optionClass || undefined}
              data-active={active ? "" : undefined}
              data-persona-id={persona.id}
              title={persona.description}
              onClick={() => onChange(persona.id)}
            >
              {icons?.persona}
              <span>{persona.name}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
