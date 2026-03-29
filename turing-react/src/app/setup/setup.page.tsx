import { SetupForm } from "@/components/setup/setup-form"
import { TurLogo } from "@/components/logo/tur-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { Shield } from "lucide-react"
import { useTranslation } from "react-i18next"
import { LoginFloatingBg } from "../login/login-floating-bg"
import { LoginSettingsButton, useLoginSettings } from "../login/login-settings"
import "../login/login-animations.css"

const SetupPage = () => {
    const { t } = useTranslation()
    const { settings, update } = useLoginSettings()
    return (
        <div className={`relative min-h-svh flex items-center justify-center overflow-hidden ${settings.animations ? "bg-slate-50 dark:bg-slate-950" : "bg-blue-200 dark:bg-slate-950"}`}>
            <LoginFloatingBg settings={settings} />

            <div className="absolute top-4 right-4 z-30 flex items-center gap-2">
                <LoginSettingsButton settings={settings} onUpdate={update} />
                <ModeToggle />
            </div>

            <div className="relative z-20 flex flex-col items-center w-full max-w-md px-6 py-4">
                <div className="login-fade-in mb-3">
                    <div className={`${settings.animations ? "login-logo-ring" : ""} flex h-16 w-16 items-center justify-center rounded-2xl bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm shadow-lg ring-1 ring-blue-200/50 dark:ring-blue-500/20`}>
                        <TurLogo size={52} />
                    </div>
                </div>

                <div className="login-fade-in-d1 text-center mb-1">
                    <h1 className="text-2xl font-bold tracking-tight bg-linear-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 bg-clip-text text-transparent">
                        Viglet Turing ES
                    </h1>
                </div>

                <div className="login-fade-in-d1 text-center mb-4">
                    <p className="text-sm text-slate-500 dark:text-slate-400 max-w-xs leading-relaxed">
                        {t("setup.subtitle")}
                    </p>
                </div>

                <div className="login-fade-in-d2 flex flex-wrap justify-center gap-2 mb-5">
                    <div className="login-feature-pill flex items-center gap-1.5 rounded-full px-3 py-1">
                        <Shield className="h-3.5 w-3.5 text-blue-500" />
                        <span className="text-xs font-medium text-slate-600 dark:text-slate-300">{t("setup.firstAccess")}</span>
                    </div>
                </div>

                <div className="login-fade-in-d3 w-full">
                    <div className="login-glass-card rounded-2xl">
                        <SetupForm />
                    </div>
                </div>

                <div className="login-fade-in-d3 mt-4 text-center">
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                        Viglet Turing ES &mdash; Enterprise Search Intelligence
                    </p>
                </div>
            </div>
        </div>
    )
}

export default SetupPage
