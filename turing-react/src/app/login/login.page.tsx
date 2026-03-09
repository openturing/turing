import { LoginForm } from "@/components/login/login-form"
import { TurLogo } from "@/components/logo/tur-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { Globe, Search, Sparkles } from "lucide-react"

const LoginPage = () => {
    return (
        <div className="grid min-h-svh lg:grid-cols-2">
            {/* Left panel - Branding / Hero */}
            <div className="relative hidden lg:flex flex-col items-center justify-center overflow-hidden bg-linear-to-br from-blue-600 via-indigo-600 to-blue-700">
                {/* Animated background pattern */}
                <div className="absolute inset-0 opacity-10">
                    <div className="absolute inset-0 bg-[radial-gradient(circle_at_25%_25%,white_1px,transparent_1px),radial-gradient(circle_at_75%_75%,white_1px,transparent_1px)] bg-size-[50px_50px]" />
                </div>
                <div className="absolute -top-24 -left-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
                <div className="absolute -bottom-24 -right-24 h-96 w-96 rounded-full bg-blue-400/20 blur-3xl" />

                <div className="relative z-10 flex flex-col items-center gap-8 px-12 text-center text-white">
                    <div className="flex h-20 w-20 items-center justify-center rounded-2xl bg-white/15 backdrop-blur-sm shadow-lg ring-1 ring-white/20">
                        <TurLogo size={80} />
                    </div>
                    <div className="space-y-3">
                        <h1 className="text-4xl font-bold tracking-tight">Viglet Turing ES</h1>
                        <p className="text-lg text-white/80 max-w-md leading-relaxed">
                            Enterprise Search Intelligence Platform powered by AI
                        </p>
                    </div>

                    {/* Feature highlights */}
                    <div className="mt-6 grid gap-4 w-full max-w-sm">
                        <div className="flex items-center gap-3 rounded-xl bg-white/10 backdrop-blur-sm px-4 py-3 ring-1 ring-white/10">
                            <Search className="h-5 w-5 shrink-0 text-blue-200" />
                            <span className="text-sm text-white/90">Semantic Search & Navigation</span>
                        </div>
                        <div className="flex items-center gap-3 rounded-xl bg-white/10 backdrop-blur-sm px-4 py-3 ring-1 ring-white/10">
                            <Sparkles className="h-5 w-5 shrink-0 text-purple-200" />
                            <span className="text-sm text-white/90">Generative AI with RAG</span>
                        </div>
                        <div className="flex items-center gap-3 rounded-xl bg-white/10 backdrop-blur-sm px-4 py-3 ring-1 ring-white/10">
                            <Globe className="h-5 w-5 shrink-0 text-cyan-200" />
                            <span className="text-sm text-white/90">Multi-source Knowledge Discovery</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Right panel - Login Form */}
            <div className="relative flex flex-col items-center justify-center bg-background px-6 py-12">
                <div className="absolute top-4 right-4">
                    <ModeToggle />
                </div>
                <div className="w-full max-w-sm">
                    {/* Mobile-only branding */}
                    <div className="mb-8 flex flex-col items-center gap-3 lg:hidden">
                        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-linear-to-br from-blue-600 to-indigo-600 shadow-lg">
                            <TurLogo size={56} />
                        </div>
                        <span className="text-xl font-bold tracking-tight">Viglet Turing ES</span>
                    </div>
                    <LoginForm />
                </div>
            </div>
        </div>
    )
}

export default LoginPage