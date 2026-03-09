import React from "react";
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
        <div className="space-y-4 text-center mt-8 px-6">
            <div className="inline-flex h-14 w-14 items-center justify-center rounded-xl bg-linear-to-br from-blue-600 to-indigo-600 shadow-lg">
                <Icon className="text-white" size={28} />
            </div>
            <h1 className="mb-1">{title}</h1>
            <p className="text-muted-foreground text-sm mt-1">{description}</p>
            {urlNew && (
                <GradientButton className="mt-4" to={urlNew}>
                    {buttonText}
                </GradientButton>
            )}
        </div>
    )
}
