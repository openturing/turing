import * as React from "react"

import { cn } from "@/lib/utils"

import { FormDescription, FormItem, FormLabel } from "./form"

type FormItemTwoColumnsComponent = React.FC<React.ComponentProps<"div">> & {
    Left: React.FC<React.ComponentProps<"div">>
    Right: React.FC<React.ComponentProps<"div">>
    Label: React.FC<React.ComponentProps<typeof FormLabel>>
    Description: React.FC<React.ComponentProps<typeof FormDescription>>
}

const FormItemTwoColumnsRoot: React.FC<React.ComponentProps<"div">> = ({
    className,
    ...props
}) => {
    return (
        <FormItem>
            <div
                className={cn("flex w-full flex-row items-center gap-4", className)}
                {...props}
            />
        </FormItem>
    )
}

const FormItemTwoColumnsLeft: React.FC<React.ComponentProps<"div">> = ({
    className,
    ...props
}) => (
    <div className={cn("flex w-1/2 flex-col gap-1", className)} {...props} />
)

const FormItemTwoColumnsRight: React.FC<React.ComponentProps<"div">> = ({
    className,
    ...props
}) => <div className={cn("flex w-1/2 justify-end", className)} {...props} />

const FormItemTwoColumnsLabel: React.FC<React.ComponentProps<typeof FormLabel>> = ({
    className,
    ...props
}) => <FormLabel className={cn(className)} {...props} />

const FormItemTwoColumnsDescription: React.FC<
    React.ComponentProps<typeof FormDescription>
> = ({ className, ...props }) => (
    <FormDescription className={cn(className)} {...props} />
)

export const FormItemTwoColumns = Object.assign(FormItemTwoColumnsRoot, {
    Left: FormItemTwoColumnsLeft,
    Right: FormItemTwoColumnsRight,
    Label: FormItemTwoColumnsLabel,
    Description: FormItemTwoColumnsDescription,
}) as FormItemTwoColumnsComponent
