import { Button } from "@/components/ui/button";
import { useCart } from "@/contexts/cart";
import { formatPrice } from "@/lib/format";
import { IconShoppingCart, IconTrash, IconX } from "@tabler/icons-react";
import { useState } from "react";

/** Cart button (with badge) + slide-over drawer. The agent fills this via the
 * `add_to_cart` client tool (T438); shoppers also add from the product page. */
export function CartDrawer() {
  const { items, count, total, remove, clear } = useCart();
  const [open, setOpen] = useState(false);
  const currency = items[0]?.currency ?? "USD";

  return (
    <>
      <button
        type="button"
        aria-label="Open cart"
        onClick={() => setOpen(true)}
        className="relative rounded-lg p-2 text-foreground hover:bg-accent"
      >
        <IconShoppingCart className="size-5" />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid min-w-4 place-items-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-foreground">
            {count}
          </span>
        )}
      </button>

      {open && (
        <>
          <button
            aria-label="Close cart"
            className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm"
            onClick={() => setOpen(false)}
          />
          <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-sm flex-col border-l border-border bg-background shadow-2xl">
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <span className="flex items-center gap-2 text-sm font-semibold">
                <IconShoppingCart className="size-4" /> Your cart ({count})
              </span>
              <button aria-label="Close cart" onClick={() => setOpen(false)} className="rounded-md p-1.5 hover:bg-accent">
                <IconX className="size-5" />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              {items.length === 0 ? (
                <p className="py-12 text-center text-sm text-muted-foreground">
                  Your cart is empty. Ask Atlas to add something, or browse the store.
                </p>
              ) : (
                <ul className="space-y-3">
                  {items.map((i) => (
                    <li key={i.sku} className="flex items-center gap-3">
                      {i.image && (
                        <img src={i.image} alt={i.title} className="size-12 rounded-md object-cover" />
                      )}
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{i.title}</p>
                        <p className="text-xs text-muted-foreground">
                          {i.qty} × {formatPrice(i.price, i.currency)}
                        </p>
                      </div>
                      <button
                        type="button"
                        aria-label={`Remove ${i.title}`}
                        onClick={() => remove(i.sku)}
                        className="rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-destructive"
                      >
                        <IconTrash className="size-4" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {items.length > 0 && (
              <div className="border-t border-border p-4">
                <div className="mb-3 flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Total</span>
                  <span className="text-lg font-bold">{formatPrice(total, currency)}</span>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={clear} className="flex-1">
                    Clear
                  </Button>
                  <Button size="sm" className="flex-1">
                    Checkout
                  </Button>
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </>
  );
}
