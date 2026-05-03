"use client"
import { ROUTES } from "@/app/routes.const"
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
import { StickyPageHeader } from "@/components/sticky-page-header"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { SmartDescription } from "@/components/ui/smart-description"
import { Label } from "@/components/ui/label"
import { SectionCard } from "@/components/ui/section-card"
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
import { IconCirclePlus, IconDeviceFloppy, IconFileText, IconGitMerge, IconLink, IconReplace, IconTrash, IconX } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turSNSiteMergeService = new TurSNSiteMergeService();

interface Props {
    snSiteId: string;
    value: TurSNSiteMerge;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const SNSiteMergeForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen }) => {
    const { t } = useTranslation();
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
                    toast.success(t("forms.snMerge.created"));
                    navigate(urlBase);
                } else {
                    toast.error(t("forms.snMerge.createFailed"));
                }
            } else {
                const result = await turSNSiteMergeService.update(data);
                if (result) {
                    toast.success(t("forms.snMerge.mergeUpdated"));
                } else {
                    toast.error(t("forms.snMerge.mergeFailed"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.common.formSubmitFailed"));
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
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconGitMerge}
                        feature={t("sn.mergeProviders.feature")}
                        description={t("sn.mergeProviders.description")}
                    />
                    <StickyPageHeader.Actions>
                        {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.mergeProviders.feature")} name={value?.providerFrom && value?.providerTo ? `${value.providerFrom} → ${value.providerTo}` : t("sn.mergeProviders.newMergeProvider")} onDelete={onDelete} open={open} setOpen={setOpen} />}
                        <GradientButton type="submit" size="sm">
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                        <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                            <IconX className="size-4" />
                            {t("forms.formActions.cancel")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
                {/* Providers Section */}
                <SectionCard variant="blue">
                    <SectionCard.Header icon={IconGitMerge} title={t("forms.snMerge.providerDetails")} description={t("forms.snMerge.providerDetailsDesc")} />
                    <SectionCard.Content>
                        <FormField
                            control={form.control}
                            name="providerFrom"
                            rules={{ required: t("forms.snMerge.sourceProviderRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.snMerge.sourceProvider")}</FormLabel>
                                    <FormDescription>
                                        {t("forms.snMerge.sourceProviderDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder={t("forms.snMerge.sourceProviderPlaceholder")} type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="providerTo"
                            rules={{ required: t("forms.snMerge.destProviderRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.snMerge.destProvider")}</FormLabel>
                                    <FormDescription>
                                        {t("forms.snMerge.destProviderDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder={t("forms.snMerge.destProviderPlaceholder")} type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                    </SectionCard.Content>
                </SectionCard>

                {/* Relations Section */}
                <SectionCard variant="violet">
                    <SectionCard.Header icon={IconLink} title={t("forms.snMerge.relationMapping")} description={t("forms.snMerge.relationMappingDesc")} />
                    <SectionCard.Content>
                        <FormField
                            control={form.control}
                            name="relationFrom"
                            rules={{ required: t("forms.snMerge.sourceRelationRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.snMerge.sourceRelation")}</FormLabel>
                                    <FormDescription>
                                        {t("forms.snMerge.sourceRelationDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder={t("forms.snMerge.sourceRelationPlaceholder")} type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="relationTo"
                            rules={{ required: t("forms.snMerge.destRelationRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.snMerge.destRelation")}</FormLabel>
                                    <FormDescription>
                                        {t("forms.snMerge.destRelationDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder={t("forms.snMerge.destRelationPlaceholder")} type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                    </SectionCard.Content>
                </SectionCard>

                {/* Description Section */}
                <SectionCard variant="emerald">
                    <SectionCard.Header icon={IconFileText} title={t("forms.snMerge.aboutMerge")} description={t("forms.snMerge.aboutMergeDesc")} />
                    <SectionCard.Content>
                        <FormField
                            control={form.control}
                            name="description"
                            render={({ field }) => (
                                <FormItem>
                                    <FormControl>
                                        <SmartDescription
                                            value={field.value}
                                            onChange={field.onChange}
                                            placeholder={t("forms.snMerge.mergeDescPlaceholder")}
                                            maxLength={500}
                                            entityType="Merge Provider"
                                            enableMetaPrompt
                                        >
                                            <SmartDescription.Label>{t("forms.snMerge.mergeDescription")}</SmartDescription.Label>
                                            <SmartDescription.Description>
                                                {t("forms.snMerge.mergeDescriptionDesc")}
                                            </SmartDescription.Description>
                                        </SmartDescription>
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                    </SectionCard.Content>
                </SectionCard>

                {/* Overwritten Fields Section */}
                <SectionCard variant="amber">
                    <SectionCard.Header icon={IconReplace} title={t("forms.snMerge.fieldsToOverwrite")} description={t("forms.snMerge.fieldsToOverwriteDesc")} />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between mb-4">
                            <Dialog open={addFieldOpen} onOpenChange={setAddFieldOpen}>
                                <DialogTrigger asChild>
                                    <GradientButton variant="outline" type="button">
                                        <IconCirclePlus className="h-4 w-4 mr-2" />
                                        {t("forms.snMerge.addField")}
                                    </GradientButton>
                                </DialogTrigger>
                                <DialogContent>
                                    <DialogHeader>
                                        <DialogTitle>{t("forms.snMerge.addFieldTitle")}</DialogTitle>
                                        <DialogDescription>
                                            {t("forms.snMerge.addFieldDesc")}
                                        </DialogDescription>
                                    </DialogHeader>
                                    <div className="space-y-4 py-4">
                                        <div className="space-y-2">
                                            <Label htmlFor="field-name">{t("forms.snMerge.fieldName")}</Label>
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
                                            {t("forms.snMerge.addField")}
                                        </GradientButton>
                                    </DialogFooter>
                                </DialogContent>
                            </Dialog>
                        </div>
                        {overwrittenFields.length > 0 && (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>{t("forms.snMerge.fieldName")}</TableHead>
                                        <TableHead className="w-24 text-right">{t("forms.common.edit")}</TableHead>
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
                                                    <IconTrash className="h-4 w-4 mr-1" />
                                                    {t("forms.formActions.delete")}
                                                </GradientButton>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </SectionCard.Content>
                </SectionCard>

                {/* Action Footer */}
            </form>
        </Form>
    );
};
