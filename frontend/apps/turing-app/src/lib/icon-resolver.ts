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
    IconPuzzle,
    IconSparkles,
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
    IconPuzzle,
    IconSparkles,
    IconTools,
};

export function resolveIcon(name: string): ElementType {
    return iconMap[name] ?? IconCircle;
}
