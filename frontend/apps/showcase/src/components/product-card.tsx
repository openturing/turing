import { Stars } from "@/components/stars";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { firstValue, formatPrice, toNumber } from "@/lib/format";
import { IconTag } from "@tabler/icons-react";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";

const availabilityStyle: Record<string, string> = {
  "In Stock": "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30",
  "Low Stock": "bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30",
  "Out of Stock": "bg-red-500/15 text-red-700 dark:text-red-300 border-red-500/30",
  "Pre-order": "bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border-indigo-500/30",
  Backorder: "bg-slate-500/15 text-slate-700 dark:text-slate-300 border-slate-500/30",
};

export function ProductCard({
  doc,
  onClick,
}: Readonly<{ doc: ResolvedDocument; onClick: () => void }>) {
  const f = doc.raw.fields;
  const brand = firstValue(f.brand);
  const category = firstValue(f.subcategory) || firstValue(f.category);
  const currency = firstValue(f.currency) || "USD";
  const price = toNumber(f.price_amount) ?? toNumber(f.price);
  const listPrice = toNumber(f.list_price_amount) ?? toNumber(f.list_price);
  const onSale = firstValue(f.on_sale) === "true" || f.on_sale === true;
  const rating = toNumber(f.rating);
  const reviews = toNumber(f.review_count) ?? 0;
  const availability = firstValue(f.availability);

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/60 bg-card text-left transition-all duration-300 hover:border-primary/50 hover:shadow-lg hover:shadow-primary/10 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {doc.image && (
        <div className="relative aspect-square overflow-hidden bg-muted/30">
          <img
            src={doc.image}
            alt={doc.title}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
          {onSale && (
            <span className="absolute left-2 top-2 inline-flex items-center gap-1 rounded-full bg-accent px-2 py-0.5 text-[10px] font-bold text-accent-foreground shadow">
              <IconTag className="size-3" /> SALE
            </span>
          )}
          {availability && (
            <span
              className={cn(
                "absolute right-2 top-2 rounded-full border px-2 py-0.5 text-[10px] font-medium backdrop-blur-sm",
                availabilityStyle[availability] ??
                  "bg-slate-500/15 text-slate-600 border-slate-500/30"
              )}
            >
              {availability}
            </span>
          )}
        </div>
      )}

      <div className="flex flex-1 flex-col gap-1.5 p-4">
        <div className="flex items-center justify-between text-[11px] uppercase tracking-wide text-muted-foreground">
          <span className="font-semibold text-primary/80">{brand}</span>
          <span>{category}</span>
        </div>

        <h3
          className="line-clamp-2 text-sm font-semibold leading-snug group-hover:text-primary transition-colors"
          dangerouslySetInnerHTML={{ __html: firstValue(f.title) || doc.title }}
        />

        {rating != null && (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Stars rating={rating} />
            <span>
              {rating.toFixed(1)} · {reviews.toLocaleString()} reviews
            </span>
          </div>
        )}

        <div className="mt-auto flex items-end gap-2 pt-2">
          <span className="text-lg font-bold tracking-tight">
            {formatPrice(price, currency)}
          </span>
          {onSale && listPrice != null && listPrice > (price ?? 0) && (
            <span className="text-xs text-muted-foreground line-through">
              {formatPrice(listPrice, currency)}
            </span>
          )}
        </div>

        {Array.isArray(f.color) && f.color.length > 0 && (
          <div className="flex flex-wrap gap-1 pt-1">
            {f.color.slice(0, 3).map((c: string) => (
              <Badge key={c} variant="secondary" className="h-5 px-1.5 text-[10px]">
                {c}
              </Badge>
            ))}
          </div>
        )}
      </div>
    </button>
  );
}
