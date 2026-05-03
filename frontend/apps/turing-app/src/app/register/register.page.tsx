import { ROUTES } from "@/app/routes.const";
import { IconAlertCircle, IconArrowLeft, IconLoader2, IconUserPlus } from "@tabler/icons-react";
import { TurLogo } from "@/components/logo/tur-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

const authorization = new TurAuthorizationService();

export default function RegisterPage() {
    const { t } = useTranslation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [error, setError] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!username || !password) {
            setError(t("register.requiredFields"));
            return;
        }
        if (password !== confirmPassword) {
            setError(t("register.passwordMismatch"));
            return;
        }

        setIsLoading(true);
        try {
            await authorization.register({ username, password, firstName, lastName, email });
            globalThis.location.href = ROUTES.LOGIN;
        } catch (err) {
            setError(err instanceof Error ? err.message : t("register.failed"));
            setIsLoading(false);
        }
    };

    return (
        <div className="relative min-h-svh flex items-center justify-center bg-slate-50 dark:bg-slate-950">
            <div className="absolute top-4 right-4 z-30">
                <ModeToggle />
            </div>

            <div className="flex flex-col items-center w-full max-w-md px-6 py-8">
                <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm shadow-lg ring-1 ring-blue-200/50 dark:ring-blue-500/20">
                    <TurLogo size={52} />
                </div>

                <h1 className="text-2xl font-bold tracking-tight bg-linear-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 bg-clip-text text-transparent mb-4">
                    Viglet Turing ES
                </h1>

                <Card className="w-full border-0 shadow-none bg-transparent">
                    <CardHeader className="text-center pb-2">
                        <CardTitle className="text-2xl font-bold tracking-tight">
                            {t("register.title")}
                        </CardTitle>
                        <CardDescription className="text-muted-foreground">
                            {t("register.description")}
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                            {error && (
                                <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                                    <IconAlertCircle className="h-4 w-4 shrink-0" />
                                    <span>{error}</span>
                                </div>
                            )}

                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-2">
                                    <Label htmlFor="firstName">{t("register.firstName")}</Label>
                                    <Input
                                        id="firstName"
                                        value={firstName}
                                        onChange={(e) => setFirstName(e.target.value)}
                                        disabled={isLoading}
                                        className="h-11"
                                    />
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="lastName">{t("register.lastName")}</Label>
                                    <Input
                                        id="lastName"
                                        value={lastName}
                                        onChange={(e) => setLastName(e.target.value)}
                                        disabled={isLoading}
                                        className="h-11"
                                    />
                                </div>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="email">{t("register.email")}</Label>
                                <Input
                                    id="email"
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    disabled={isLoading}
                                    className="h-11"
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="username">{t("register.username")} *</Label>
                                <Input
                                    id="username"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    disabled={isLoading}
                                    className="h-11"
                                    required
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-2">
                                    <Label htmlFor="password">{t("register.password")} *</Label>
                                    <Input
                                        id="password"
                                        type="password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        disabled={isLoading}
                                        className="h-11"
                                        required
                                    />
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="confirmPassword">{t("register.confirmPassword")} *</Label>
                                    <Input
                                        id="confirmPassword"
                                        type="password"
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        disabled={isLoading}
                                        className="h-11"
                                        required
                                    />
                                </div>
                            </div>

                            <GradientButton type="submit" className="w-full" disabled={isLoading}>
                                {isLoading ? (
                                    <IconLoader2 className="mr-2 h-4 w-4 animate-spin" />
                                ) : (
                                    <IconUserPlus className="mr-2 h-4 w-4" />
                                )}
                                {isLoading ? t("register.creating") : t("register.createAccount")}
                            </GradientButton>

                            <div className="text-center">
                                <Link
                                    to={ROUTES.LOGIN}
                                    className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-primary transition-colors"
                                >
                                    <IconArrowLeft className="h-3.5 w-3.5" />
                                    {t("register.backToLogin")}
                                </Link>
                            </div>
                        </form>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
