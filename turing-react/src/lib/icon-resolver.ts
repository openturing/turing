import {
    IconAdjustmentsSearch,
    IconAppWindow,
    IconChartBar,
    IconCircle,
    IconDatabase,
    IconFolder,
    IconGitCommit,
    IconGlobe,
    IconGraph,
    IconInfoCircle,
    IconListCheck,
    IconTools,
} from "@tabler/icons-react";
import type { ElementType } from "react";

const iconMap: Record<string, ElementType> = {
    IconAdjustmentsSearch,
    IconAppWindow,
    IconChartBar,
    IconDatabase,
    IconFolder,
    IconGitCommit,
    IconGlobe,
    IconGraph,
    IconInfoCircle,
    IconListCheck,
    IconTools,
};

export function resolveIcon(name: string): ElementType {
    return iconMap[name] ?? IconCircle;
}
