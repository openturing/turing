import { ROUTES } from '@/app/routes.const';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { GradientButton } from '@/components/ui/gradient-button';
import { Input } from '@/components/ui/input';
import { cn } from "@/lib/utils.ts";
import type { TurRestInfo } from '@/models/auth/rest-info';
import { TurAuthorizationService } from '@/services/auth/authorization.service';
import { AlertCircle, KeyRound, Loader2, LogIn, User } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useSearchParams } from 'react-router-dom';


export function LoginForm({
    className,
    ...props
}: React.ComponentProps<"form">) {
    const [searchParams] = useSearchParams();
    const returnUrl = searchParams.get('returnUrl') || ROUTES.CONSOLE;
    const form = useForm<TurRestInfo>();
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const authorization = new TurAuthorizationService()
    function onSubmit(turRestInfo: TurRestInfo) {
        setError('');
        setIsLoading(true);
        try {
            if (!turRestInfo.username || !turRestInfo.password) {
                throw new Error('Please enter both username and password');
            }
            authorization.login(turRestInfo.username, turRestInfo.password).then((response) => {
                localStorage.setItem("restInfo", JSON.stringify(response));
                globalThis.location.href = returnUrl;
            }).catch(() => {
                setError('Invalid credentials. Please try again.');
                setIsLoading(false);
            });
        } catch (err) {
            setIsLoading(false);
            if (err instanceof Error) {
                setError(err.message || 'Login failed');
            } else {
                setError('Login failed');
            }
        }
    };
    return (
        <Card className="border-0 shadow-xl shadow-black/5">
            <CardHeader className="text-center pb-2">
                <CardTitle className="text-2xl font-bold tracking-tight">
                    Welcome back
                </CardTitle>
                <CardDescription className="text-muted-foreground">
                    Sign in to access your dashboard
                </CardDescription>
            </CardHeader>
            <CardContent>
                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className={cn("flex flex-col gap-5", className)} {...props}>
                        {error && (
                            <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                                <AlertCircle className="h-4 w-4 shrink-0" />
                                <span>{error}</span>
                            </div>
                        )}
                        <div className="grid gap-4">
                            <FormField
                                control={form.control}
                                name="username"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className="text-sm font-medium">Username</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder="Enter your username"
                                                    type="text"
                                                    className="pl-10 h-11"
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
                                        <FormLabel className="text-sm font-medium">Password</FormLabel>
                                        <FormControl>
                                            <div className="relative">
                                                <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                                <Input
                                                    {...field}
                                                    placeholder="Enter your password"
                                                    type="password"
                                                    className="pl-10 h-11"
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
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                                <LogIn className="mr-2 h-4 w-4" />
                            )}
                            {isLoading ? 'Signing in...' : 'Sign in'}
                        </GradientButton>
                    </form>
                </Form>
            </CardContent>
        </Card>
    );
}