import {
  createContext,
  useContext,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";

// ── Context ──

/**
 * The state + callbacks the view needs to render. The orchestration (the
 * autocomplete / history / url-search hooks that produce these values) lives in
 * the data-wiring wrapper — `TuringSearchField` in `@viglet/turing-react-sdk` —
 * which feeds this view through props. Keeping only primitives here is what lets
 * this package stay zero-runtime-dep (it never imports the SDK's hook types).
 */
export interface TuringSearchFieldViewContextValue {
  /** Current (controlled) input value. */
  inputValue: string;
  /** Autocomplete suggestions; take priority over history in the dropdown. */
  suggestions: string[];
  /** History terms already filtered to the current input + max-count. */
  filteredHistory: string[];
  /** Whether the dropdown should show (history fallback gate). */
  showDropdown: boolean;
  /** Called as the user types. */
  onInputChange: (value: string) => void;
  /** Called when the input gains focus. */
  onFocus: () => void;
  /** Called when the input loses focus. */
  onBlur: () => void;
  /** Called to submit the current query (Enter / button click). */
  onSubmit: () => void;
  /** Called when a suggestion or history term is picked. */
  onPick: (term: string) => void;
  /** Called to remove a single history term. */
  onRemoveHistory: (term: string) => void;
  /** Called to clear all history. */
  onClearHistory: () => void;
}

const TuringSearchFieldContext =
  createContext<TuringSearchFieldViewContextValue | null>(null);

function useTuringSearchFieldContext() {
  const ctx = useContext(TuringSearchFieldContext);
  if (!ctx)
    throw new Error(
      "TuringSearchFieldView.* must be used inside <TuringSearchFieldView>",
    );
  return ctx;
}

// ── Root ──

export interface TuringSearchFieldViewProps
  extends TuringSearchFieldViewContextValue {
  children: ReactNode;
  className?: string;
}

/**
 * Headless compound search field — the presentation half of the legacy
 * `TuringSearchField`. It owns the markup structure (input + button + dropdown
 * with suggestions/history) and the keyboard/mouse wiring, but carries no
 * autocomplete/history/search logic and **no baked-in styling** (the old
 * defaults shipped Tailwind `px-4 py-2.5 hover:bg-gray-100 …` markup, a
 * violation of the headless contract — those are gone). Every visual is pushed
 * to the host via `className` on the sub-components and the dropdown's
 * `renderSuggestion` / `renderHistory` / `renderHistoryHeader` render props.
 *
 * <p>Wire it with state + callbacks via props; the canonical wiring is
 * `TuringSearchField` in `@viglet/turing-react-sdk`, which runs the hooks and
 * forwards them here, re-attaching these same sub-components so consumers keep
 * writing `<TuringSearchField.Input />`. Migrated/split in T307.</p>
 *
 * @example
 * ```tsx
 * <TuringSearchFieldView {...viewState} className="relative">
 *   <div className="flex items-center gap-2">
 *     <TuringSearchFieldView.Input placeholder="Search…" className="flex-1" />
 *     <TuringSearchFieldView.Button className="btn">Search</TuringSearchFieldView.Button>
 *   </div>
 *   <TuringSearchFieldView.Dropdown className="absolute mt-1 w-full" />
 * </TuringSearchFieldView>
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
function TuringSearchFieldViewRoot({
  children,
  className,
  ...value
}: Readonly<TuringSearchFieldViewProps>) {
  return (
    <TuringSearchFieldContext.Provider value={value}>
      <div className={className}>{children}</div>
    </TuringSearchFieldContext.Provider>
  );
}

TuringSearchFieldViewRoot.displayName = "TuringSearchFieldView";

// ── Input ──

export interface TuringSearchFieldViewInputProps
  extends Omit<
    InputHTMLAttributes<HTMLInputElement>,
    "onChange" | "onFocus" | "onBlur" | "value"
  > {
  className?: string;
}

function SearchFieldViewInput(props: Readonly<TuringSearchFieldViewInputProps>) {
  const { inputValue, onInputChange, onFocus, onBlur, onSubmit } =
    useTuringSearchFieldContext();

  return (
    <input
      type="search"
      autoComplete="off"
      {...props}
      value={inputValue}
      onChange={(e) => onInputChange(e.target.value)}
      onFocus={onFocus}
      onBlur={onBlur}
      onKeyDown={(e) => {
        if (e.key === "Enter") onSubmit();
        props.onKeyDown?.(e);
      }}
    />
  );
}

SearchFieldViewInput.displayName = "TuringSearchFieldView.Input";

// ── Button ──

export interface TuringSearchFieldViewButtonProps {
  children: ReactNode;
  className?: string;
}

function SearchFieldViewButton({
  children,
  className,
}: Readonly<TuringSearchFieldViewButtonProps>) {
  const { onSubmit } = useTuringSearchFieldContext();

  return (
    <button type="button" className={className} onClick={onSubmit}>
      {children}
    </button>
  );
}

SearchFieldViewButton.displayName = "TuringSearchFieldView.Button";

// ── Dropdown ──

export interface TuringSearchFieldViewDropdownProps {
  className?: string;
  /** Render a suggestion item. */
  renderSuggestion?: (term: string, onClick: () => void) => ReactNode;
  /** Render a history item. */
  renderHistory?: (
    term: string,
    onClick: () => void,
    onRemove: () => void,
  ) => ReactNode;
  /** Render the history header with "clear all". */
  renderHistoryHeader?: (onClearAll: () => void) => ReactNode;
  /** Label for the "Recent searches" header (default "Recent searches"). */
  historyLabel?: string;
  /** Label for "Clear all" button (default "Clear all"). */
  clearAllLabel?: string;
}

