import { ROUTES } from '@/app/routes.const';
import { IconAlertCircle, IconKey, IconLoader2, IconLogin, IconUser } from "@tabler/icons-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { GradientButton } from '@/components/ui/gradient-button';
import { Input } from '@/components/ui/input';
import { cn } from "@/lib/utils.ts";
import type { TurRestInfo } from '@/models/auth/rest-info';
import { TurAuthorizationService } from '@/services/auth/authorization.service';
import { TurSetupService } from '@/services/auth/setup.service';
import { Icon } from '@iconify/react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';


const providerMeta: Record<string, { icon: string; label: string }> = {
    google: { icon: "flat-color-icons:google", label: "Google" },
    microsoft: { icon: "devicon:microsoft", label: "Microsoft" },
    github: { icon: "mdi:github", label: "GitHub" },
};

export function LoginForm({
    className,
    ...props
}: React.ComponentProps<"form">) {
    const [searchParams] = useSearchParams();
    const returnUrl = searchParams.get('returnUrl') || ROUTES.CONSOLE;
    const { t } = useTranslation();
    const form = useForm<TurRestInfo>();
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [oauth2Providers, setOauth2Providers] = useState<string[]>([]);
    const [selfRegistration, setSelfRegistration] = useState(false);
    const authorization = new TurAuthorizationService()

    useEffect(() => {
        new TurSetupService().status().then((status) => {
            if (status.required) {
                globalThis.location.href = ROUTES.SETUP;
            }
        }).catch(() => { /* ignore — login page is still usable */ });

        authorization.discovery().then((discovery) => {
            if (discovery.authThirdparty && discovery.oauth2Providers?.length > 0) {
                setOauth2Providers(discovery.oauth2Providers);
            }
            setSelfRegistration(discovery.selfRegistration ?? false);
        }).catch(() => { /* ignore */ });
    }, []);

    function onSubmit(turRestInfo: TurRestInfo) {
        setError('');
        setIsLoading(true);
        try {
            if (!turRestInfo.username || !turRestInfo.password) {
                throw new Error(t('login.emptyFields'));
            }
            authorization.login(turRestInfo.username, turRestInfo.password).then(() => {
                globalThis.location.href = returnUrl;
            }).catch(() => {
                setError(t('login.invalidCredentials'));
                setIsLoading(false);
            });
        } catch (err) {
            setIsLoading(false);
            if (err instanceof Error) {
                setError(err.message || t('login.loginFailed'));
            } else {
                setError(t('login.loginFailed'));
            }
        }
    }

    const apiBaseUrl = (import.meta.env.VITE_API_URL as string) || '';

    return (
        <Card className="border-0 shadow-none bg-transparent">
            <CardHeader className="text-center pb-2">
                <CardTitle className="text-2xl font-bold tracking-tight">
                    {t('login.welcomeBack')}
                </CardTitle>
                <CardDescription className="text-muted-foreground">
                    {t('login.signInDescription')}
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
                                name="username"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className="text-sm font-medium">{t('login.username')}</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <IconUser className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder={t('login.enterUsername')}
                                                    type="text"
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
                                name="password"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className="text-sm font-medium">{t('login.password')}</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <IconKey className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder={t('login.enterPassword')}
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
                                <IconLogin className="mr-2 h-4 w-4" />
                            )}
                            {isLoading ? t('login.signingIn') : t('login.signIn')}
                        </GradientButton>

                        {oauth2Providers.length > 0 && (
                            <>
                                {/* Divider */}
                                <div className="relative">
                                    <div className="absolute inset-0 flex items-center">
                                        <span className="w-full border-t border-slate-200 dark:border-slate-700" />
                                    </div>
                                    <div className="relative flex justify-center text-xs uppercase">
                                        <span className="bg-white/75 dark:bg-slate-900/70 px-2 text-muted-foreground backdrop-blur-sm">
                                            {t('login.orContinueWith')}
                                        </span>
                                    </div>
                                </div>

                                {/* Social login buttons — only configured providers */}
                                <div className={cn("grid gap-3", oauth2Providers.length === 1 ? "grid-cols-1" : oauth2Providers.length === 2 ? "grid-cols-2" : "grid-cols-3")}>
                                    {oauth2Providers.map((provider) => {
                                        const meta = providerMeta[provider];
                                        if (!meta) return null;
                                        return (
                                            <a
                                                key={provider}
                                                href={`${apiBaseUrl}/oauth2/authorization/${provider}`}
                                                className={cn(
                                                    "inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 dark:border-slate-700",
                                                    "bg-white/60 dark:bg-slate-800/60 backdrop-blur-sm",
                                                    "px-3 py-2.5 text-sm font-medium",
                                                    "text-slate-700 dark:text-slate-300",
                                                    "transition-all hover:bg-white/90 dark:hover:bg-slate-800/90",
                                                    "hover:border-slate-300 dark:hover:border-slate-600",
                                                    "hover:shadow-sm",
                                                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2",
                                                )}
                                            >
                                                <Icon icon={meta.icon} className="h-5 w-5" />
                                                <span className="sr-only">{meta.label}</span>
                                                <span className="hidden sm:inline">{meta.label}</span>
                                            </a>
                                        );
                                    })}
                                </div>
                            </>
                        )}
                        {selfRegistration && (
                            <div className="text-center text-sm text-muted-foreground">
                                {t('login.noAccount')}{' '}
                                <Link to={ROUTES.REGISTER} className="font-medium text-blue-600 hover:text-blue-500 dark:text-blue-400 dark:hover:text-blue-300 transition-colors">
                                    {t('login.createAccount')}
                                </Link>
                            </div>
                        )}
                    </form>
                </Form>
            </CardContent>
        </Card>
    );
}
