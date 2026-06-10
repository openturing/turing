import {
  TuringCopyButton,
  type TuringCopyButtonClassNames,
  type TuringCopyButtonIcons,
  type TuringCopyButtonLabels,
} from "./TuringCopyButton";

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringCodeBlockClassNames {
  /** Wrapper around the header + `<pre>`. */
  root?: string;
  /** The header bar (language label + copy button). */
  header?: string;
  /** The language caption. */
  language?: string;
  /** The `<pre>` element. */
  pre?: string;
  /** The `<code>` element (e.g. a `language-xxx` class for a highlighter). */
  code?: string;
  /** Passed through to the copy button's slots. */
  copyButton?: TuringCopyButtonClassNames;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringCodeBlockLabels {
  /** Copy-button labels. Defaults: `"Copy"` / `"Copied"`. */
  copy?: TuringCopyButtonLabels;
}

export interface TuringCodeBlockProps {
  /** The source to display (and copy). */
  code: string;
  /** Optional language tag shown in the header and used as `language-xxx` hint. */
  language?: string;
  /** Show the copy button. Default `true`. */
  showCopy?: boolean;
  /** Copy-button icons (see {@link TuringCopyButtonIcons}). */
  copyIcons?: TuringCopyButtonIcons;
  /** Convenience alias for {@link TuringCodeBlockClassNames.root}. */
  className?: string;
  classNames?: TuringCodeBlockClassNames;
  labels?: TuringCodeBlockLabels;
}

/**
 * Headless fenced code block with a built-in copy affordance — a `<pre><code>`
 * plus an optional header carrying the language tag and a {@link TuringCopyButton}.
 * Centralises the "code + copy" pattern so chat surfaces stop hand-rolling it.
 *
 * <p>It does not highlight syntax itself — the host markdown pipeline
 * ({@code rehype-highlight} etc.) styles the {@code <code>} children, and the
 * {@code classNames.code} slot carries any {@code language-xxx} class a
 * highlighter expects.</p>
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only and
 * carries no styling or icon dependency of its own. Skin via
 * {@code className}/{@code classNames}, localize via {@code labels}, and supply
 * copy {@code copyIcons} from whatever icon set the app uses. This is why the
 * admin console and viglet.com can share one implementation while keeping their
 * distinct looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringCodeBlock({
  code,
  language,
  showCopy = true,
  copyIcons,
  className,
  classNames,
  labels,
}: Readonly<TuringCodeBlockProps>) {
  const showHeader = Boolean(language) || showCopy;

  return (
    <div
      className={className ?? classNames?.root}
      data-turing-code-block=""
      data-language={language || undefined}
    >
      {showHeader && (
        <div className={classNames?.header}>
          {language ? (
            <span className={classNames?.language}>{language}</span>
          ) : (
            <span />
          )}
          {showCopy && (
            <TuringCopyButton
              value={code}
              classNames={classNames?.copyButton}
              labels={labels?.copy}
              icons={copyIcons}
            />
          )}
        </div>
      )}
      <pre className={classNames?.pre}>
        <code
          className={
            classNames?.code ??
            (language ? `language-${language}` : undefined)
          }
        >
          {code}
        </code>
      </pre>
    </div>
  );
}
