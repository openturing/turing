import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormActions } from "@/components/ui/form-actions";
import { SectionCard } from "@/components/ui/section-card";
import type { TurGroup } from "@/models/auth/group";
import { TurGroupService } from "@/services/auth/group.service";
import { IconUsersGroup, IconNotes } from "@tabler/icons-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const turGroupService = new TurGroupService();

interface Props {
    value: TurGroup;
    isNew: boolean;
}

export const AdminGroupForm: React.FC<Props> = ({ value, isNew }) => {
    const form = useForm<TurGroup>({ defaultValues: value });
    const navigate = useNavigate();

    useEffect(() => {
        form.reset(value);
    }, [value]);

    async function onSubmit(data: TurGroup) {
        try {
            if (isNew) {
                const result = await turGroupService.create(data);
                if (result) {
                    toast.success(`Group "${data.name}" was created.`);
                    navigate(ROUTES.ADMIN_GROUPS);
                } else {
                    toast.error("Failed to create group.");
                }
            } else {
                const result = await turGroupService.update(data.id, data);
                if (result) {
                    toast.success(`Group "${data.name}" was updated.`);
                } else {
                    toast.error("Failed to update group.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to save group. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUsersGroup} title="Group Details" description="Name and identification for this group." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: "Group name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Group Name</FormLabel>
                                        <FormDescription>
                                            A unique name to identify this group (e.g. "Administrators", "Editors").
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Administrators" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title="Description" description="Optional details about this group's purpose." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Description</FormLabel>
                                        <FormDescription>
                                            Describe the purpose or scope of permissions for this group.
                                        </FormDescription>
                                        <FormControl>
                                            <Textarea
                                                {...field}
                                                placeholder="e.g. Full administrative access to all features"
                                                className="resize-none"
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <FormActions>
                        <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_GROUPS)} />
                        <FormActions.Submit>{isNew ? "Create Group" : "Save Changes"}</FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </div>
    );
};
