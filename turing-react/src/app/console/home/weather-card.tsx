import type { WeatherData } from "./use-weather"
import {
  IconCloud,
  IconCloudRain,
  IconCloudSnow,
  IconCloudStorm,
  IconDroplet,
  IconHaze,
  IconMist,
  IconSun,
  IconWind,
} from "@tabler/icons-react"
import type { Icon } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

function getWeatherIcon(code: number): Icon {
  if (code === 0) return IconSun
  if (code <= 2) return IconHaze
  if (code === 3) return IconCloud
  if (code === 45 || code === 48) return IconMist
  if (code >= 51 && code <= 67) return IconCloudRain
  if (code >= 71 && code <= 77) return IconCloudSnow
  if (code >= 80 && code <= 82) return IconCloudRain
  if (code >= 95) return IconCloudStorm
  return IconCloud
}

function getWeatherGradient(code: number): string {
  if (code === 0) return "from-amber-500 to-orange-500"
  if (code <= 2) return "from-blue-400 to-sky-500"
  if (code === 3) return "from-slate-400 to-slate-500"
  if (code === 45 || code === 48) return "from-slate-300 to-slate-400"
  if (code >= 51 && code <= 67) return "from-blue-500 to-indigo-500"
  if (code >= 71 && code <= 77) return "from-slate-200 to-blue-300"
  if (code >= 80 && code <= 82) return "from-blue-600 to-indigo-600"
  if (code >= 95) return "from-purple-600 to-indigo-700"
  return "from-blue-500 to-indigo-500"
}

interface WeatherCardProps {
  weather: WeatherData
}

export function WeatherCard({ weather }: WeatherCardProps) {
  const { t } = useTranslation()
  const WeatherIcon = getWeatherIcon(weather.conditionCode)
  const gradient = getWeatherGradient(weather.conditionCode)
  const conditionKey = `home.weather.condition.${weather.conditionCode}`
  const condition = t(conditionKey, { defaultValue: t("home.weather.condition.unknown") })

  return (
    <div className="flex items-center gap-4 rounded-xl border bg-card px-5 py-3">
      <div className={`inline-flex rounded-lg bg-gradient-to-br ${gradient} p-2.5`}>
        <WeatherIcon className="size-6 text-white" />
      </div>
      <div className="flex flex-col">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-bold tabular-nums">{weather.temperature}°C</span>
          <span className="text-sm text-muted-foreground">{condition}</span>
        </div>
        <span className="text-xs text-muted-foreground">{weather.city}</span>
      </div>
      <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1" title={t("home.weather.feelsLike")}>
          {t("home.weather.feelsLike")} {weather.feelsLike}°C
        </span>
        <span className="inline-flex items-center gap-1" title={t("home.weather.humidity")}>
          <IconDroplet className="size-3.5" />
          {weather.humidity}%
        </span>
        <span className="inline-flex items-center gap-1" title={t("home.weather.wind")}>
          <IconWind className="size-3.5" />
          {weather.windSpeed} km/h
        </span>
      </div>
    </div>
  )
}
