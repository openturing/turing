import { BrandHeader } from "@/components/brand-header";
import { Stars } from "@/components/stars";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { useCart } from "@/contexts/cart";
import { resolveSiteName } from "@/lib/resolve-site";
import { firstValue, formatPrice, toNumber } from "@/lib/format";
import {
  IconArrowLeft,
  IconShoppingCartPlus,
  IconSparkles,
  IconTag,
} from "@tabler/icons-react";
import {
  TuringProvider,
  useTuringSimilar,
  type ResolvedDocument,
} from "@viglet/turing-react-sdk";
import { useLocation, useNavigate } from "react-router-dom";

function AttrChips({ label, values }: Readonly<{ label: string; values: unknown }>) {
  const arr = Array.isArray(values) ? values : values ? [String(values)] : [];
  if (!arr.length) return null;
  return (
    <div className="space-y-1">
      <span className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <div className="flex flex-wrap gap-1.5">
        {arr.map((v) => (
          <Badge key={String(v)} variant="secondary" className="text-xs">
            {String(v)}
          </Badge>
        ))}
      </div>
    </div>
  );
}

/* ── "You may also like" rail — T400 more-like-this / vector similar ── */
function SimilarRail({ documentId }: Readonly<{ documentId: string }>) {
  const { results, loading } = useTuringSimilar({ documentId, rows: 6 });
  if (loading || results.length === 0) return null;
  return (
    <section className="mt-12">
      <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
        <IconSparkles className="size-4 text-primary" />
        You may also like
      </h2>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {results.map((r) => (
          <a
            key={r.id}
            href={r.url}
            className="rounded-lg border border-border/60 bg-card p-3 text-sm transition-colors hover:border-primary/50"
          >
            <span className="line-clamp-3 font-medium leading-snug">{r.title}</span>
            <span className="mt-1 block text-[11px] uppercase tracking-wide text-muted-foreground">
              {r.type}
            </span>
          </a>
        ))}
      </div>
    </section>
  );
}

function ProductDetail({ doc }: Readonly<{ doc: ResolvedDocument }>) {
  const navigate = useNavigate();
  const cart = useCart();
  const f = doc.raw.fields;
  const currency = firstValue(f.currency) || "USD";
  const price = toNumber(f.price_amount) ?? toNumber(f.price);
  const listPrice = toNumber(f.list_price_amount) ?? toNumber(f.list_price);
  const onSale = firstValue(f.on_sale) === "true" || f.on_sale === true;
  const rating = toNumber(f.rating);
  const reviews = toNumber(f.review_count) ?? 0;
  const availability = firstValue(f.availability);
  const brand = firstValue(f.brand);
  const category = firstValue(f.category);
  const sub = firstValue(f.subcategory);
  const documentId = firstValue(f.id);

  return (
    <div className="flex min-h-screen flex-col">
      <BrandHeader />
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">
        <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="mb-6 gap-1.5">
          <IconArrowLeft className="size-4" /> Back
        </Button>

        <div className="grid gap-8 lg:grid-cols-2">
          <div className="overflow-hidden rounded-2xl border border-border/60 bg-muted/20">
            {doc.image && (
              <img src={doc.image} alt={doc.title} className="aspect-square w-full object-cover" />
            )}
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-2 text-xs uppercase tracking-wide text-muted-foreground">
              <span className="font-semibold text-primary">{brand}</span>
              {category && <span>· {category}</span>}
              {sub && <span>· {sub}</span>}
            </div>

            <h1
              className="text-2xl font-bold leading-tight sm:text-3xl"
              dangerouslySetInnerHTML={{ __html: firstValue(f.title) || doc.title }}
            />

            {rating != null && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Stars rating={rating} className="size-4" />
                <span>
                  {rating.toFixed(1)} · {reviews.toLocaleString()} reviews
                </span>
              </div>
            )}

            <div className="flex items-end gap-3">
              <span className="text-3xl font-bold tracking-tight">
                {formatPrice(price, currency)}
              </span>
              {onSale && listPrice != null && listPrice > (price ?? 0) && (
                <>
                  <span className="text-base text-muted-foreground line-through">
                    {formatPrice(listPrice, currency)}
                  </span>
                  <Badge className="bg-accent text-accent-foreground">
                    <IconTag className="mr-1 size-3" /> Sale
                  </Badge>
                </>
              )}
            </div>

            {availability && (
              <p className="text-sm">
                Availability: <span className="font-medium">{availability}</span>
              </p>
            )}

            {doc.description && (
              <p
                className="text-sm leading-relaxed text-muted-foreground"
                dangerouslySetInnerHTML={{ __html: doc.description }}
              />
            )}

            <Button
              size="lg"
              className="mt-2 w-full gap-2 sm:w-auto"
              disabled={availability === "Out of Stock"}
              onClick={() =>
                cart.add({
                  sku: firstValue(f.sku) || documentId,
                  title: firstValue(f.title) || doc.title,
                  price: price ?? 0,
                  currency,
                  image: doc.image,
                })
              }
            >
              <IconShoppingCartPlus className="size-5" />
              {availability === "Out of Stock" ? "Out of stock" : "Add to cart"}
            </Button>

            <Separator className="my-2" />

            <div className="grid grid-cols-2 gap-4">
              <AttrChips label="Colors" values={f.color} />
              <AttrChips label="Materials" values={f.material} />
              <AttrChips label="Features" values={f.tags} />
              {firstValue(f.sku) && (
                <div className="space-y-1">
                  <span className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                    SKU
                  </span>
                  <p className="text-xs">{firstValue(f.sku)}</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {firstValue(f.text) && (
          <section className="mt-10 max-w-3xl">
            <h2 className="mb-2 text-lg font-semibold">Product details</h2>
            <p className="text-sm leading-relaxed text-muted-foreground">{firstValue(f.text)}</p>
          </section>
        )}

        {documentId && <SimilarRail documentId={documentId} />}
      </main>
    </div>
  );
}

export default function ProductPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const site = resolveSiteName();
  const doc = (location.state as { document?: ResolvedDocument } | null)?.document;

  if (!doc) {
    return (
      <div className="flex min-h-screen flex-col">
        <BrandHeader />
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <p className="text-sm text-muted-foreground">No product selected.</p>
          <Button onClick={() => navigate("/")}>Back to store</Button>
        </div>
      </div>
    );
  }

  return (
    <TuringProvider config={{ site, locale: import.meta.env.VITE_LOCALE }}>
      <ProductDetail doc={doc} />
    </TuringProvider>
  );
}
