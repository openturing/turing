import { Skeleton } from "@/components/ui/skeleton";
import { IconAlertTriangleFilled } from "@tabler/icons-react";
import { BlankSlate } from "./blank-slate";

interface LoadProviderProps {
    checkIsNotUndefined?: any;
    error?: string | null;
    tryAgainUrl?: string;
    children: React.ReactNode;
}

export const LoadProvider = ({
    checkIsNotUndefined,
    error,
    tryAgainUrl,
    children
}: LoadProviderProps) => {
    if (error) {
        return (
            <BlankSlate
                icon={IconAlertTriangleFilled}
                title="Error while loading data"
                description={error}
                buttonText="Try again"
                urlNew={tryAgainUrl}
            />
        );
    }

    if (checkIsNotUndefined === undefined && !error) {
        return (
            <div className="space-y-6 py-8 px-6 animate-pulse">
                <Skeleton className="h-8 w-1/3 rounded-md" />
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {["skeleton-1", "skeleton-2", "skeleton-3"].map((key) => (
                        <div key={key} className="space-y-3 p-4 border rounded-xl bg-slate-50/50 dark:bg-slate-900/20">
                            <Skeleton className="h-5 w-2/3" />
                            <Skeleton className="h-12 w-full" />
                            <Skeleton className="h-4 w-5/6" />
                        </div>
                    ))}
                </div>
                <div className="pt-4">
                    <Skeleton className="h-10 w-32 rounded-lg" />
                </div>
            </div>
        );
    }
    return <>{children}</>;
};