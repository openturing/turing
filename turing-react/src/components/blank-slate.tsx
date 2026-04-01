import React from "react";
import { FloatingFormulasBg } from "./ui/floating-formulas-bg";
import { GradientButton } from "./ui/gradient-button";

interface Props {
    icon: React.ElementType
    title: string;
    description: string;
    buttonText: string;
    urlNew?: string;
}

export const BlankSlate: React.FC<Props> = ({ icon: Icon, title, description, urlNew, buttonText }) => {
    return (
        <div className="flex items-center justify-center px-6 py-16">
            <div className="relative w-full max-w-md overflow-hidden rounded-2xl border border-border/50 bg-card/80 backdrop-blur-sm shadow-xl">
                <FloatingFormulasBg itemCount={10} />

                <div className="relative flex flex-col items-center text-center px-8 py-12 space-y-6">
                    {/* Icon with animated ring */}
                    <div className="relative">
                        <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-blue-500/20 to-indigo-500/20 blur-xl animate-pulse" />
                        <div className="relative inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-600 shadow-lg shadow-blue-500/25 ring-4 ring-blue-500/10">
                            <Icon className="text-white" size={30} />
                        </div>
                    </div>

                    {/* Text */}
                    <div className="space-y-2">
                        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
                        <p className="text-sm text-muted-foreground leading-relaxed max-w-xs mx-auto">
                            {description}
                        </p>
                    </div>

                    {/* CTA */}
                    {urlNew && (
                        <GradientButton size="lg" to={urlNew}>
                            {buttonText}
                        </GradientButton>
                    )}
                </div>
            </div>
        </div>
    );
}
