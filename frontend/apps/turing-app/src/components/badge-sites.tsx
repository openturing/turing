import { BadgeColorful } from "@/components/badge-colorful";

interface BadgeSitesProps {
    sites: string[] | unknown;
    className?: string;
}

export const BadgeSites: React.FC<BadgeSitesProps> = ({ sites, className }) => {
    const sitesArray = Array.isArray(sites) ? sites : [];

    return (
        <div className={`flex flex-wrap gap-1.5 min-w-37.5 ${className ?? ""}`}>
            {sitesArray.map((site: string, index: number) => (
                <BadgeColorful
                    key={`${site}-${index}`}
                    text={site}
                />
            ))}
        </div>
    );
};
