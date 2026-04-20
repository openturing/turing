import { LoginForm } from "@/components/login/login-form"
import { TurLogo } from "@/components/logo/tur-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { Login } from "@viglet/viglet-design-system"
import { Globe, Search, Sparkles } from "lucide-react"
import { useTranslation } from "react-i18next"
import { LoginSettingsButton, useLoginSettings } from "./login-settings"

const LoginPage = () => {
  const { t } = useTranslation()
  const { settings, update } = useLoginSettings()

  return (
    <Login color="#2563eb" colorDark="#60a5fa">
      <Login.Background
        withFormulas={settings.formulas}
        withBonds={settings.formulas}
        withLightning={settings.animations}
        withExplosion={settings.explosion}
        withOrbs={settings.animations}
        withGrid
      />

      <Login.Settings>
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </Login.Settings>

      <Login.Content>
        <Login.Logo pulsePaused={!settings.animations}>
          <TurLogo size={52} />
        </Login.Logo>

        <Login.Title>Viglet Turing ES</Login.Title>
        <Login.Tagline>{t("login.heroTagline")}</Login.Tagline>

        <Login.Features>
          <Login.FeaturePill icon={<Search className="h-3.5 w-3.5 text-blue-500" />}>
            {t("login.featureSearch")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Sparkles className="h-3.5 w-3.5 text-indigo-500" />}>
            {t("login.featureAi")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Globe className="h-3.5 w-3.5 text-cyan-500" />}>
            {t("login.featureKnowledge")}
          </Login.FeaturePill>
        </Login.Features>

        <Login.Card>
          <LoginForm />
        </Login.Card>

        <Login.Footer>Viglet Turing ES &mdash; Enterprise Search Intelligence</Login.Footer>
      </Login.Content>
    </Login>
  )
}

export default LoginPage
