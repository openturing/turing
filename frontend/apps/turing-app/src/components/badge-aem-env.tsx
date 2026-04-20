import { Badge } from "@/components/ui/badge";
import { Globe, PencilLine } from "lucide-react";

interface BadgeAemEnvProps {
    environment: string;
    className?: string;
}

export const BadgeAemEnv: React.FC<BadgeAemEnvProps> = ({ environment, className }) => {
    const isAuthor = String(environment).toUpperCase() === "AUTHOR";

    return (
        <Badge
            variant="outline"
            className={`
                font-mono font-bold gap-1.5 px-3 py-1 transition-all
                ${isAuthor
                    ? "bg-amber-500/10 text-amber-600 border-amber-500/20 hover:bg-amber-500/20 dark:text-amber-400"
                    : "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 hover:bg-emerald-500/20 dark:text-emerald-400"
                }
                ${className ?? ""}
            `}
        >
            {isAuthor ? (
                <>
                    <PencilLine className="w-3.5 h-3.5" />
                    <span>AUTHOR</span>
                </>
            ) : (
                <>
                    <Globe className="w-3.5 h-3.5" />
                    <span>PUBLISHING</span>
                </>
            )}
        </Badge>
    );
};
