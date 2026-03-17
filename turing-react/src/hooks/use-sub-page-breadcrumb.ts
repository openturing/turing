import { type BreadcrumbItem, useBreadcrumb } from "@/contexts/breadcrumb.context";
import { useEffect, useRef } from "react";

/**
 * Manages breadcrumb items for a sub-page, automatically pushing on mount
 * and popping on unmount.
 *
 * Accepts:
 * - A string: single static label → `useSubPageBreadcrumb("Settings")`
 * - A BreadcrumbItem: label + optional href → `useSubPageBreadcrumb({ label: "Settings", href: "/..." })`
 * - An array of BreadcrumbItem: multiple levels → `useSubPageBreadcrumb([{ label: "Users", href: "/..." }, { label: "admin" }])`
 * - `undefined`: deferred — items are pushed once the value becomes defined (useful for async data)
 *
 * @since 2026.1.14
 */
export function useSubPageBreadcrumb(
    items: string | BreadcrumbItem | BreadcrumbItem[] | undefined,
) {
    const { pushItem, popItem } = useBreadcrumb();
    const countRef = useRef(0);

    useEffect(() => {
        if (items === undefined || countRef.current > 0) return;

        const normalized: BreadcrumbItem[] =
            typeof items === "string"
                ? [{ label: items }]
                : Array.isArray(items)
                    ? items
                    : [items];

        for (const item of normalized) {
            pushItem(item);
        }
        countRef.current = normalized.length;
    }, [items, pushItem]);

    useEffect(() => {
        return () => {
            for (let i = 0; i < countRef.current; i++) {
                popItem();
            }
            countRef.current = 0;
        };
    }, [popItem]);
}
