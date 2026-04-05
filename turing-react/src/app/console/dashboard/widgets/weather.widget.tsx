import type { WeatherData } from "@/app/console/home/use-weather";
import { useWeather } from "@/app/console/home/use-weather";
import {
  IconCloud,
  IconCloudRain,
  IconCloudSnow,
  IconCloudStorm,
  IconDroplet,
  IconHaze,
  IconMist,
  IconSun,
  IconTemperature,
  IconWind,
} from "@tabler/icons-react";
import type { Icon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

function getWeatherIcon(code: number): Icon {
  if (code === 0) return IconSun;
  if (code <= 2) return IconHaze;
  if (code === 3) return IconCloud;
  if (code === 45 || code === 48) return IconMist;
  if (code >= 51 && code <= 67) return IconCloudRain;
  if (code >= 71 && code <= 77) return IconCloudSnow;
  if (code >= 80 && code <= 82) return IconCloudRain;
  if (code >= 95) return IconCloudStorm;
  return IconCloud;
}

function getWeatherGradient(code: number): string {
  if (code === 0) return "from-amber-500 to-orange-500";
  if (code <= 2) return "from-blue-400 to-sky-500";
  if (code === 3) return "from-slate-400 to-slate-500";
  if (code === 45 || code === 48) return "from-slate-300 to-slate-400";
  if (code >= 51 && code <= 67) return "from-blue-500 to-indigo-500";
  if (code >= 71 && code <= 77) return "from-slate-200 to-blue-300";
  if (code >= 80 && code <= 82) return "from-blue-600 to-indigo-600";
  if (code >= 95) return "from-purple-600 to-indigo-700";
  return "from-blue-500 to-indigo-500";
}

interface WeatherDetailsProps {
  weather: WeatherData;
}

function WeatherDetails({ weather }: WeatherDetailsProps) {
  const { t } = useTranslation();
  const WeatherIcon = getWeatherIcon(weather.conditionCode);
  const gradient = getWeatherGradient(weather.conditionCode);
  const conditionKey = `home.weather.condition.${weather.conditionCode}`;
  const condition = t(conditionKey, { defaultValue: t("home.weather.condition.unknown") });

  return (
    <div className="flex flex-col gap-4">
      {/* Main temp */}
      <div className="flex items-center gap-3">
        <div className={`inline-flex rounded-xl bg-gradient-to-br ${gradient} p-3`}>
          <WeatherIcon className="size-8 text-white" />
        </div>
        <div>
          <div className="text-3xl font-bold tabular-nums">{weather.temperature}°C</div>
          <div className="text-xs text-muted-foreground">{condition}</div>
          <div className="text-xs text-muted-foreground mt-0.5">{weather.city}</div>
        </div>
      </div>

      {/* Details grid */}
      <div className="grid grid-cols-3 gap-2">
        <div className="flex flex-col items-center gap-1 rounded-lg bg-secondary/30 p-2">
          <IconTemperature className="size-4 text-muted-foreground" />
          <span className="text-xs font-medium tabular-nums">{weather.feelsLike}°</span>
          <span className="text-[10px] text-muted-foreground">{t("home.weather.feelsLike")}</span>
        </div>
        <div className="flex flex-col items-center gap-1 rounded-lg bg-secondary/30 p-2">
          <IconDroplet className="size-4 text-muted-foreground" />
          <span className="text-xs font-medium tabular-nums">{weather.humidity}%</span>
          <span className="text-[10px] text-muted-foreground">{t("home.weather.humidity")}</span>
        </div>
        <div className="flex flex-col items-center gap-1 rounded-lg bg-secondary/30 p-2">
          <IconWind className="size-4 text-muted-foreground" />
          <span className="text-xs font-medium tabular-nums">{weather.windSpeed}</span>
          <span className="text-[10px] text-muted-foreground">km/h</span>
        </div>
      </div>
    </div>
  );
}

interface WeatherWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function WeatherWidget({ onHide, colSpan }: WeatherWidgetProps) {
  const { t } = useTranslation();
  const { weather, loading } = useWeather();

  return (
    <WidgetWrapper
      id="weather"
      titleKey="dashboard.widgets.weather.title"
      icon={IconSun}
      gradient="from-amber-500 to-orange-500"
      colSpan={colSpan}
      onHide={onHide}
    >
      {loading ? (
        <div className="text-xs text-muted-foreground">{t("dashboard.loading")}</div>
      ) : weather ? (
        <WeatherDetails weather={weather} />
      ) : (
        <p className="text-xs text-muted-foreground">{t("dashboard.widgets.weather.unavailable")}</p>
      )}
    </WidgetWrapper>
  );
}
