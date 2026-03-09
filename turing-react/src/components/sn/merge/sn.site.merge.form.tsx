"use client"
import { ROUTES } from "@/app/routes.const"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog"
import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import type { TurSNSiteMergeField } from "@/models/sn/sn-site-merge-field.model"
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model"
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service"
import { PlusCircle, Trash2 } from "lucide-react"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"

const turSNSiteMergeService = new TurSNSiteMergeService();

interface Props {
    snSiteId: string;
    value: TurSNSiteMerge;
    isNew: boolean;
}

export const SNSiteMergeForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
    const form = useForm<TurSNSiteMerge>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/merge-providers`;
    const [addFieldOpen, setAddFieldOpen] = useState(false);
    const [newFieldName, setNewFieldName] = useState("");

    useEffect(() => {
        form.reset(value);
    }, [value]);

    async function onSubmit(data: TurSNSiteMerge) {
        try {
            if (isNew) {
                const result = await turSNSiteMergeService.create(data);
                if (result) {
                    toast.success(`The merge provider was created`);
                    navigate(urlBase);
                } else {
                    toast.error("Failed to create the merge provider. Please try again.");
                }
            } else {
                const result = await turSNSiteMergeService.update(data);
                if (result) {
                    toast.success(`The merge provider was updated`);
                } else {
                    toast.error("Failed to update the merge provider. Please try again.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to submit the form. Please try again.");
        }
    }

    function addOverwrittenField() {
        if (!newFieldName.trim()) return;
        const currentFields = form.getValues("overwrittenFields") || [];
        const newField: TurSNSiteMergeField = { name: newFieldName.trim() };
        form.setValue("overwrittenFields", [...currentFields, newField]);
        setNewFieldName("");
        setAddFieldOpen(false);
    }

    function removeOverwrittenField(index: number) {
        const currentFields = form.getValues("overwrittenFields") || [];
        form.setValue(
            "overwrittenFields",
            currentFields.filter((_, i) => i !== index)
        );
    }

    const overwrittenFields = form.watch("overwrittenFields") || [];

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-10 py-8 px-6">
                <Accordion
                    type="multiple"
                    defaultValue={["providers", "relations", "description", "overwritten-fields"]}
                    className="w-full space-y-4"
                >
                    {/* Providers Section */}
                    <AccordionItem value="providers" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Provider Details</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <FormField
                                control={form.control}
                                name="providerFrom"
                                rules={{ required: "Please enter the source provider." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Source Provider</FormLabel>
                                        <FormDescription>
                                            Choose the provider you want to merge data from. This is where your information starts.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Main Database" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="providerTo"
                                rules={{ required: "Please enter the destination provider." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Destination Provider</FormLabel>
                                        <FormDescription>
                                            Select where the merged data will go. This is your target location.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Analytics Store" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </AccordionContent>
                    </AccordionItem>

                    {/* Relations Section */}
                    <AccordionItem value="relations" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Relation Mapping</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <FormField
                                control={form.control}
                                name="relationFrom"
                                rules={{ required: "Please enter the source relation." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Source Relation</FormLabel>
                                        <FormDescription>
                                            Specify the relation from your source provider. This helps match the right data.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. user_id" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="relationTo"
                                rules={{ required: "Please enter the destination relation." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Destination Relation</FormLabel>
                                        <FormDescription>
                                            Enter the relation in your destination provider. This is where the data will be linked.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. account_id" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </AccordionContent>
                    </AccordionItem>

                    {/* Description Section */}
                    <AccordionItem value="description" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">About This Merge</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Merge Description</FormLabel>
                                        <FormDescription>
                                            Add a short note to help you and your team understand the purpose of this merge.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Sync user profiles for analytics" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </AccordionContent>
                    </AccordionItem>

                    {/* Overwritten Fields Section */}
                    <AccordionItem value="overwritten-fields" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Fields to Overwrite</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <div className="flex items-center justify-between mb-4">
                                <Dialog open={addFieldOpen} onOpenChange={setAddFieldOpen}>
                                    <DialogTrigger asChild>
                                        <GradientButton variant="outline" type="button">
                                            <PlusCircle className="h-4 w-4 mr-2" />
                                            Add Field
                                        </GradientButton>
                                    </DialogTrigger>
                                    <DialogContent>
                                        <DialogHeader>
                                            <DialogTitle>Add a Field to Overwrite</DialogTitle>
                                            <DialogDescription>
                                                Pick a field from your source that should replace the value in your destination. This is useful for keeping important data up to date.
                                            </DialogDescription>
                                        </DialogHeader>
                                        <div className="space-y-4 py-4">
                                            <div className="space-y-2">
                                                <Label htmlFor="field-name">Field Name</Label>
                                                <Input
                                                    id="field-name"
                                                    value={newFieldName}
                                                    onChange={(e) => setNewFieldName(e.target.value)}
                                                    placeholder="e.g. email"
                                                    onKeyDown={(e) => {
                                                        if (e.key === "Enter") {
                                                            e.preventDefault();
                                                            addOverwrittenField();
                                                        }
                                                    }}
                                                />
                                            </div>
                                        </div>
                                        <DialogFooter>
                                            <GradientButton type="button" onClick={addOverwrittenField}>
                                                Add Field
                                            </GradientButton>
                                        </DialogFooter>
                                    </DialogContent>
                                </Dialog>
                            </div>
                            {overwrittenFields.length > 0 && (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Field Name</TableHead>
                                            <TableHead className="w-24 text-right">Actions</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {overwrittenFields.map((field, index) => (
                                            <TableRow key={field.id ?? `new-${index}`}>
                                                <TableCell>{field.name}</TableCell>
                                                <TableCell className="text-right">
                                                    <GradientButton
                                                        variant="destructive"
                                                        size="sm"
                                                        type="button"
                                                        onClick={() => removeOverwrittenField(index)}
                                                    >
                                                        <Trash2 className="h-4 w-4 mr-1" />
                                                        Delete
                                                    </GradientButton>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </AccordionContent>
                    </AccordionItem>
                </Accordion>

                {/* Action Footer */}
                <div className="flex justify-end gap-3 pt-4">
                    <GradientButton
                        type="button"
                        variant="outline"
                        onClick={() => navigate(urlBase)}
                    >
                        Cancel
                    </GradientButton>
                    <GradientButton type="submit">
                        {isNew ? "Create Merge Provider" : "Save Changes"}
                    </GradientButton>
                </div>
            </form>
        </Form>
    );
};
