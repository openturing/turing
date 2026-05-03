import { SetupForm } from "@/components/setup/setup-form"
import { IconShield } from "@tabler/icons-react";
import { TurLogo } from "@/components/logo/tur-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { StartupFirst } from "@viglet/viglet-design-system"
import { useTranslation } from "react-i18next"
import { LoginSettingsButton, useLoginSettings } from "../login/login-settings"

const SetupPage = () => {
  const { t } = useTranslation()
  const { settings, update } = useLoginSettings()

  return (
    <StartupFirst color="#2563eb" colorDark="#60a5fa">
      <StartupFirst.Background
        withFormulas={settings.formulas}
        withBonds={settings.formulas}
        withLightning={settings.animations}
        withExplosion={settings.explosion}
        withOrbs={settings.animations}
        withGrid
      />

      <div className="absolute top-4 right-4 z-30 flex items-center gap-2">
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </div>

      <StartupFirst.Content>
        <StartupFirst.Logo size={64} pulsePaused={!settings.animations}>
          <TurLogo size={52} />
        </StartupFirst.Logo>

        <StartupFirst.Title>Viglet Turing ES</StartupFirst.Title>
        <StartupFirst.Description>{t("setup.subtitle")}</StartupFirst.Description>

        <div className="flex flex-wrap justify-center gap-2 mb-5">
          <div className="vig-login__feature-pill flex items-center gap-1.5 rounded-full px-3 py-1">
            <IconShield className="h-3.5 w-3.5 text-blue-500" />
            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
              {t("setup.firstAccess")}
            </span>
          </div>
        </div>

        <StartupFirst.Card>
          <SetupForm />
        </StartupFirst.Card>

        <StartupFirst.Footer>Viglet Turing ES &mdash; Enterprise Search Intelligence</StartupFirst.Footer>
      </StartupFirst.Content>
    </StartupFirst>
  )
}

export default SetupPage
