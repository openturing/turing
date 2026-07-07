import { ArrowUpRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import type { LinkCard } from "@/lib/site-content";

function isExternal(href: string) {
  return href.startsWith("http");
}

export function LinkCards({ items }: { items: LinkCard[] }) {
  return (
    <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <a
          key={item.title}
          href={item.href}
          target={isExternal(item.href) ? "_blank" : undefined}
          rel={isExternal(item.href) ? "noreferrer" : undefined}
          className="group block"
        >
          <Card className="h-full gap-0 p-6 transition-all duration-200 group-hover:-translate-y-1.5 group-hover:border-primary/60 group-hover:shadow-xl group-hover:shadow-primary/10">
            <div className="flex items-start justify-between gap-3">
              <h3 className="text-lg font-bold">{item.title}</h3>
              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-primary" />
            </div>
            <p className="mt-1.5 flex-1 text-sm leading-relaxed text-muted-foreground">
              {item.description}
            </p>
            <span className="mt-4 w-fit rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
              {item.tag}
            </span>
          </Card>
        </a>
      ))}
    </div>
  );
}
