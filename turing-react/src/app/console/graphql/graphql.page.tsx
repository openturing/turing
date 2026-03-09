import { useTheme } from "@/components/theme-provider"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useEffect, useRef } from "react"

export default function GraphiQLPage() {
    const { pushItem, popItem } = useBreadcrumb()
    const { theme } = useTheme()
    const iframeRef = useRef<HTMLIFrameElement>(null)

    useEffect(() => {
        pushItem({ label: "GraphQL Explorer" })
        return () => {
            popItem()
        }
    }, [])

    useEffect(() => {
        if (!iframeRef.current || !iframeRef.current.contentWindow) return;

        let resolvedTheme = theme;
        if (theme === "system") {
            resolvedTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        }

        try {
            iframeRef.current.contentWindow.localStorage.setItem("graphiql:theme", resolvedTheme);
            // We tell the iframe to reload so GraphiQL reads from localStorage and updates the theme
            iframeRef.current.contentWindow.location.reload();
        } catch (e) {
            console.error("Could not set GraphiQL theme on iframe localStorage", e);
        }
    }, [theme])

    return (
        <div className="flex flex-1 flex-col h-[calc(100vh-theme(spacing.16))] w-full">
            <iframe
                ref={iframeRef}
                src="/graphiql"
                className="h-full w-full border-0 flex-1"
                title="GraphiQL Explorer"
            />
        </div>
    )
}
