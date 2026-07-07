import type { CartItem } from "@/contexts/cart";

/** A client-tool handler: receives the agent's JSON args, returns a result. */
type ClientToolHandler = (args: unknown) => unknown | Promise<unknown>;

/**
 * Builds the Atlas Store client tools (T438/T439) the agent can invoke on the
 * shopper's live page: `add_to_cart`, `get_cart`, `clear_cart`. These are the
 * frontend half of the client-tool protocol — the agent calls them over SSE and
 * the host runs them against the real cart state (impossible in a server-only
 * agent). `get_cart` also lets the code interpreter (T80) "compute my cart
 * total" against the actual basket.
 */
export function createCartClientTools(cart: {
  add: (item: Omit<CartItem, "qty">, qty?: number) => void;
  remove: (sku: string) => void;
  clear: () => void;
  items: CartItem[];
}): Record<string, ClientToolHandler> {
  return {
    add_to_cart: (args) => {
      const a = (args ?? {}) as Partial<CartItem> & { quantity?: number };
      if (!a.sku && !a.title) return { success: false, error: "sku or title is required" };
      cart.add(
        {
          sku: String(a.sku ?? a.title),
          title: String(a.title ?? a.sku),
          price: Number(a.price ?? 0),
          currency: String(a.currency ?? "USD"),
          image: a.image,
        },
        Number(a.quantity ?? 1)
      );
      return { success: true, added: a.sku ?? a.title };
    },
    get_cart: () => ({
      items: cart.items.map((i) => ({
        sku: i.sku,
        title: i.title,
        price: i.price,
        currency: i.currency,
        qty: i.qty,
      })),
      count: cart.items.reduce((n, i) => n + i.qty, 0),
      total: cart.items.reduce((s, i) => s + i.price * i.qty, 0),
    }),
    clear_cart: () => {
      cart.clear();
      return { success: true };
    },
  };
}
