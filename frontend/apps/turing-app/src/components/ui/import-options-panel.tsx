import type {
    TurAgentConflict,
    TurSiteConflict,
} from "@/models/marketplace/import-result.model";
import {
    IconAlertTriangle,
    IconBrowserCheck,
    IconDatabase,
    IconSettings2,
} from "@tabler/icons-react";

export interface ImportOptionsLabels {
    configAlwaysIncluded: string;
    checkingConflicts?: string;
    conflictWarningTitle: string;
    overwriteConfig: string;
    overwriteConfigOn: string;
    overwriteConfigOff: string;
    includeContent: string;
    includeContentDescription: string;
    includeTemplate: string;
    includeTemplateDescription: string;
    templateRequiresStorage: string;
}

interface ImportOptionsPanelProps {
    conflicts: TurSiteConflict[];
    /** Existing AI agents that the bundle would overwrite. @since 2026.2.8 */
    agentConflicts?: TurAgentConflict[];
    conflictsLoading?: boolean;
    hasContent: boolean;
    hasTemplate: boolean;
    storageEnabled: boolean;
    values: {
        includeContent: boolean;
        includeTemplate: boolean;
        overwrite: boolean;
    };
    onChange: {
        setIncludeContent: (v: boolean) => void;
        setIncludeTemplate: (v: boolean) => void;
        setOverwrite: (v: boolean) => void;
    };
    labels: ImportOptionsLabels;
    disabled?: boolean;
}

function OptionCard({
    icon,
    title,
    description,
    checked,
    onChange,
    disabled,
    hint,
}: Readonly<{
    icon: React.ReactNode;
    title: string;
    description: string;
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
    hint?: string;
}>) {
    return (
        <button
            type="button"
            disabled={disabled}
            onClick={() => !disabled && onChange(!checked)}
            className={`flex items-start gap-3 w-full rounded-lg border p-3.5 text-left transition-all ${
                disabled
                    ? "opacity-50 cursor-not-allowed border-border bg-muted/30"
                    : checked
                        ? "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                        : "border-border hover:border-primary/30 hover:bg-accent/50 cursor-pointer"
            }`}
        >
            <div
                className={`mt-0.5 shrink-0 flex items-center justify-center size-8 rounded-md ${
                    checked && !disabled
                        ? "bg-primary/15 text-primary"
                        : "bg-muted text-muted-foreground"
                }`}
            >
                {icon}
            </div>
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{title}</span>
                    {checked && !disabled && (
                        <span className="inline-flex items-center rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-semibold text-primary">
                            ON
                        </span>
                    )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                    {description}
                </p>
                {hint && (
                    <p className="text-[11px] text-amber-600 dark:text-amber-400 mt-1">
                        {hint}
                    </p>
                )}
            </div>
        </button>
    );
}

export function ImportOptionsPanel({
    conflicts,
    agentConflicts = [],
    conflictsLoading,
    hasContent,
    hasTemplate,
    storageEnabled,
    values,
    onChange,
    labels,
    disabled,
}: Readonly<ImportOptionsPanelProps>) {
    const hasConflicts = conflicts.length > 0 || agentConflicts.length > 0;
    const canIncludeTemplate = hasTemplate && storageEnabled;

    return (
        <>
            {hasConflicts ? (
                <div className="space-y-2.5">
                    <div className="flex items-start gap-2.5 rounded-lg bg-amber-500/10 border border-amber-500/30 px-3 py-2.5">
                        <IconAlertTriangle className="size-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                        <div className="flex-1 min-w-0 text-xs text-foreground/80">
                            <div className="font-medium">{labels.conflictWarningTitle}</div>
                            <ul className="mt-1 space-y-0.5 text-muted-foreground">
                                {conflicts.map((c) => (
                                    <li key={`site-${c.id}`} className="truncate">• {c.name}</li>
                                ))}
                                {agentConflicts.map((c) => (
                                    <li key={`agent-${c.id}`} className="truncate">• {c.name} <span className="text-[10px] opacity-60">(agent)</span></li>
                                ))}
                            </ul>
                        </div>
                    </div>
                    <OptionCard
                        icon={<IconSettings2 className="size-4" />}
                        title={labels.overwriteConfig}
                        description={
                            values.overwrite
                                ? labels.overwriteConfigOn
                                : labels.overwriteConfigOff
                        }
                        checked={values.overwrite}
                        onChange={onChange.setOverwrite}
                        disabled={disabled}
                    />
                </div>
            ) : (
                <div className="flex items-center gap-2.5 rounded-lg bg-primary/5 border border-primary/20 px-3 py-2.5">
                    <IconSettings2 className="size-4 text-primary shrink-0" />
                    <span className="text-sm text-foreground/80">
                        {conflictsLoading && labels.checkingConflicts
                            ? labels.checkingConflicts
                            : labels.configAlwaysIncluded}
                    </span>
                </div>
            )}

            {(hasContent || hasTemplate) && (
                <div className="space-y-2.5">
                    {hasContent && (
                        <OptionCard
                            icon={<IconDatabase className="size-4" />}
                            title={labels.includeContent}
                            description={labels.includeContentDescription}
                            checked={values.includeContent}
                            onChange={onChange.setIncludeContent}
                            disabled={disabled}
                        />
                    )}
                    {hasTemplate && (
                        <OptionCard
                            icon={<IconBrowserCheck className="size-4" />}
                            title={labels.includeTemplate}
                            description={labels.includeTemplateDescription}
                            checked={values.includeTemplate && canIncludeTemplate}
                            onChange={onChange.setIncludeTemplate}
                            disabled={disabled || !canIncludeTemplate}
                            hint={!storageEnabled ? labels.templateRequiresStorage : undefined}
                        />
                    )}
                </div>
            )}
        </>
    );
}
