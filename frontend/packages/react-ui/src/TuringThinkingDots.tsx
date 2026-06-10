/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringThinkingDotsClassNames {
  /** The inline container wrapping the dots. */
  root?: string;
  /** Each individual dot. The app's animation keyframe class goes here. */
  dot?: string;
}

export interface TuringThinkingDotsProps {
  /** How many dots to render. Default `3`. */
  count?: number;
  /**
   * Seconds added to each successive dot's {@code animation-delay}, so the host
   * app's keyframe animation (supplied via {@code classNames.dot}) staggers into
   * the classic typing-indicator wave. Default `0.18`.
   */
  delayStep?: number;
  /** Accessible label announced to screen readers. Default `"Thinking…"`. */
  label?: string;
  /** Convenience alias for {@link TuringThinkingDotsClassNames.root}. */
  className?: string;
  classNames?: TuringThinkingDotsClassNames;
}

/**
 * Headless "assistant is thinking" typing indicator — a row of dots with a
 * staggered {@code animation-delay} on each, the shape both viglet.com and the
 * admin reached for while a reply streams. The bounce/pulse keyframe itself is
 * the app's to define (pass its class via {@code classNames.dot}); the component
 * only lays out the dots and staggers them.
 *
 * <p><b>Design-agnostic by construction:</b> it renders structure only — no
 * colors, sizes, or the animation itself, just the per-dot delay that turns the
 * app's keyframe into a wave. Skin via {@code className}/{@code classNames},
 * localize the screen-reader text via {@code label}. This is why the admin
 * console and viglet.com can share one implementation while keeping their looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringThinkingDots({
  count = 3,
  delayStep = 0.18,
  label = "Thinking…",
  className,
  classNames,
}: Readonly<TuringThinkingDotsProps>) {
  return (
    <span
      className={className ?? classNames?.root}
      role="status"
      aria-label={label}
      data-turing-thinking-dots=""
    >
      {Array.from({ length: count }, (_, i) => (
        <span
          key={i}
          aria-hidden="true"
          className={classNames?.dot}
          style={{ animationDelay: `${i * delayStep}s` }}
        />
      ))}
    </span>
  );
}
