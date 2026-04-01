import {
  useCallback,
  useRef,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";

interface TuringSearchBarProps {
  onSearch: (query: string) => void;
  defaultQuery?: string;
  placeholder?: string;
  /** Render prop for the input element */
  renderInput?: (props: InputHTMLAttributes<HTMLInputElement> & { ref: React.Ref<HTMLInputElement> }) => ReactNode;
  /** Render prop for the submit button */
  renderButton?: (props: { onClick: () => void }) => ReactNode;
  className?: string;
  children?: ReactNode;
}

/**
 * Search bar with customizable input and button via render props.
 *
 * @since 2026.2.0
 */
export function TuringSearchBar({
  onSearch,
  defaultQuery = "",
  placeholder = "Search...",
  renderInput,
  renderButton,
  className,
  children,
}: TuringSearchBarProps) {
  const [value, setValue] = useState(defaultQuery);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      onSearch(value || "*");
    },
    [onSearch, value],
  );

  const inputProps: InputHTMLAttributes<HTMLInputElement> & { ref: React.Ref<HTMLInputElement> } = {
    ref: inputRef,
    type: "search",
    value,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setValue(e.target.value),
    placeholder,
    autoComplete: "off",
    "aria-label": placeholder,
  };

  return (
    <form onSubmit={handleSubmit} className={className} role="search">
      {renderInput ? renderInput(inputProps) : <input {...inputProps} />}
      {renderButton
        ? renderButton({ onClick: () => onSearch(value || "*") })
        : <button type="submit">Search</button>}
      {children}
    </form>
  );
}

/** Expose a controlled setter for external control of the input value */
TuringSearchBar.displayName = "TuringSearchBar";
