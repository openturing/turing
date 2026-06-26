import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ModeToggle } from "@/components/mode-toggle";
import { Container, GitHubIcon, Logo, LINKS } from "@/components/brand";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { label: "Capabilities", href: "#features" },
  { label: "How it works", href: "#how" },
  { label: "Developers", href: "#developers" },
  { label: "Marketplace", href: "#marketplace" },
  { label: "Docs", href: LINKS.docs },
];

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

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
        <a href="#top" className="text-base">
          <Logo />
        </a>
        <nav className="ml-6 hidden items-center gap-6 md:flex">
          {NAV_ITEMS.map((item) => (
            <a
              key={item.label}
              href={item.href}
              className="text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground"
            >
              {item.label}
            </a>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <Button asChild variant="outline" size="sm" className="hidden sm:inline-flex">
            <a href={LINKS.github} target="_blank" rel="noreferrer">
              <GitHubIcon className="size-4" />
              Star
            </a>
          </Button>
          <Button asChild size="sm">
            <a href={LINKS.docs}>
              <Star className="size-4" />
              Get started
            </a>
          </Button>
          <ModeToggle />
        </div>
      </Container>
    </header>
  );
}
