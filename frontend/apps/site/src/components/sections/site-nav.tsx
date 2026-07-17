import { useEffect, useState } from "react";
import { ArrowRight, ArrowUpRight, ChevronDown, Menu, Sparkles, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ModeToggle } from "@/components/mode-toggle";
import { Container, Logo, LINKS } from "@/components/brand";
import { cn } from "@/lib/utils";

// Hrefs are root-anchored ("/#…") so they also work from the feature pages —
// the browser navigates home and scrolls to the section.
//
// The bar stays uncluttered: two direct links ("Try it live", "Docs") flank two
// grouped dropdowns. AEM is deliberately pulled OUT of the Solutions dropdown
// and rendered as a highlighted top-level pill — it's our headline solution.
const NAV_LINKS = [
  { label: "Try it live", href: "/#playground" },
  { label: "Docs", href: LINKS.docs },
];

const NAV_GROUPS = [
  {
    label: "Product",
    items: [
      { label: "Capabilities", href: "/#features" },
      { label: "AI Personas", href: "/persona" },
      { label: "Why Turing", href: "/#why" },
      { label: "Developers", href: "/#developers" },
      { label: "Marketplace", href: "/#marketplace" },
    ],
  },
  {
    label: "Solutions",
    items: [
      { label: "Security & data residency", href: "/security" },
      { label: "Compare", href: "/compare" },
      { label: "Migrate", href: "/migrate" },
    ],
  },
];

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Close the mobile menu on Escape (the panel itself closes on any nav click).
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  return (
    <header
      className={cn(
        "sticky top-0 z-50 backdrop-blur-md transition-colors",
        scrolled
          ? "border-b border-border bg-background/80"
          : "border-b border-transparent bg-background/60"
      )}
    >
      <Container className="flex h-16 items-center gap-4">
        <div className="flex shrink-0 items-center gap-2.5">
          <a href="/" className="text-base" aria-label="Viglet Turing ES — home">
            {/* Icon-only mark on mobile to leave room for the CTA + menu; full
                wordmark from sm up. */}
            <Logo className="hidden sm:inline-flex" />
            <Logo showWord={false} className="sm:hidden" />
          </a>
          {/* Fluid path back to the umbrella brand — keeps the product from
              feeling like a dead end and reinforces the Viglet portfolio. */}
          <a
            href={LINKS.viglet}
            className="hidden items-center gap-1 rounded-full border border-border/60 px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:border-border hover:text-foreground sm:inline-flex"
          >
            part of viglet.org
            <ArrowUpRight className="size-3" />
          </a>
        </div>
        <nav className="ml-4 hidden items-center gap-5 lg:flex xl:ml-6 xl:gap-6">
          {NAV_LINKS.map((item) => (
            <a
              key={item.label}
              href={item.href}
              className="text-sm font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground"
            >
              {item.label}
            </a>
          ))}
          {/* Headline solution — pulled out of the dropdown and highlighted. */}
          <a
            href="/aem-search"
            className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-sm font-bold whitespace-nowrap text-primary transition-colors hover:border-primary/50 hover:bg-primary/15"
          >
            <Sparkles className="size-3.5" />
            AEM AI Search
          </a>
          {NAV_GROUPS.map((group) => (
            <DropdownMenu key={group.label}>
              <DropdownMenuTrigger className="group flex items-center gap-1 text-sm font-semibold whitespace-nowrap text-muted-foreground outline-hidden transition-colors hover:text-foreground data-[state=open]:text-foreground">
                {group.label}
                <ChevronDown className="size-3.5 transition-transform group-data-[state=open]:rotate-180" />
              </DropdownMenuTrigger>
              {/* Prevent Radix from returning focus to the trigger on close:
                  its trigger.focus() scrolls the (top-of-page) trigger back
                  into view, which cancels the native hash scroll to the target
                  section when clicking a "/#…" item from the landing page
                  itself. Cross-page items full-load so they're unaffected. */}
              <DropdownMenuContent
                align="start"
                className="min-w-44"
                onCloseAutoFocus={(e) => e.preventDefault()}
              >
                {group.items.map((item) => (
                  <DropdownMenuItem key={item.label} asChild>
                    <a href={item.href} className="w-full cursor-pointer font-medium">
                      {item.label}
                    </a>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <Button asChild size="sm">
            <a href={LINKS.docs}>
              Get started
              <ArrowRight className="size-4" />
            </a>
          </Button>
          <ModeToggle />
          {/* Mobile/tablet menu trigger — the desktop <nav> is hidden below lg,
              so without this there'd be no way to reach Docs/AEM/Product/Solutions. */}
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            aria-expanded={menuOpen}
            className="flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </Container>

      {/* Mobile/tablet nav panel */}
      {menuOpen && (
        <nav className="border-t border-border bg-background/95 backdrop-blur-md lg:hidden">
          <Container className="flex flex-col py-3">
            {NAV_LINKS.map((item) => (
              <a
                key={item.label}
                href={item.href}
                onClick={closeMenu}
                className="rounded-lg px-2 py-2.5 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
              >
                {item.label}
              </a>
            ))}
            <a
              href="/aem-search"
              onClick={closeMenu}
              className="mt-1 inline-flex items-center gap-1.5 rounded-lg bg-primary/10 px-2 py-2.5 text-sm font-bold text-primary transition-colors hover:bg-primary/15"
            >
              <Sparkles className="size-4" />
              AEM AI Search
            </a>
            {NAV_GROUPS.map((group) => (
              <div key={group.label} className="mt-2 border-t border-border/60 pt-2">
                <p className="px-2 py-1 text-[0.7rem] font-bold tracking-wide text-muted-foreground/70 uppercase">
                  {group.label}
                </p>
                {group.items.map((item) => (
                  <a
                    key={item.label}
                    href={item.href}
                    onClick={closeMenu}
                    className="block rounded-lg px-2 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                  >
                    {item.label}
                  </a>
                ))}
              </div>
            ))}
            <a
              href={LINKS.viglet}
              onClick={closeMenu}
              className="mt-3 inline-flex w-fit items-center gap-1 px-2 text-xs font-semibold text-muted-foreground transition-colors hover:text-foreground"
            >
              part of viglet.org
              <ArrowUpRight className="size-3" />
            </a>
          </Container>
        </nav>
      )}
    </header>
  );
}
