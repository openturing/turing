import * as AvatarPrimitive from "@radix-ui/react-avatar"
import { cva, type VariantProps } from "class-variance-authority"
import * as React from "react"

import { cn } from "@/lib/utils"

const gradientAvatarFallbackVariants = cva(
    "flex size-full items-center justify-center rounded-full text-xs font-semibold text-white",
    {
        variants: {
            variant: {
                default: [
                    "bg-gradient-to-br from-blue-600 to-indigo-600",
                    "dark:from-blue-500 dark:to-indigo-500",
                ].join(" "),
                secondary: [
                    "bg-gradient-to-br from-slate-600 to-slate-700",
                    "dark:from-slate-500 dark:to-slate-600",
                ].join(" "),
                destructive: [
                    "bg-gradient-to-br from-red-600 to-rose-600",
                    "dark:from-red-500 dark:to-rose-500",
                ].join(" "),
                success: [
                    "bg-gradient-to-br from-emerald-600 to-teal-600",
                    "dark:from-emerald-500 dark:to-teal-500",
                ].join(" "),
                warning: [
                    "bg-gradient-to-br from-amber-600 to-orange-600",
                    "dark:from-amber-500 dark:to-orange-500",
                ].join(" "),
                info: [
                    "bg-gradient-to-br from-cyan-600 to-blue-600",
                    "dark:from-cyan-500 dark:to-blue-500",
                ].join(" "),
            },
        },
        defaultVariants: {
            variant: "default",
        },
    }
)

function GradientAvatar({
    className,
    ...props
}: React.ComponentProps<typeof AvatarPrimitive.Root>) {
    return (
        <AvatarPrimitive.Root
            data-slot="gradient-avatar"
            className={cn(
                "relative flex size-8 shrink-0 overflow-hidden rounded-full",
                className
            )}
            {...props}
        />
    )
}

function GradientAvatarImage({
    className,
    ...props
}: React.ComponentProps<typeof AvatarPrimitive.Image>) {
    return (
        <AvatarPrimitive.Image
            data-slot="gradient-avatar-image"
            className={cn("aspect-square size-full", className)}
            {...props}
        />
    )
}

function GradientAvatarFallback({
    className,
    variant = "default",
    ...props
}: React.ComponentProps<typeof AvatarPrimitive.Fallback> &
    VariantProps<typeof gradientAvatarFallbackVariants>) {
    return (
        <AvatarPrimitive.Fallback
            data-slot="gradient-avatar-fallback"
            className={cn(
                gradientAvatarFallbackVariants({ variant }),
                className
            )}
            {...props}
        />
    )
}

export { GradientAvatar, GradientAvatarFallback, gradientAvatarFallbackVariants, GradientAvatarImage }

