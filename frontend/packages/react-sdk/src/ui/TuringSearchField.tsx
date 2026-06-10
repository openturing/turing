import { useCallback, useEffect, useRef, useState, type FC, type ReactNode } from "react";
import {
  TuringSearchFieldView,
  type TuringSearchFieldViewInputProps,
  type TuringSearchFieldViewButtonProps,
  type TuringSearchFieldViewDropdownProps,
} from "@viglet/turing-react-ui";
import { useTuringAutoComplete } from "../hooks/use-turing-autocomplete";
import { useTuringSearchHistory } from "../hooks/use-turing-search-history";
import type { UseTuringUrlSearchReturn } from "../hooks/use-turing-url-search";

// ── Root (data-wiring wrapper) ──

interface TuringSearchFieldProps {
  /** The return value of useTuringUrlSearch */
  turing: UseTuringUrlSearchReturn;
  /** Autocomplete debounce in ms (default 300) */
  debounce?: number;
  /** Max history items to show (default 8) */
  maxHistory?: number;
  children: ReactNode;
  className?: string;
}

/**
 * Composite search field that encapsulates autocomplete, search history,
 * and dropdown logic. Use the sub-components to build your UI:
 *
 * <p>This is the data-wiring half of the component: it runs the
 * {@link useTuringAutoComplete} / {@link useTuringSearchHistory} hooks and the
 * results→history save effect, then forwards the resulting state + callbacks to
 * the headless {@code TuringSearchFieldView} in {@code @viglet/turing-react-ui}
 * (T307 split). The {@code .Input} / {@code .Button} / {@code .Dropdown}
 * sub-components are re-attached from the view, so consumer markup is unchanged.</p>
 *
 * @example
 * ```tsx
 * <TuringSearchField turing={turing} className="relative">
 *   <div className="flex items-center gap-2 border rounded-lg px-3 py-2">
 *     <TuringSearchField.Input placeholder="Search..." className="flex-1" />
 *     <TuringSearchField.Button className="btn-primary">Search</TuringSearchField.Button>
 *   </div>
 *   <TuringSearchField.Dropdown className="absolute z-50 mt-1 w-full" />
 * </TuringSearchField>
 * ```
 *
 * @since 2026.2.4
 */
function TuringSearchFieldRoot({
  turing,
  debounce = 300,
  maxHistory = 8,
  children,
  className,
}: TuringSearchFieldProps) {
  const { suggestions, fetch: fetchSuggestions, clear: clearSuggestions } = useTuringAutoComplete(debounce);
  const { history, save, remove, clear: clearHist } = useTuringSearchHistory();
  const [showDropdown, setShowDropdown] = useState(false);

  const filteredHistory = history
    .filter((h) => !turing.inputValue || h.toLowerCase().includes(turing.inputValue.toLowerCase()))
    .slice(0, maxHistory);

  const handleInputChange = useCallback((value: string) => {
    turing.setInputValue(value);
    fetchSuggestions(value);
  }, [turing, fetchSuggestions]);

  const handleFocus = useCallback(() => setShowDropdown(true), []);

  const handleBlur = useCallback(() => {
    globalThis.setTimeout(() => {
      clearSuggestions();
      setShowDropdown(false);
    }, 150);
  }, [clearSuggestions]);

  // Track the query that was submitted so we can save it after results arrive
  const pendingTermRef = useRef<string | null>(null);

  const handleSubmit = useCallback(() => {
    const term = turing.inputValue.trim();
    if (term && term !== "*") {
      pendingTermRef.current = term;
    }
    clearSuggestions();
    setShowDropdown(false);
    turing.submitSearch();
  }, [turing, clearSuggestions]);

  // Save to history only after search succeeds with results
  useEffect(() => {
    if (
      turing.status === "success" &&
      turing.documents.length > 0 &&
      pendingTermRef.current
    ) {
      save(pendingTermRef.current);
      pendingTermRef.current = null;
    } else if (turing.status === "success" && turing.documents.length === 0) {
      pendingTermRef.current = null;
    }
  }, [turing.status, turing.documents.length, save]);

  const handlePick = useCallback((term: string) => {
    turing.setInputValue(term);
    clearSuggestions();
    setShowDropdown(false);
  }, [turing, clearSuggestions]);

  const removeFromHistory = useCallback((term: string) => {
    remove(term);
  }, [remove]);

  const clearAllHistory = useCallback(() => {
    clearHist();
    setShowDropdown(false);
  }, [clearHist]);

  return (
    <TuringSearchFieldView
      className={className}
      inputValue={turing.inputValue}
      suggestions={suggestions}
      filteredHistory={filteredHistory}
      showDropdown={showDropdown}
      onInputChange={handleInputChange}
      onFocus={handleFocus}
      onBlur={handleBlur}
      onSubmit={handleSubmit}
      onPick={handlePick}
      onRemoveHistory={removeFromHistory}
      onClearHistory={clearAllHistory}
    >
      {children}
    </TuringSearchFieldView>
  );
}

// ── Composite export ──
// Re-attach the headless sub-components from react-ui so the public API stays
// `<TuringSearchField.Input />` etc. — they read the view context this wrapper
// provides through `TuringSearchFieldView`.
export const TuringSearchField: typeof TuringSearchFieldRoot & {
  Input: FC<TuringSearchFieldViewInputProps>;
  Button: FC<TuringSearchFieldViewButtonProps>;
  Dropdown: FC<TuringSearchFieldViewDropdownProps>;
} = Object.assign(TuringSearchFieldRoot, {
  Input: TuringSearchFieldView.Input,
  Button: TuringSearchFieldView.Button,
  Dropdown: TuringSearchFieldView.Dropdown,
});
