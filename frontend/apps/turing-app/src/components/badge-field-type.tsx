import { Badge } from "@/components/ui/badge";
import {
    AlignLeft,
    Binary,
    Calendar,
    CheckCircle2,
    CircleDollarSign,
    CircleDot,
    Hash,
    List,
    Sigma,
    Type,
} from "lucide-react";

type FieldTypeBadgeProps = {
    type?: string | null;
    variation?: "short" | "long";
};

const typeConfig = {
    INT: {
        label: "Integer",
        icon: Hash,
        className:
            "bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-800",
    },
    LONG: {
        label: "Long",
        icon: Binary,
        className:
            "bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-900/30 dark:text-slate-300 dark:border-slate-800",
    },
    FLOAT: {
        label: "Float",
        icon: CircleDot,
        className:
            "bg-cyan-100 text-cyan-700 border-cyan-200 dark:bg-cyan-900/30 dark:text-cyan-400 dark:border-cyan-800",
    },
    DOUBLE: {
        label: "Double",
        icon: Sigma,
        className:
            "bg-rose-100 text-rose-700 border-rose-200 dark:bg-rose-900/30 dark:text-rose-400 dark:border-rose-800",
    },
    CURRENCY: {
        label: "Currency",
        icon: CircleDollarSign,
        className:
            "bg-teal-100 text-teal-700 border-teal-200 dark:bg-teal-900/30 dark:text-teal-400 dark:border-teal-800",
    },
    STRING: {
        label: "String",
        icon: Type,
        className:
            "bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800",
    },
    TEXT: {
        label: "Text",
        icon: AlignLeft,
        className:
            "bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-800",
    },
    ARRAY: {
        label: "Array",
        icon: List,
        className:
            "bg-violet-100 text-violet-700 border-violet-200 dark:bg-violet-900/30 dark:text-violet-400 dark:border-violet-800",
    },
    DATE: {
        label: "Date",
        icon: Calendar,
        className:
            "bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-900/30 dark:text-orange-400 dark:border-orange-800",
    },
    BOOL: {
        label: "Boolean",
        icon: CheckCircle2,
        className:
            "bg-lime-100 text-lime-700 border-lime-200 dark:bg-lime-900/30 dark:text-lime-400 dark:border-lime-800",
    },
} as const;

export const BadgeFieldType: React.FC<FieldTypeBadgeProps> = ({ type, variation = "long" }) => {
    const typeValue = type ?? "";
    const cleanType = typeValue.split("(")[0].toUpperCase();
    const config = typeConfig[cleanType as keyof typeof typeConfig] ?? {
        label: typeValue,
        icon: Type,
        className: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
    };

    const Icon = config.icon;
    const isShort = variation === "short";

    return (
        <Badge
            variant="outline"
            className={`justify-center inline-flex items-center font-semibold whitespace-nowrap transition-colors ${isShort ? "w-8 px-0 py-1" : "w-auto md:w-24 gap-1 md:gap-2 py-1 px-2 md:px-3"} ${config.className}`}
        >
            {Icon && <Icon className="h-3.5 w-3.5 shrink-0" />}
            {!isShort && <span className="hidden md:inline">{config.label}</span>}
        </Badge>
    );
};
