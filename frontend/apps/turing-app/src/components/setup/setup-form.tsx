import { ROUTES } from '@/app/routes.const';
import { IconAlertCircle, IconCircleCheck, IconKey, IconLoader2 } from "@tabler/icons-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { GradientButton } from '@/components/ui/gradient-button';
import { Input } from '@/components/ui/input';
import { cn } from "@/lib/utils";
import { TurSetupService } from '@/services/auth/setup.service';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';

interface SetupFormValues {
    password: string;
    confirmPassword: string;
}

export function SetupForm({
    className,
    ...props
}: React.ComponentProps<"form">) {
    const { t } = useTranslation();
    const form = useForm<SetupFormValues>({
        defaultValues: { password: '', confirmPassword: '' },
    });
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const setupService = new TurSetupService();

    useEffect(() => {
        setupService.status().then((status) => {
            if (!status.required) {
                globalThis.location.href = ROUTES.LOGIN;
            }
        }).catch(() => {
            globalThis.location.href = ROUTES.LOGIN;
        });
    }, []);

    function onSubmit(values: SetupFormValues) {
        setError('');

        if (!values.password || values.password.length < 6) {
            setError(t('setup.passwordMinLength'));
            return;
        }
        if (values.password !== values.confirmPassword) {
            setError(t('setup.passwordMismatch'));
            return;
        }

        setIsLoading(true);
        setupService.setAdminPassword(values.password).then(() => {
            globalThis.location.href = ROUTES.LOGIN;
        }).catch(() => {
            setError(t('setup.setupFailed'));
            setIsLoading(false);
        });
    }

    return (
        <Card className="border-0 shadow-none bg-transparent">
            <CardHeader className="text-center pb-2">
                <CardTitle className="text-2xl font-bold tracking-tight">
                    {t('setup.title')}
                </CardTitle>
                <CardDescription className="text-muted-foreground">
                    {t('setup.description')}
                </CardDescription>
            </CardHeader>
            <CardContent>
                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className={cn("flex flex-col gap-5", className)} {...props}>
                        {error && (
                            <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                                <IconAlertCircle className="h-4 w-4 shrink-0" />
                                <span>{error}</span>
                            </div>
                        )}
                        <div className="grid gap-4">
                            <FormField
                                control={form.control}
                                name="password"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className="text-sm font-medium">{t('setup.password')}</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <IconKey className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder={t('setup.enterPassword')}
                                                    type="password"
                                                    className="pl-9 h-11"
                                                    disabled={isLoading}
                                                />
                                            </div>
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="confirmPassword"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className="text-sm font-medium">{t('setup.confirmPassword')}</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <IconKey className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder={t('setup.repeatPassword')}
                                                    type="password"
                                                    className="pl-9 h-11"
                                                    disabled={isLoading}
                                                />
                                            </div>
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        </div>
                        <GradientButton
                            type="submit"
                            className="w-full"
                            disabled={isLoading}
                        >
                            {isLoading ? (
                                <IconLoader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                                <IconCircleCheck className="mr-2 h-4 w-4" />
                            )}
                            {isLoading ? t('setup.saving') : t('setup.save')}
                        </GradientButton>
                    </form>
                </Form>
            </CardContent>
        </Card>
    );
}