function SearchFieldViewDropdown({
  className,
  renderSuggestion,
  renderHistory,
  renderHistoryHeader,
  historyLabel = "Recent searches",
  clearAllLabel = "Clear all",
}: Readonly<TuringSearchFieldViewDropdownProps>) {
  const {
    suggestions,
    filteredHistory,
    showDropdown,
    onPick,
    onRemoveHistory,
    onClearHistory,
  } = useTuringSearchFieldContext();

  // Autocomplete suggestions take priority.
  if (suggestions.length > 0) {
    return (
      <div className={className} role="listbox">
        {suggestions.map((term) =>
          renderSuggestion ? (
            renderSuggestion(term, () => onPick(term))
          ) : (
            <button
              key={term}
              type="button"
              role="option"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => onPick(term)}
            >
              {term}
            </button>
          ),
        )}
      </div>
    );
  }

  // History fallback.
  if (showDropdown && filteredHistory.length > 0) {
    return (
      <div className={className} role="listbox">
        {renderHistoryHeader ? (
          renderHistoryHeader(onClearHistory)
        ) : (
          <div>
            <span>{historyLabel}</span>
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={onClearHistory}
            >
              {clearAllLabel}
            </button>
          </div>
        )}
        {filteredHistory.map((term) =>
          renderHistory ? (
            renderHistory(
              term,
              () => onPick(term),
              () => onRemoveHistory(term),
            )
          ) : (
            <div key={term}>
              <button
                type="button"
                role="option"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => onPick(term)}
              >
                {term}
              </button>
              <button
                type="button"
                title="Remove"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => onRemoveHistory(term)}
              >
                &times;
              </button>
            </div>
          ),
        )}
      </div>
    );
  }

  return null;
}

SearchFieldViewDropdown.displayName = "TuringSearchFieldView.Dropdown";

// ── Composite export ──

/**
 * Headless compound: `TuringSearchFieldView` Root + `.Input` / `.Button` /
 * `.Dropdown`. See {@link TuringSearchFieldViewRoot}.
 */
export const TuringSearchFieldView = Object.assign(TuringSearchFieldViewRoot, {
  Input: SearchFieldViewInput,
  Button: SearchFieldViewButton,
  Dropdown: SearchFieldViewDropdown,
});
