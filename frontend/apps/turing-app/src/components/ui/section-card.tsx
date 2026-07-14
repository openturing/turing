import { SectionCard as DesignSystemSectionCard } from "@viglet/viglet-design-system";
import {
  Children,
  createContext,
  isValidElement,
  useContext,
  type ComponentType,
  type ReactElement,
  type ReactNode,
} from "react";
import { BentoFormSection, type BentoTone } from "@/components/bento";

/**
 * Chrome a {@link SectionCard} renders in. `console` (default) delegates to the
 * design system's collapsible SectionCard; `bento` renders the frosted
 * {@link BentoFormSection} so a form whose sections are shared between the
 * console and the bento shell looks native in each — without duplicating any
 * field logic. Flip it once at the top of a bento page:
 *
 * ```tsx
 * <SectionCardChromeProvider chrome="bento">
 *   <MySharedSection state={state} />
 * </SectionCardChromeProvider>
 * ```
 *
 * @since 2026.3.4
 */
type SectionChrome = "console" | "bento";

const SectionChromeContext = createContext<SectionChrome>("console");

export function SectionCardChromeProvider({
  chrome,
  children,
}: Readonly<{ chrome: SectionChrome; children: ReactNode }>) {
  return <SectionChromeContext.Provider value={chrome}>{children}</SectionChromeContext.Provider>;
}

/**
 * Read the active {@link SectionChrome} from context. Shared sections use this
 * to drop console-only layout constraints (e.g. the narrow `max-w-2xl` centered
 * column) when rendered inside the full-width bento shell.
 *
 * @since 2026.3.4
 */
export function useSectionChrome(): SectionChrome {
  return useContext(SectionChromeContext);
}

/** SectionCard color variants → the closest Bento tone. */
type SectionVariant = "blue" | "violet" | "emerald" | "amber" | "rose" | "slate" | "cyan" | "orange";

const VARIANT_TONE: Record<SectionVariant, BentoTone> = {
  blue: "blue",
  violet: "violet",
  emerald: "emerald",
  amber: "amber",
  rose: "rose",
  slate: "slate",
  cyan: "blue",
  orange: "amber",
};

interface HeaderLikeProps {
  icon: ComponentType<{ size?: number; className?: string }>;
  title: string;
  description?: string;
}

interface SectionCardProps {
  variant?: SectionVariant;
  defaultOpen?: boolean;
  className?: string;
  children?: ReactNode;
}

/**
 * Drop-in replacement for the design system SectionCard that switches to a
 * frosted {@link BentoFormSection} when rendered under a `bento`
 * {@link SectionCardChromeProvider}. In bento mode it reads the
 * icon/title/description off the `SectionCard.Header` (or `.StaticHeader`)
 * child and the fields off the `SectionCard.Content` child, so callers keep
 * the exact same compound markup. Outside a bento provider it behaves
 * identically to the design system component.
 */
function SectionCard({ variant = "blue", defaultOpen, className, children }: Readonly<SectionCardProps>) {
  const chrome = useContext(SectionChromeContext);

  let header: HeaderLikeProps | undefined;
  let content: ReactNode;
  if (chrome === "bento") {
    Children.forEach(children, (child) => {
      if (!isValidElement(child)) return;
      const { type } = child as ReactElement;
      if (type === DesignSystemSectionCard.Header || type === DesignSystemSectionCard.StaticHeader) {
        header = (child as ReactElement<HeaderLikeProps>).props;
      } else if (type === DesignSystemSectionCard.Content) {
        content = (child as ReactElement<{ children?: ReactNode }>).props.children;
      }
    });
  }

  // Bento chrome, but only when we recognized a header — otherwise fall through
  // to the console card so no content is ever silently dropped.
  if (chrome === "bento" && header) {
    return (
      <BentoFormSection icon={header.icon} tone={VARIANT_TONE[variant] ?? "blue"} title={header.title} description={header.description}>
        {content}
      </BentoFormSection>
    );
  }

  return (
    <DesignSystemSectionCard variant={variant} defaultOpen={defaultOpen} className={className}>
      {children}
    </DesignSystemSectionCard>
  );
}

SectionCard.Header = DesignSystemSectionCard.Header;
SectionCard.StaticHeader = DesignSystemSectionCard.StaticHeader;
SectionCard.Content = DesignSystemSectionCard.Content;

export { SectionCard };
