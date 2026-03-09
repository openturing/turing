import * as SwitchPrimitive from "@radix-ui/react-switch"
import { cva, type VariantProps } from "class-variance-authority"
import * as React from "react"

import { cn } from "@/lib/utils"

const gradientSwitchVariants = cva(
    "peer inline-flex h-[1.15rem] w-8 shrink-0 items-center rounded-full border border-transparent shadow-xs transition-all outline-none focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50",
    {
        variants: {
            variant: {
                default: [
                    "data-[state=checked]:bg-gradient-to-r data-[state=checked]:from-blue-600 data-[state=checked]:to-indigo-600",
                    "data-[state=unchecked]:bg-input",
                    "focus-visible:ring-blue-500/50",
                    "dark:data-[state=checked]:from-blue-500 dark:data-[state=checked]:to-indigo-500",
                    "dark:data-[state=unchecked]:bg-input/80",
                    "dark:focus-visible:ring-blue-400/50",
                ].join(" "),
                secondary: [
                    "data-[state=checked]:bg-gradient-to-r data-[state=checked]:from-slate-600 data-[state=checked]:to-slate-700",
                    "data-[state=unchecked]:bg-input",
                    "focus-visible:ring-slate-500/50",
                    "dark:data-[state=checked]:from-slate-500 dark:data-[state=checked]:to-slate-600",
                    "dark:data-[state=unchecked]:bg-input/80",
                    "dark:focus-visible:ring-slate-400/50",
                ].join(" "),
                destructive: [
                    "data-[state=checked]:bg-gradient-to-r data-[state=checked]:from-red-600 data-[state=checked]:to-rose-600",
                    "data-[state=unchecked]:bg-input",
                    "focus-visible:ring-red-500/50",
                    "dark:data-[state=checked]:from-red-500 dark:data-[state=checked]:to-rose-500",
                    "dark:data-[state=unchecked]:bg-input/80",
                    "dark:focus-visible:ring-red-400/50",
                ].join(" "),
                success: [
                    "data-[state=checked]:bg-gradient-to-r data-[state=checked]:from-emerald-600 data-[state=checked]:to-teal-600",
                    "data-[state=unchecked]:bg-input",
                    "focus-visible:ring-emerald-500/50",
                    "dark:data-[state=checked]:from-emerald-500 dark:data-[state=checked]:to-teal-500",
                    "dark:data-[state=unchecked]:bg-input/80",
                    "dark:focus-visible:ring-emerald-400/50",
                ].join(" "),
            },
        },
        defaultVariants: {
            variant: "default",
        },
    }
)

function GradientSwitch({
    className,
    variant,
    ...props
}: React.ComponentProps<typeof SwitchPrimitive.Root> &
    VariantProps<typeof gradientSwitchVariants>) {
    return (
        <SwitchPrimitive.Root
            data-slot="gradient-switch"
            className={cn(gradientSwitchVariants({ variant }), className)}
            {...props}
        >
            <SwitchPrimitive.Thumb
                data-slot="gradient-switch-thumb"
                className={cn(
                    "bg-background pointer-events-none block size-4 rounded-full ring-0 transition-transform data-[state=checked]:translate-x-[calc(100%-2px)] data-[state=unchecked]:translate-x-0 dark:bg-primary-foreground"
                )}
            />
        </SwitchPrimitive.Root>
    )
}

export { GradientSwitch, gradientSwitchVariants }

