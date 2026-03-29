import {
    IconAdjustmentsSearch,
    IconAppWindow,
    IconChartBar,
    IconCircle,
    IconGitCommit,
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
    IconGitCommit,
    IconGraph,
    IconInfoCircle,
    IconListCheck,
    IconTools,
};

export function resolveIcon(name: string): ElementType {
    return iconMap[name] ?? IconCircle;
}
