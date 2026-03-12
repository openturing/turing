import { IconChevronDown } from "@tabler/icons-react";
import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { cn } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Color variants
// ---------------------------------------------------------------------------

const colorVariants = {
  blue: {
    color: "text-blue-600 dark:text-blue-400",
    bg: "from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20",
    ring: "ring-blue-500/10 dark:ring-blue-400/10",
  },
  violet: {
    color: "text-violet-600 dark:text-violet-400",
    bg: "from-violet-500/10 to-purple-500/10 dark:from-violet-500/20 dark:to-purple-500/20",
    ring: "ring-violet-500/10 dark:ring-violet-400/10",
  },
  emerald: {
    color: "text-emerald-600 dark:text-emerald-400",
    bg: "from-emerald-500/10 to-teal-500/10 dark:from-emerald-500/20 dark:to-teal-500/20",
    ring: "ring-emerald-500/10 dark:ring-emerald-400/10",
  },
  amber: {
    color: "text-amber-600 dark:text-amber-400",
    bg: "from-amber-500/10 to-orange-500/10 dark:from-amber-500/20 dark:to-orange-500/20",
    ring: "ring-amber-500/10 dark:ring-amber-400/10",
  },
  rose: {
    color: "text-rose-600 dark:text-rose-400",
    bg: "from-rose-500/10 to-pink-500/10 dark:from-rose-500/20 dark:to-pink-500/20",
    ring: "ring-rose-500/10 dark:ring-rose-400/10",
  },
  cyan: {
    color: "text-cyan-600 dark:text-cyan-400",
    bg: "from-cyan-500/10 to-sky-500/10 dark:from-cyan-500/20 dark:to-sky-500/20",
    ring: "ring-cyan-500/10 dark:ring-cyan-400/10",
  },
  orange: {
    color: "text-orange-600 dark:text-orange-400",
    bg: "from-orange-500/10 to-amber-500/10 dark:from-orange-500/20 dark:to-amber-500/20",
    ring: "ring-orange-500/10 dark:ring-orange-400/10",
  },
  slate: {
    color: "text-slate-600 dark:text-slate-400",
    bg: "from-slate-500/10 to-gray-500/10 dark:from-slate-500/20 dark:to-gray-500/20",
    ring: "ring-slate-500/10 dark:ring-slate-400/10",
  },
} as const;

type ColorVariant = keyof typeof colorVariants;

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

interface SectionCardContextValue {
  open: boolean;
  toggle: () => void;
  variant: ColorVariant;
}

const SectionCardContext = createContext<SectionCardContextValue>({
  open: true,
  toggle: () => {},
  variant: "blue",
});

// ---------------------------------------------------------------------------
// SectionCard (root)
// ---------------------------------------------------------------------------

interface SectionCardProps {
  variant?: ColorVariant;
  defaultOpen?: boolean;
  children: ReactNode;
  className?: string;
}

function SectionCard({ variant = "blue", defaultOpen = true, children, className }: Readonly<SectionCardProps>) {
  const [open, setOpen] = useState(defaultOpen);
  const toggle = () => setOpen((v) => !v);

  return (
    <SectionCardContext.Provider value={{ open, toggle, variant }}>
      <div className={cn("rounded-xl border overflow-hidden", className)}>
        {children}
      </div>
    </SectionCardContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

interface HeaderProps {
  icon: React.ElementType;
  title: string;
  description?: string;
  className?: string;
}

function Header({ icon: Icon, title, description, className }: Readonly<HeaderProps>) {
  const { open, toggle, variant } = useContext(SectionCardContext);
  const colors = colorVariants[variant];

  return (
    <button
      type="button"
      onClick={toggle}
      className={cn(
        "flex w-full items-center gap-3 px-5 py-4 bg-muted/40 border-b cursor-pointer hover:bg-muted/60 transition-colors",
        className,
      )}
    >
      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br ${colors.bg} ring-1 ${colors.ring}`}>
        <Icon className={`size-4 ${colors.color}`} />
      </div>
      <div className="min-w-0 text-left">
        <h3 className="text-sm font-semibold">{title}</h3>
        {description && (
          <p className="text-xs text-muted-foreground">{description}</p>
        )}
      </div>
      <IconChevronDown
        className={cn(
          "ml-auto size-4 text-muted-foreground shrink-0 transition-transform duration-200",
          open && "rotate-180",
        )}
      />
    </button>
  );
}

// ---------------------------------------------------------------------------
// Static Header (non-collapsible)
// ---------------------------------------------------------------------------

interface StaticHeaderProps {
  icon: React.ElementType;
  title: string;
  description?: string;
  className?: string;
}

function StaticHeader({ icon: Icon, title, description, className }: Readonly<StaticHeaderProps>) {
  const { variant } = useContext(SectionCardContext);
  const colors = colorVariants[variant];

  return (
    <div
      className={cn(
        "flex items-center gap-3 px-5 py-4 bg-muted/40 border-b",
        className,
      )}
    >
      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br ${colors.bg} ring-1 ${colors.ring}`}>
        <Icon className={`size-4 ${colors.color}`} />
      </div>
      <div className="min-w-0">
        <h3 className="text-sm font-semibold">{title}</h3>
        {description && (
          <p className="text-xs text-muted-foreground">{description}</p>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Content
// ---------------------------------------------------------------------------

interface ContentProps {
  children: ReactNode;
  className?: string;
}

function Content({ children, className }: Readonly<ContentProps>) {
  const { open } = useContext(SectionCardContext);

  if (!open) return null;

  return (
    <div className={cn("p-5 space-y-6", className)}>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Composite export
// ---------------------------------------------------------------------------

SectionCard.Header = Header;
SectionCard.StaticHeader = StaticHeader;
SectionCard.Content = Content;

export { SectionCard, colorVariants, type ColorVariant };
