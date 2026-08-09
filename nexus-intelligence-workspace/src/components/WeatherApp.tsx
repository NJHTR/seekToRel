import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import {
  Cloud,
  CloudDrizzle,
  CloudFog,
  CloudLightning,
  CloudRain,
  CloudSnow,
  Droplets,
  LocateFixed,
  RefreshCw,
  Sun,
  Sunrise,
  Sunset,
  Wind
} from 'lucide-react'
import '../weather.css'

type WeatherReading = {
  temperature: number
  apparentTemperature: number
  humidity: number
  windSpeed: number
  precipitation: number
  weatherCode: number
  isDay: boolean
}

type Hour = { time: string; temperature: number; weatherCode: number; isDay: boolean }
type Day = { date: string; weatherCode: number; high: number; low: number; precipitation: number; sunrise: string; sunset: string }
type WeatherData = { current: WeatherReading; hourly: Hour[]; daily: Day[]; updatedAt: Date; source: 'live' | 'offline' }

const guangzhou = { latitude: 23.1291, longitude: 113.2644, name: 'Guangzhou, Guangdong' }

function round(value: number) {
  return Math.round(value)
}

function codeMeta(code: number, isDay = true) {
  if (code === 0) return { label: isDay ? 'Clear' : 'Clear night', Icon: isDay ? Sun : Sunrise }
  if (code <= 2) return { label: 'Partly cloudy', Icon: Cloud }
  if (code === 3) return { label: 'Overcast', Icon: Cloud }
  if (code === 45 || code === 48) return { label: 'Foggy', Icon: CloudFog }
  if (code <= 57) return { label: 'Drizzle', Icon: CloudDrizzle }
  if (code <= 67 || code === 80 || code === 81 || code === 82) return { label: 'Rain showers', Icon: CloudRain }
  if (code <= 77 || code === 85 || code === 86) return { label: 'Snow showers', Icon: CloudSnow }
  if (code >= 95) return { label: 'Thunderstorms', Icon: CloudLightning }
  return { label: 'Cloudy', Icon: Cloud }
}

function formatHour(value: string) {
  return new Intl.DateTimeFormat('en-US', { hour: 'numeric' }).format(new Date(value))
}

function formatDay(value: string, index: number) {
  if (index === 0) return 'Today'
  if (index === 1) return 'Tomorrow'
  return new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(new Date(`${value}T12:00:00`))
}

function fallbackWeather(): WeatherData {
  const now = new Date()
  const hour = now.getHours()
  const current: WeatherReading = {
    temperature: 30,
    apparentTemperature: 35,
    humidity: 76,
    windSpeed: 10,
    precipitation: 15,
    weatherCode: hour > 18 || hour < 6 ? 1 : 2,
    isDay: hour >= 6 && hour < 18
  }
  const hourly = [0, 3, 6, 9, 12, 15, 18, 21].map((offset) => {
    const point = new Date(now)
    point.setHours(now.getHours() + offset, 0, 0, 0)
    return { time: point.toISOString(), temperature: 29 + Math.round(Math.sin((point.getHours() - 8) / 24 * Math.PI * 2) * 3), weatherCode: point.getHours() >= 11 && point.getHours() <= 17 ? 2 : 1, isDay: point.getHours() >= 6 && point.getHours() < 18 }
  })
  const daily = Array.from({ length: 7 }, (_, index) => {
    const point = new Date(now)
    point.setDate(now.getDate() + index)
    const weatherCode = [2, 61, 80, 3, 1, 2, 51][index]
    return { date: point.toISOString().slice(0, 10), weatherCode, high: 32 - (index % 3), low: 26 + (index % 2), precipitation: [15, 54, 62, 25, 12, 18, 38][index], sunrise: `${point.toISOString().slice(0, 10)}T06:05`, sunset: `${point.toISOString().slice(0, 10)}T18:58` }
  })
  return { current, hourly, daily, updatedAt: now, source: 'offline' }
}

function readWeather(payload: any): WeatherData {
  const now = new Date()
  const current = payload.current
  const hourlyIndexes = payload.hourly.time.map((time: string, index: number) => ({ time, index })).filter(({ time }: { time: string }) => new Date(time).getTime() >= now.getTime()).slice(0, 8)
  return {
    current: {
      temperature: current.temperature_2m,
      apparentTemperature: current.apparent_temperature,
      humidity: current.relative_humidity_2m,
      windSpeed: current.wind_speed_10m,
      precipitation: current.precipitation_probability,
      weatherCode: current.weather_code,
      isDay: current.is_day === 1
    },
    hourly: hourlyIndexes.map(({ time, index }: { time: string; index: number }) => ({ time, temperature: payload.hourly.temperature_2m[index], weatherCode: payload.hourly.weather_code[index], isDay: new Date(time).getHours() >= 6 && new Date(time).getHours() < 18 })),
    daily: payload.daily.time.map((date: string, index: number) => ({ date, weatherCode: payload.daily.weather_code[index], high: payload.daily.temperature_2m_max[index], low: payload.daily.temperature_2m_min[index], precipitation: payload.daily.precipitation_probability_max[index], sunrise: payload.daily.sunrise[index], sunset: payload.daily.sunset[index] })),
    updatedAt: now,
    source: 'live'
  }
}

