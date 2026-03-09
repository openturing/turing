import { Badge } from "@/components/ui/badge";
import { getHashedColor } from "@/lib/utils";
import React from "react";

interface BadgeColorfulProps {
    text: string;
    href?: string;
    onClick?: (href: string) => void;
    className?: string;
    prefix?: React.ReactNode;
}

export const BadgeColorful: React.FC<BadgeColorfulProps> = ({
    text,
    href,
    onClick,
    className,
    prefix,
}) => {
    if (!text) return null;

    const colors = getHashedColor(text);

    return (
        <>
            <Badge
                variant="outline"
                title={text}
                onClick={() => href && onClick?.(href)}
                className={`text-xs font-medium px-2 py-0.5 gap-1.5 cursor-pointer transition-all hover:opacity-80 dynamic-badge-${text.length} ${className ?? ""}`}
                style={{
                    // Variáveis CSS locais para este badge específico
                    "--badge-bg": colors.light.bg,
                    "--badge-text": colors.light.text,
                    "--badge-border": colors.light.border,
                    "--badge-dark-bg": colors.dark.bg,
                    "--badge-dark-text": colors.dark.text,
                    "--badge-dark-border": colors.dark.border,

                    backgroundColor: "var(--badge-bg)",
                    color: "var(--badge-text)",
                    borderColor: "var(--badge-border)",
                } as React.CSSProperties}
            >
                {prefix}
                <span dangerouslySetInnerHTML={{ __html: text }} />
            </Badge>

            {/* Estilo global injetado apenas uma vez para lidar com dark mode via variáveis */}
            <style suppressHydrationWarning>{`
        .dark [title="${text}"] {
          background-color: var(--badge-dark-bg) !important;
          color: var(--badge-dark-text) !important;
          border-color: var(--badge-dark-border) !important;
        }
      `}</style>
        </>
    );
};
