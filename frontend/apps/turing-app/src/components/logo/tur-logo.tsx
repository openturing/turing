interface TurLogoProps {
    readonly size?: number;
    readonly className?: string;
}

export function TurLogo({ size = 48, className }: TurLogoProps) {
    return (
        <svg
            className={className}
            width={size}
            height={size}
            viewBox="0 0 549 549"
        >
            <defs>
                <style>{`
                    .tur-logo-bg {
                        fill: royalblue;
                        stroke: #ffc;
                        stroke-width: 20px;
                        opacity: 1.0;
                    }
                    .tur-logo-text {
                        font-size: 98.505px;
                        fill: #ffc;
                        font-family: "Proxima Nova", system-ui, sans-serif;
                        font-weight: 500;
                    }
                `}</style>
            </defs>
            <rect
                className="tur-logo-bg"
                x="0.063"
                width="548"
                height="548.188"
                rx="100"
                ry="100"
            />
            <text
                className="tur-logo-text"
                transform="translate(64.825 442.418) scale(2.74 2.741)"
            >
                Tu
            </text>
        </svg>
    );
}