async function requestWeather(signal: AbortSignal) {
  const query = new URLSearchParams({
    latitude: String(guangzhou.latitude), longitude: String(guangzhou.longitude), timezone: 'Asia/Shanghai', forecast_days: '7',
    current: 'temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,precipitation_probability,weather_code,is_day',
    hourly: 'temperature_2m,weather_code',
    daily: 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset'
  })
  const response = await fetch(`https://api.open-meteo.com/v1/forecast?${query}`, { signal })
  if (!response.ok) throw new Error(`Weather service returned ${response.status}`)
  return readWeather(await response.json())
}

export function WeatherApp() {
  const [weather, setWeather] = useState<WeatherData>(fallbackWeather)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [selectedDay, setSelectedDay] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    requestWeather(controller.signal).then((data) => { setWeather(data); setFailed(false) }).catch((error: unknown) => { if (!(error instanceof DOMException && error.name === 'AbortError')) { setWeather(fallbackWeather()); setFailed(true) } }).finally(() => setLoading(false))
    return () => controller.abort()
  }, [])

  async function refresh() {
    setLoading(true)
    try { setWeather(await requestWeather(new AbortController().signal)); setFailed(false) } catch { setWeather(fallbackWeather()); setFailed(true) } finally { setLoading(false) }
  }

  const currentMeta = codeMeta(weather.current.weatherCode, weather.current.isDay)
  const CurrentIcon = currentMeta.Icon
  const selected = weather.daily[selectedDay] ?? weather.daily[0]
  const selectedMeta = codeMeta(selected.weatherCode)
  const sunrise = useMemo(() => new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date(selected.sunrise)), [selected.sunrise])
  const sunset = useMemo(() => new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date(selected.sunset)), [selected.sunset])

  return <div className="weather-app" aria-busy={loading}>
    <header className="weather-header">
      <div><span className="weather-eyebrow"><LocateFixed size={13} fill="currentColor" /> My Location</span><h1>{guangzhou.name}</h1><p>{currentMeta.label} <span>•</span> Feels like {round(weather.current.apparentTemperature)}°</p></div>
      <button className="weather-refresh" onClick={refresh} disabled={loading} aria-label="Refresh weather" title="Refresh weather"><RefreshCw size={17} className={loading ? 'weather-spin' : ''} /></button>
    </header>

    {failed && <div className="weather-offline"><span>Showing the local Guangzhou forecast</span><small>Live weather is temporarily unavailable.</small></div>}

    <section className="weather-hero">
      <div className="weather-hero-copy"><strong>{round(weather.current.temperature)}°</strong><span>{currentMeta.label}</span><p>H:{round(selected.high)}° &nbsp; L:{round(selected.low)}°</p></div>
      <CurrentIcon className="weather-hero-icon" strokeWidth={1.45} />
      <div className="weather-glow" aria-hidden="true" />
    </section>

    <section className="weather-panel weather-hourly"><h2>HOURLY FORECAST</h2><div className="weather-hour-list">{weather.hourly.map((hour, index) => { const meta = codeMeta(hour.weatherCode, hour.isDay); const Icon = meta.Icon; return <article key={hour.time} className="weather-hour"><span>{index === 0 ? 'Now' : formatHour(hour.time)}</span><Icon size={25} strokeWidth={1.7} /><strong>{round(hour.temperature)}°</strong></article> })}</div></section>

    <section className="weather-panel weather-daily"><h2>10-DAY FORECAST</h2>{weather.daily.map((day, index) => { const meta = codeMeta(day.weatherCode); const Icon = meta.Icon; return <button key={day.date} className={`weather-day ${selectedDay === index ? 'weather-day-selected' : ''}`} onClick={() => setSelectedDay(index)}><span>{formatDay(day.date, index)}</span><Icon size={22} strokeWidth={1.8} /><span className="weather-rain"><Droplets size={12} fill="currentColor" /> {round(day.precipitation)}%</span><div className="weather-temperature-range"><em>{round(day.low)}°</em><i style={{ '--weather-range-start': `${Math.max(0, Math.min(50, day.low - 15))}%`, '--weather-range-width': `${Math.max(25, Math.min(70, day.high - day.low + 28))}%` } as CSSProperties} /><strong>{round(day.high)}°</strong></div></button> })}</section>

    <section className="weather-detail-grid">
      <article className="weather-detail"><span><Wind size={16} /> WIND</span><strong>{round(weather.current.windSpeed)} <small>km/h</small></strong><p>Gentle breeze</p></article>
      <article className="weather-detail"><span><Droplets size={16} /> HUMIDITY</span><strong>{round(weather.current.humidity)}<small>%</small></strong><p>Comfortable indoors</p></article>
      <article className="weather-detail weather-sun-card"><span><Sunrise size={16} /> SUNRISE & SUNSET</span><div><p><Sunrise size={17} /> {sunrise}</p><p><Sunset size={17} /> {sunset}</p></div><small>{selectedMeta.label}</small></article>
    </section>

    <footer className="weather-footer">Updated {new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(weather.updatedAt)} <span>•</span> {weather.source === 'live' ? 'Open-Meteo' : 'Offline forecast'}</footer>
  </div>
}
