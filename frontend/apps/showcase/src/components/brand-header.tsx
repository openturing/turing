import { IconBuildingStore } from "@tabler/icons-react";
import { CartDrawer } from "@/components/cart/cart-drawer";
import { ModeToggle } from "@/components/mode-toggle";

/**
 * Atlas Store top bar — the brand lockup shared across storefront and product
 * pages. Kept deliberately small; the storefront hero carries the headline.
 */
export function BrandHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/80 backdrop-blur-xl">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
        <a href="#/" className="flex items-center gap-2.5 group">
          <span className="grid size-8 place-items-center rounded-lg bg-linear-to-br from-primary to-indigo-700 text-primary-foreground shadow-sm">
            <IconBuildingStore className="size-5" />
          </span>
          <span className="flex flex-col leading-none">
            <span className="font-bold text-sm tracking-tight">Atlas Store</span>
            <span className="text-[10px] uppercase tracking-[0.18em] text-muted-foreground">
              Turing ES Showcase
            </span>
          </span>
        </a>
        <div className="flex items-center gap-1">
          <CartDrawer />
          <ModeToggle />
        </div>
      </div>
    </header>
  );
}
