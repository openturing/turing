import {
  useCallback,
  useRef,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
  type ChangeEvent,
} from "react";

/** Props for the input render-prop — the native input attributes plus a ref. */
export type TuringSearchBarInputProps = InputHTMLAttributes<HTMLInputElement> & {
  ref: Ref<HTMLInputElement>;
};

export interface TuringSearchBarProps {
  /** Called with the current query when the form is submitted. Empty input submits `"*"`. */
  onSearch: (query: string) => void;
  /** Initial (uncontrolled) query value. */
  defaultQuery?: string;
  /** Placeholder + `aria-label` for the default input. */
  placeholder?: string;
  /** Render prop for the input element. Defaults to a bare `<input type="search">`. */
  renderInput?: (props: TuringSearchBarInputProps) => ReactNode;
  /** Render prop for the submit button. Defaults to a bare `<button type="submit">`. */
  renderButton?: (props: { onClick: () => void }) => ReactNode;
  className?: string;
  children?: ReactNode;
}

/**
 * Headless search bar with customizable input and button via render props.
 *
 * <p>Pure renderer — it owns only the controlled input value and the submit
 * handler, and carries no API/hook coupling or baked-in styling. The host skins
 * it via {@code className} + the {@code renderInput}/{@code renderButton} render
 * props. Migrated from {@code @viglet/turing-react-sdk} (T306) so design-only
 * consumers (e.g. viglet.com) can use it without pulling the axios SDK; the SDK
 * keeps re-exporting it so existing imports are unchanged.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringSearchBar({
  onSearch,
  defaultQuery = "",
  placeholder = "Search...",
  renderInput,
  renderButton,
  className,
  children,
}: Readonly<TuringSearchBarProps>) {
  const [value, setValue] = useState(defaultQuery);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      onSearch(value || "*");
    },
    [onSearch, value],
  );

  const inputProps: TuringSearchBarInputProps = {
    ref: inputRef,
    type: "search",
    value,
    onChange: (e: ChangeEvent<HTMLInputElement>) => setValue(e.target.value),
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

TuringSearchBar.displayName = "TuringSearchBar";
