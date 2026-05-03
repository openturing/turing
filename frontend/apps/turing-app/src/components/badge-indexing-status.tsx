import { Badge } from "@/components/ui/badge";
import { IconBan, IconBolt, IconCircleArrowRight, IconCircleCheck, IconClock, IconDatabase, IconFileSearch, IconRefresh, IconSend } from "@tabler/icons-react";
interface BadgeIndexingStatusProps {
    status: string;
    className?: string;
}

const STATUS_CONFIG: Record<string, {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    className: string;
}> = {
    PREPARE_INDEX: {
        label: "Indexed",
        icon: IconFileSearch,
        className: "bg-blue-500/10 text-blue-600 border-blue-500/20",
    },
    PREPARE_UNCHANGED: {
        label: "Unchanged",
        icon: IconClock,
        className: "bg-slate-500/10 text-slate-600 border-slate-500/20",
    },
    PREPARE_REINDEX: {
        label: "Reindexed",
        icon: IconRefresh,
        className: "bg-cyan-500/10 text-cyan-600 border-cyan-500/20",
    },
    PREPARE_FORCED_REINDEX: {
        label: "Forced Reindexing",
        icon: IconBolt,
        className: "bg-indigo-500/10 text-indigo-600 border-indigo-500/20",
    },
    RECEIVED_AND_SENT_TO_TURING: {
        label: "Sent to Turing",
        icon: IconSend,
        className: "bg-purple-500/10 text-purple-600 border-purple-500/20",
    },
    SENT_TO_QUEUE: {
        label: "In Queue",
        icon: IconCircleArrowRight,
        className: "bg-orange-500/10 text-orange-600 border-orange-500/20",
    },
    RECEIVED_FROM_QUEUE: {
        label: "From Queue",
        icon: IconDatabase,
        className: "bg-amber-500/10 text-amber-600 border-amber-500/20",
    },
    INDEXED: {
        label: "Indexed",
        icon: IconCircleCheck,
        className: "bg-emerald-500/10 text-emerald-600 border-emerald-500/20",
    },
    FINISHED: {
        label: "Finished",
        icon: IconCircleCheck,
        className: "bg-green-500/10 text-green-600 border-green-500/20",
    },
    DEINDEXED: {
        label: "Deindexed",
        icon: IconBan,
        className: "bg-rose-500/10 text-rose-600 border-rose-500/20",
    },
    NOT_PROCESSED: {
        label: "Not Processed",
        icon: IconClock,
        className: "bg-gray-500/10 text-gray-600 border-gray-500/20",
    },
    IGNORED: {
        label: "Ignored",
        icon: IconBan,
        className: "bg-zinc-500/10 text-zinc-600 border-zinc-500/20",
    },
};

const DEFAULT_CONFIG = {
    label: "",
    icon: IconClock,
    className: "bg-gray-100 text-gray-600",
};

export const BadgeIndexingStatus: React.FC<BadgeIndexingStatusProps> = ({ status, className }) => {
    const normalized = String(status);
    const config = STATUS_CONFIG[normalized] ?? { ...DEFAULT_CONFIG, label: normalized };
    const Icon = config.icon;

    return (
        <Badge
            variant="outline"
            className={`flex w-fit items-center gap-1.5 px-2 py-0.5 font-mono text-[10px] font-bold tracking-tight uppercase ${config.className} ${className ?? ""}`}
        >
            <Icon className="h-3 w-3" />
            {config.label}
        </Badge>
    );
};
