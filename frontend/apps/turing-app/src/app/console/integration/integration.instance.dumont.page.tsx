import { loadRemote } from "@module-federation/runtime";
import { Component, lazy, Suspense, useEffect, useState, type ErrorInfo, type ReactNode } from "react";

const RemoteDumontRoutes = lazy(() =>
    loadRemote("dumont_react/DumontRoutes").then((mod) => ({
        default: (mod as { default: React.ComponentType }).default,
    }))
);

class DumontErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
    state = { error: null as Error | null };
    static getDerivedStateFromError(error: Error) { return { error }; }
    componentDidCatch(error: Error, info: ErrorInfo) { console.error("[DumontRemote]", error, info.componentStack); }
    render() {
        if (this.state.error) {
            return (
                <div className="p-6 text-sm">
                    <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-4">
                        <p className="font-semibold text-destructive">Dumont module error</p>
                        <pre className="mt-2 text-xs text-muted-foreground whitespace-pre-wrap">{this.state.error.message}</pre>
                    </div>
                </div>
            );
        }
        return this.props.children;
    }
}

export default function IntegrationInstanceDumontPage() {
    const [ready, setReady] = useState(false);

    useEffect(() => {
        setReady(true);
    }, []);

    if (!ready) return null;

    return (
        <DumontErrorBoundary>
            <Suspense
                fallback={
                    <div className="flex items-center justify-center py-12 text-muted-foreground">
                        Loading Dumont module...
                    </div>
                }
            >
                <RemoteDumontRoutes />
            </Suspense>
        </DumontErrorBoundary>
    );
}
