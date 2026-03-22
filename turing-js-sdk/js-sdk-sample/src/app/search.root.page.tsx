import { AppSidebar } from "@/components/app-sidebar";
import { ModeToggle } from "@/components/mode-toggle";
import { Separator } from "@/components/ui/separator";
import {
    SidebarInset,
    SidebarProvider, SidebarTrigger
} from "@/components/ui/sidebar";
import { TurSNSiteSearchService, type TurSNSiteSearchDocument } from "@viglet/turing-sdk";
import React, { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
const QUERY_PARAM = "q";
const ROWS_PARAM = "rows";
const PAGE_PARAM = "p";
const SORT_PARAM = "sort";
export default function SearchRootPage() {
    const [totalDocuments, setTotalDocuments] = useState(0);
    const [searchDocuments, setSearchDocuments] = useState<TurSNSiteSearchDocument[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [searchParams] = useSearchParams();
    const query = searchParams.get(QUERY_PARAM) || import.meta.env.VITE_SN_DEFAULT_QUERY;
    const rows = searchParams.get(ROWS_PARAM) || import.meta.env.VITE_SN_DEFAULT_ROWS;
    const currentPage = searchParams.get(PAGE_PARAM) || import.meta.env.VITE_SN_DEFAULT_PAGE;
    const sort = searchParams.get(SORT_PARAM) || import.meta.env.VITE_SN_DEFAULT_PAGE;
    const fetchDocuments = async () => {
        setLoading(true);
        setError(null);
        try {
            const searchService = new TurSNSiteSearchService(import.meta.env.VITE_API_URL);
            const res = await searchService.search(import.meta.env.VITE_SN_SITE, {
                q: query,
                rows: rows,
                currentPage: currentPage,
                sort: sort,
                localeRequest: import.meta.env.VITE_LOCALE,
            });
            setSearchDocuments(res.results?.document || []);
            setTotalDocuments(res.queryContext?.count || 0);
        } catch (err: any) {
            setError("Search failed: " + (err?.message || "Unknown error"));
        } finally {
            setLoading(false);
        }
    };
    useEffect(() => {
        await fetchDocuments();
    }, [query]);

    return (
        <SidebarProvider
            style={
                {
                    "--sidebar-width": "calc(var(--spacing) * 72)",
                    "--header-height": "calc(var(--spacing) * 12)",
                } as React.CSSProperties
            }
            className="min-h-svh"
        >
            <AppSidebar variant="inset" />
            <SidebarInset>
                <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
                    <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
                        <SidebarTrigger className="-ml-1" />
                        <Separator
                            orientation="vertical"
                            className="mx-2 data-[orientation=vertical]:h-4"
                        />
                        <h1 className="text-base font-medium"> Search</h1>
                        <div className="ml-auto flex items-center gap-2">
                            <ModeToggle />
                        </div>
                    </div>
                </header>
                <div className="flex min-h-svh flex-col items-center justify-center">

                    <div className="mb-4 text-lg font-semibold">
                        Total Documents: {totalDocuments}
                    </div>
                    {loading && <div className="mb-4 text-gray-500">Loading...</div>}
                    {error && <div className="mb-4 text-red-500">{error}</div>}
                    <ul className="w-full max-w-md divide-y divide-gray-200 rounded-md border border-gray-300 bg-white shadow">
                        {searchDocuments.map((doc, index) => (
                            <li key={doc.fields?.url || index} className="p-4">
                                <h3
                                    className="text-base font-medium"
                                    dangerouslySetInnerHTML={{ __html: doc.fields?.title || "" }}
                                />
                                {doc.fields?.url && (
                                    <a
                                        href={doc.fields.url}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-blue-600 underline break-all"
                                    >
                                        {doc.fields.url}
                                    </a>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            </SidebarInset>
        </SidebarProvider>
    )
}
