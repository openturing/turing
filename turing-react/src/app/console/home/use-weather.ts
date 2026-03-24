import { useEffect, useState } from "react"

export interface WeatherData {
  city: string
  temperature: number
  feelsLike: number
  humidity: number
  windSpeed: number
  conditionCode: number
}

async function reverseGeocode(lat: number, lon: number): Promise<string> {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&zoom=10`,
      { headers: { "Accept-Language": navigator.language } }
    )
    const data = await res.json()
    return (
      data.address?.city ||
      data.address?.town ||
      data.address?.village ||
      data.address?.municipality ||
      data.address?.state ||
      "Unknown"
    )
  } catch {
    return "Unknown"
  }
}

async function fetchWeather(lat: number, lon: number): Promise<WeatherData> {
  const url =
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
    `&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m` +
    `&timezone=auto`

  const [weatherRes, city] = await Promise.all([
    fetch(url).then((r) => r.json()),
    reverseGeocode(lat, lon),
  ])

  const current = weatherRes.current
  const code = current.weather_code ?? -1

  return {
    city,
    temperature: Math.round(current.temperature_2m),
    feelsLike: Math.round(current.apparent_temperature),
    humidity: current.relative_humidity_2m,
    windSpeed: Math.round(current.wind_speed_10m),
    conditionCode: code,
  }
}

export function useWeather() {
  const [weather, setWeather] = useState<WeatherData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!navigator.geolocation) {
      setLoading(false)
      return
    }

    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const data = await fetchWeather(pos.coords.latitude, pos.coords.longitude)
          setWeather(data)
        } catch {
          // silently fail — weather is not critical
        } finally {
          setLoading(false)
        }
      },
      () => setLoading(false),
      { timeout: 10000, maximumAge: 300000 }
    )
  }, [])

  return { weather, loading }
}
