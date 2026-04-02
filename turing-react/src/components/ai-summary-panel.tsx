import { Card, CardContent } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Skeleton } from "@/components/ui/skeleton";
import { IconLoader2, IconRefresh } from "@tabler/icons-react";
import axios from "axios";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";

export interface SummaryResponse {
    success: boolean;
    error: string | null;
    content: string | null;
    canRegenerate: boolean;
}

interface AiSummaryPanelProps {
    endpoint: string;
    i18nPrefix: string;
}

export function AiSummaryPanel({ endpoint, i18nPrefix }: Readonly<AiSummaryPanelProps>) {
    const { t } = useTranslation();
    const [content, setContent] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [canRegenerate, setCanRegenerate] = useState(false);

    const fetchSummary = useCallback(async (regenerate = false) => {
        setIsLoading(true);
        setError(null);
        setContent(null);
        try {
            const response = await axios.get<SummaryResponse>(
                endpoint,
                { params: regenerate ? { regenerate: true } : {} }
            );

            if (response.data.success && response.data.content) {
                setContent(response.data.content);
            } else {
                setError(response.data.error ?? t(`${i18nPrefix}.failedGenerate`));
            }
            setCanRegenerate(response.data.canRegenerate ?? false);
        } catch {
            setError(t(`${i18nPrefix}.failedConnect`));
        } finally {
            setIsLoading(false);
        }
    }, [endpoint, i18nPrefix, t]);

    useEffect(() => {
        fetchSummary();
    }, [fetchSummary]);

    return (
        <div className="px-6 py-6 space-y-4">
            {isLoading && (
                <Card>
                    <CardContent className="p-6 space-y-4">
                        <div className="flex items-center gap-2 text-muted-foreground">
                            <IconLoader2 className="size-4 animate-spin" />
                            <span className="text-sm">{t(`${i18nPrefix}.generating`)}</span>
                        </div>
                        <Skeleton className="h-4 w-3/4" />
                        <Skeleton className="h-4 w-full" />
                        <Skeleton className="h-4 w-5/6" />
                        <Skeleton className="h-4 w-2/3" />
                        <Skeleton className="h-4 w-full" />
                        <Skeleton className="h-4 w-4/5" />
                    </CardContent>
                </Card>
            )}

            {error && !isLoading && (
                <Card>
                    <CardContent className="p-6">
                        <p className="text-sm text-destructive">{error}</p>
                    </CardContent>
                </Card>
            )}

            {content && !isLoading && (
                <Card>
                    <CardContent className="p-6">
                        <div className="prose prose-sm dark:prose-invert max-w-none">
                            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
                                {content}
                            </ReactMarkdown>
                        </div>
                    </CardContent>
                </Card>
            )}

            {!isLoading && canRegenerate && (
                <div className="flex justify-end">
                    <GradientButton
                        type="button"
                        variant="outline"
                        onClick={() => fetchSummary(true)}
                    >
                        <IconRefresh className="size-4" />
                        {t(`${i18nPrefix}.regenerate`)}
                    </GradientButton>
                </div>
            )}
        </div>
    );
}
