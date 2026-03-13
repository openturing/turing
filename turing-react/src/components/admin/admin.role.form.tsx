import { ROUTES } from "@/app/routes.const";
import {
    Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormActions } from "@/components/ui/form-actions";
import { SectionCard } from "@/components/ui/section-card";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { IconUserShield, IconNotes } from "@tabler/icons-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const turRoleService = new TurRoleService();

interface Props {
    value: TurRole;
    isNew: boolean;
}

export const AdminRoleForm: React.FC<Props> = ({ value, isNew }) => {
    const form = useForm<TurRole>({ defaultValues: value });
    const navigate = useNavigate();

    useEffect(() => {
        form.reset(value);
    }, [value]);

    async function onSubmit(data: TurRole) {
        try {
            if (isNew) {
                const result = await turRoleService.create(data);
                if (result) {
                    toast.success(`Role "${data.name}" was created.`);
                    navigate(ROUTES.ADMIN_ROLES);
                } else {
                    toast.error("Failed to create role.");
                }
            } else {
                const result = await turRoleService.update(data.id, data);
                if (result) {
                    toast.success(`Role "${data.name}" was updated.`);
                } else {
                    toast.error("Failed to update role.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to save role. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    <SectionCard variant="blue">
                        <SectionCard.Header icon={IconUserShield} title="Role Details" description="Name and identification for this role." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: "Role name is required." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Role Name</FormLabel>
                                        <FormDescription>
                                            A unique name for this role (e.g. "Admin", "Editor", "Viewer").
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Admin" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconNotes} title="Description" description="Optional details about this role's scope." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="description"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Description</FormLabel>
                                        <FormDescription>
                                            Describe what permissions or access level this role grants.
                                        </FormDescription>
                                        <FormControl>
                                            <Textarea
                                                {...field}
                                                placeholder="e.g. Full read/write access to all resources"
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
                        <FormActions.Cancel onClick={() => navigate(ROUTES.ADMIN_ROLES)} />
                        <FormActions.Submit>{isNew ? "Create Role" : "Save Changes"}</FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </div>
    );
};
