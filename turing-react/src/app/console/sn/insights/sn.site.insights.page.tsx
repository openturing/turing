import { SubPageHeader } from "@/components/sub.page.header";
import { Card, CardContent } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Skeleton } from "@/components/ui/skeleton";
import { IconLoader2, IconRefresh, IconSparkles } from "@tabler/icons-react";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import axios from "axios";

interface SummaryResponse {
    success: boolean;
    error: string | null;
    content: string | null;
    canRegenerate: boolean;
}

export default function SNSiteSummaryPage() {
    const { id } = useParams() as { id: string };
    useSubPageBreadcrumb("AI Insights");

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
                `/sn/${id}/summary`,
                { params: regenerate ? { regenerate: true } : {} }
            );

            if (response.data.success && response.data.content) {
                setContent(response.data.content);
            } else {
                setError(response.data.error ?? "Failed to generate summary.");
            }
            setCanRegenerate(response.data.canRegenerate ?? false);
        } catch {
            setError("Failed to connect to the summary service. Please try again.");
        } finally {
            setIsLoading(false);
        }
    }, [id]);

    useEffect(() => {
        fetchSummary();
    }, [fetchSummary]);

    return (
        <>
            <SubPageHeader
                icon={IconSparkles}
                name="AI Insights"
                feature="AI Insights"
                description="AI-powered overview and suggestions for this Semantic Navigation site."
            />
            <div className="px-6 py-6 space-y-4">
                {isLoading && (
                    <Card>
                        <CardContent className="p-6 space-y-4">
                            <div className="flex items-center gap-2 text-muted-foreground">
                                <IconLoader2 className="size-4 animate-spin" />
                                <span className="text-sm">Generating AI summary...</span>
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
                            Regenerate
                        </GradientButton>
                    </div>
                )}
            </div>
        </>
    );
}
