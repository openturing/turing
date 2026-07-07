import { Button } from "@/components/ui/button";
import { formatPrice } from "@/lib/format";
import { IconBuildingStore, IconCheck, IconExternalLink, IconShoppingCartPlus, IconSparkles } from "@tabler/icons-react";
import { createHostActions } from "@viglet/turing-sdk";
import { useMemo, useState } from "react";

/**
 * T450 — Embeddable action widget on a MOCK third-party host page.
 *
 * This page is NOT the Atlas storefront — it's a fake partner blog with its own
 * cart. It wires `createHostActions({ onAddToCart })` from the zero-dep vanilla
 * `@viglet/turing-sdk` and spreads the resulting `clientTools` into a chat
 * controller (`createChatController({ clientTools })`) so the embedded Atlas
 * agent can ACT on this page — add to the host's cart — not just talk. Turing
 * becomes an action layer over any website, no browser extension.
 *
 * The live agent drives these tools over SSE against a Turing backend; here we
 * invoke the same `add_to_cart` handler directly so the wiring is demonstrable
 * fully offline.
 */
const FEATURED = {
  productId: "ATL-0007-SON",
  title: "Sondr Orbit Pro Headphones",
  price: 199.0,
  currency: "USD",
};

export default function EmbedDemoPage() {
  const [hostCart, setHostCart] = useState<{ productId: string; quantity: number }[]>([]);
  const [lastAction, setLastAction] = useState<string | null>(null);

  // Host-defined actions the embedded agent may call (T450).
  const host = useMemo(
    () =>
      createHostActions({
        onAddToCart: ({ productId, quantity }: { productId: string; quantity: number }) => {
          setHostCart((prev) => [...prev, { productId, quantity }]);
          setLastAction(`Agent added ${quantity}× ${productId} to your cart`);
          return { added: productId, quantity };
        },
      }),
    []
  );

  // In production: createChatController({ baseUrl, site, clientTools: host.clientTools }).
  const simulateAgentAddToCart = () => {
    void host.clientTools.add_to_cart({ productId: FEATURED.productId, quantity: 1 });
  };

  const count = hostCart.reduce((n, i) => n + i.quantity, 0);

  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900 dark:bg-neutral-950 dark:text-neutral-100">
      {/* Mock partner site header */}
      <header className="border-b border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-900">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
          <span className="text-lg font-bold tracking-tight">The Gadget Journal</span>
          <span className="relative inline-flex items-center gap-1 text-sm text-neutral-500">
            <IconShoppingCartPlus className="size-5" />
            {count > 0 && <span className="font-semibold text-neutral-900 dark:text-neutral-100">{count}</span>}
          </span>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-10">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-indigo-600">Review</p>
        <h1 className="mb-4 text-3xl font-bold tracking-tight">
          The {FEATURED.title} are the best value cans of the year
        </h1>
        <p className="mb-4 leading-relaxed text-neutral-600 dark:text-neutral-300">
          This is a mock third-party article — not the Atlas storefront. It embeds the Atlas action
          widget so a reader can add the product to <em>this site's</em> cart without leaving the
          page. The widget is powered by the zero-dependency vanilla{" "}
          <code className="rounded bg-neutral-200 px-1 dark:bg-neutral-800">@viglet/turing-sdk</code>{" "}
          and host actions you wire yourself.
        </p>

        <div className="my-6 flex items-center gap-4 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-900">
          <div className="grid size-12 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-indigo-700 text-white">
            <IconBuildingStore className="size-6" />
          </div>
          <div className="flex-1">
            <p className="font-semibold">{FEATURED.title}</p>
            <p className="text-sm text-neutral-500">{formatPrice(FEATURED.price, FEATURED.currency)}</p>
          </div>
          <Button onClick={simulateAgentAddToCart} className="gap-1.5">
            <IconShoppingCartPlus className="size-4" /> Add to cart
          </Button>
        </div>

        <div className="rounded-xl border border-indigo-200 bg-indigo-50 p-4 dark:border-indigo-900/50 dark:bg-indigo-950/30">
          <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-indigo-700 dark:text-indigo-300">
            <IconSparkles className="size-4" /> Embedded Atlas agent
          </p>
          <p className="mb-3 text-sm text-neutral-600 dark:text-neutral-300">
            The agent calls host-defined client tools (<code>add_to_cart</code>, <code>navigate</code>,{" "}
            <code>fill_form</code>, <code>click_element</code>) via{" "}
            <code>createHostActions(&#123; onAddToCart &#125;)</code>. Click to simulate the agent
            acting on this page:
          </p>
          <Button variant="outline" size="sm" onClick={simulateAgentAddToCart} className="gap-1.5">
            <IconSparkles className="size-4" /> Simulate: agent adds it to your cart
          </Button>
          {lastAction && (
            <p className="mt-3 flex items-center gap-1.5 text-sm text-emerald-700 dark:text-emerald-400">
              <IconCheck className="size-4" /> {lastAction}
            </p>
          )}
        </div>

        <a
          href="#/"
          className="mt-8 inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline"
        >
          <IconExternalLink className="size-4" /> Back to the Atlas storefront
        </a>
      </main>
    </div>
  );
}
