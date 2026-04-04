import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";
import { useTuringAutoComplete } from "../hooks/use-turing-autocomplete";
import { useTuringSearchHistory } from "../hooks/use-turing-search-history";
import type { UseTuringUrlSearchReturn } from "../hooks/use-turing-url-search";

// ── Context ──

interface SearchFieldContextValue {
  turing: UseTuringUrlSearchReturn;
  suggestions: string[];
  filteredHistory: string[];
  showDropdown: boolean;
  handleInputChange: (value: string) => void;
  handleFocus: () => void;
  handleBlur: () => void;
  handleSubmit: () => void;
  handlePick: (term: string) => void;
  removeFromHistory: (term: string) => void;
  clearAllHistory: () => void;
}

const SearchFieldContext = createContext<SearchFieldContextValue | null>(null);

function useSearchFieldContext() {
  const ctx = useContext(SearchFieldContext);
  if (!ctx) throw new Error("TuringSearchField.* must be used inside <TuringSearchField>");
  return ctx;
}

// ── Root ──

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

  const value: SearchFieldContextValue = {
    turing,
    suggestions,
    filteredHistory,
    showDropdown,
    handleInputChange,
    handleFocus,
    handleBlur,
    handleSubmit,
    handlePick,
    removeFromHistory,
    clearAllHistory,
  };

  return (
    <SearchFieldContext.Provider value={value}>
      <div className={className}>{children}</div>
    </SearchFieldContext.Provider>
  );
}

// ── Input ──

interface SearchFieldInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "onFocus" | "onBlur" | "value"> {
  className?: string;
}

function SearchFieldInput(props: SearchFieldInputProps) {
  const { turing, handleInputChange, handleFocus, handleBlur, handleSubmit } = useSearchFieldContext();

  return (
    <input
      type="search"
      autoComplete="off"
      {...props}
      value={turing.inputValue}
      onChange={(e) => handleInputChange(e.target.value)}
      onFocus={handleFocus}
      onBlur={handleBlur}
      onKeyDown={(e) => {
        if (e.key === "Enter") handleSubmit();
        props.onKeyDown?.(e);
      }}
    />
  );
}

// ── Button ──

interface SearchFieldButtonProps {
  children: ReactNode;
  className?: string;
}

function SearchFieldButton({ children, className }: SearchFieldButtonProps) {
  const { handleSubmit } = useSearchFieldContext();

  return (
    <button type="button" className={className} onClick={handleSubmit}>
      {children}
    </button>
  );
}

// ── Dropdown ──

interface SearchFieldDropdownProps {
  className?: string;
  /** Render a suggestion item */
  renderSuggestion?: (term: string, onClick: () => void) => ReactNode;
  /** Render a history item */
  renderHistory?: (term: string, onClick: () => void, onRemove: () => void) => ReactNode;
  /** Render the history header with "clear all" */
  renderHistoryHeader?: (onClearAll: () => void) => ReactNode;
  /** Label for the "Recent searches" header (default "Recent searches") */
  historyLabel?: string;
  /** Label for "Clear all" button (default "Clear all") */
  clearAllLabel?: string;
}

function SearchFieldDropdown({
  className,
  renderSuggestion,
  renderHistory,
  renderHistoryHeader,
  historyLabel = "Recent searches",
  clearAllLabel = "Clear all",
}: SearchFieldDropdownProps) {
  const {
    suggestions,
    filteredHistory,
    showDropdown,
    handlePick,
    removeFromHistory,
    clearAllHistory,
  } = useSearchFieldContext();

  // Autocomplete suggestions take priority
  if (suggestions.length > 0) {
    return (
      <div className={className} role="listbox">
        {suggestions.map((term) =>
          renderSuggestion ? (
            renderSuggestion(term, () => handlePick(term))
          ) : (
            <button
              key={term}
              type="button"
              role="option"
              className="w-full px-4 py-2.5 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors flex items-center gap-2 cursor-pointer"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handlePick(term)}
            >
              {term}
            </button>
          ),
        )}
      </div>
    );
  }

  // History fallback
  if (showDropdown && filteredHistory.length > 0) {
    return (
      <div className={className} role="listbox">
        {renderHistoryHeader ? (
          renderHistoryHeader(clearAllHistory)
        ) : (
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 dark:border-gray-700">
            <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              {historyLabel}
            </span>
            <button
              type="button"
              className="text-xs text-gray-500 hover:text-red-500 transition-colors cursor-pointer"
              onMouseDown={(e) => e.preventDefault()}
              onClick={clearAllHistory}
            >
              {clearAllLabel}
            </button>
          </div>
        )}
        {filteredHistory.map((term) =>
          renderHistory ? (
            renderHistory(term, () => handlePick(term), () => removeFromHistory(term))
          ) : (
            <div key={term} className="flex items-center hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
              <button
                type="button"
                role="option"
                className="flex-1 px-4 py-2.5 text-left text-sm flex items-center gap-2 cursor-pointer"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handlePick(term)}
              >
                {term}
              </button>
              <button
                type="button"
                title="Remove"
                className="px-3 py-2.5 text-gray-400 hover:text-red-500 transition-colors cursor-pointer"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => removeFromHistory(term)}
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

// ── Composite export ──

export const TuringSearchField = Object.assign(TuringSearchFieldRoot, {
  Input: SearchFieldInput,
  Button: SearchFieldButton,
  Dropdown: SearchFieldDropdown,
});
