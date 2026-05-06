import { IconArrowUp } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const REVEAL_THRESHOLD_PX = 480;

/**
 * Floating "scroll to top" button — appears with an iOS spring
 * animation once the user has scrolled past the threshold, and
 * smooth-scrolls back to the top on click.
 *
 * Complements the sticky header rather than replacing it: the header
 * keeps navigation/branding always reachable, this button just cuts
 * the long way back when content is heavy.
 */
export function BentoBackToTop() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    function update() {
      setVisible(window.scrollY > REVEAL_THRESHOLD_PX);
    }
    update();
    window.addEventListener("scroll", update, { passive: true });
    return () => window.removeEventListener("scroll", update);
  }, []);

  return (
    <button
      type="button"
      aria-label="Back to top"
      tabIndex={visible ? 0 : -1}
      onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
      className={`fixed bottom-6 right-6 z-40 grid h-11 w-11 place-items-center rounded-full border border-border/60 bg-card/80 text-foreground shadow-lg backdrop-blur-xl transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:-translate-y-0.5 hover:shadow-xl active:scale-95 ${
        visible
          ? "translate-y-0 scale-100 opacity-100"
          : "pointer-events-none translate-y-4 scale-90 opacity-0"
      }`}
    >
      <IconArrowUp size={18} />
    </button>
  );
}
