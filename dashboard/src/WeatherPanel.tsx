import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Cloud, CloudSun, Droplets, MapPin, RefreshCw, Search, Wind } from 'lucide-react'
import { errorMessage, weatherApi } from './api'
import type { Credentials, CurrentWeatherResponse, DailyForecast, ForecastResponse, WeatherLocation, WeatherStatus, WeatherTool } from './api'

const descriptions: Record<string, string> = {
  search_location: 'Find a place and return its coordinates and timezone.',
  get_current_weather: 'Get current temperature, humidity, wind, and conditions.',
  get_weather_forecast: 'Get a daily forecast for 1 to 7 days.',
}

function number(value: number | undefined, digits = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '—'
}

function placeLabel(place: WeatherLocation) {
  return [place.name, place.admin1, place.country].filter(Boolean).join(', ')
}

function locationResults(value: unknown): WeatherLocation[] {
  if (value && typeof value === 'object' && 'locations' in value && Array.isArray(value.locations)) {
    return value.locations.filter((item): item is WeatherLocation => !!item && typeof item === 'object' && 'name' in item && 'latitude' in item && 'longitude' in item)
  }
  return []
}

export default function WeatherPanel() {
  const [credentials, setCredentials] = useState<Credentials>({ username: '', password: '' })
  const [authenticated, setAuthenticated] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [status, setStatus] = useState<WeatherStatus | null>(null)
  const [tools, setTools] = useState<WeatherTool[]>([])
  const [statusError, setStatusError] = useState('')
  const [query, setQuery] = useState('')
  const [locationLoading, setLocationLoading] = useState(false)
  const [locations, setLocations] = useState<WeatherLocation[]>([])
  const [selected, setSelected] = useState<WeatherLocation | null>(null)
  const [current, setCurrent] = useState<CurrentWeatherResponse | null>(null)
  const [forecast, setForecast] = useState<ForecastResponse | null>(null)
  const [days, setDays] = useState(5)
  const [weatherLoading, setWeatherLoading] = useState(false)
  const [error, setError] = useState('')
  const requestId = useRef(0)
  const searchId = useRef(0)

  async function checkConnection(event?: FormEvent) {
    event?.preventDefault()
    setConnecting(true)
    setStatusError('')
    try {
      const [nextStatus, nextTools] = await Promise.all([weatherApi.status(credentials), weatherApi.tools(credentials)])
      setStatus(nextStatus)
      setTools(nextTools)
      setAuthenticated(true)
      if (!nextStatus.available) setStatusError('The backend is reachable, but the Weather MCP client is not initialized. Check the backend MCP server configuration and retry.')
    } catch (reason) {
      setAuthenticated(false)
      setStatus(null)
      setStatusError(errorMessage(reason))
    } finally { setConnecting(false) }
  }

  async function searchLocation(event: FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) { setError('Enter a city or place name to search.'); return }
    setError('')
    setLocations([])
    setLocationLoading(true)
    const id = ++searchId.current
    try {
      const result = await weatherApi.locations(credentials, trimmed)
      const matches = locationResults(result)
      if (id === searchId.current) {
        setLocations(matches)
        if (!matches.length) setError(`No locations found for “${trimmed}”. Try a nearby city or a more specific name.`)
      }
    } catch (reason) { if (id === searchId.current) setError(errorMessage(reason)) }
    finally { if (id === searchId.current) setLocationLoading(false) }
  }

  async function loadWeather(place: WeatherLocation, duration = days) {
    const id = ++requestId.current
    setSelected(place)
    setError('')
    setWeatherLoading(true)
    try {
      const [nowResult, forecastResult] = await Promise.allSettled([
        weatherApi.current(credentials, place.name), weatherApi.forecast(credentials, place.name, duration),
      ])
      if (id === requestId.current) {
        if (nowResult.status === 'fulfilled') setCurrent(nowResult.value)
        if (forecastResult.status === 'fulfilled') setForecast(forecastResult.value)
        const failures = [nowResult, forecastResult].filter((result) => result.status === 'rejected')
        if (failures.length) setError(errorMessage((failures[0] as PromiseRejectedResult).reason))
      }
    } catch (reason) { if (id === requestId.current) setError(errorMessage(reason)) }
    finally { if (id === requestId.current) setWeatherLoading(false) }
  }

  async function changeDuration(duration: number) {
    setDays(duration)
    if (!selected) return
    const id = ++requestId.current
    setWeatherLoading(true)
    setError('')
    try {
      const result = await weatherApi.forecast(credentials, selected.name, duration)
      if (id === requestId.current) setForecast(result)
    } catch (reason) { if (id === requestId.current) setError(errorMessage(reason)) }
    finally { if (id === requestId.current) setWeatherLoading(false) }
  }

  async function refresh() {
    if (selected) await loadWeather(selected, days)
    else await checkConnection()
  }

  return <>
    {!authenticated ? <form className="connect-card" onSubmit={checkConnection}>
      <span className="eyebrow">WEATHER MCP · GATEWAY CONNECTION</span><h2>Connect to the Weather MCP gateway</h2>
      <p>Sign in with your Spring Boot HTTP Basic credentials. Weather requests are sent through the backend MCP gateway.</p>
      <div className="credentials"><label>Username<input autoComplete="username" required value={credentials.username} onChange={(event) => setCredentials({ ...credentials, username: event.target.value })} /></label><label>Password<input type="password" autoComplete="current-password" required value={credentials.password} onChange={(event) => setCredentials({ ...credentials, password: event.target.value })} /></label></div>
      <button className="primary" type="submit" disabled={connecting}>{connecting ? 'Checking connection…' : 'Connect to Weather MCP'}</button>
      {statusError && <div className="error" role="alert">{statusError}</div>}
    </form> : <>
      <section className="weather-heading">
        <div><span className="eyebrow">MCP GATEWAY · OPEN-METEO</span><h2>Weather MCP</h2><p>Live conditions and daily forecasts retrieved through the Weather MCP gateway.</p></div>
        <div className="weather-heading-actions"><span className="connection"><span className={status?.available ? 'status-dot online' : 'status-dot'} />{connecting ? 'Checking connection…' : status?.available ? 'Connected' : 'Unavailable'}</span><button className="secondary" onClick={() => void refresh()} disabled={connecting || weatherLoading}><RefreshCw size={14} /> {weatherLoading ? 'Refreshing…' : 'Refresh'}</button></div>
      </section>
      {statusError && <div className="connection-diagnostic" role="status"><strong>Weather MCP is unavailable.</strong><span>{statusError}</span><button className="secondary" onClick={() => void checkConnection()} disabled={connecting}>{connecting ? 'Retrying…' : 'Retry connection'}</button></div>}
      <section className="list-panel weather-search-panel">
        <div className="panel-heading"><div><span className="eyebrow">LOCATION</span><h2>Find a place</h2></div></div>
        <form className="weather-search" onSubmit={searchLocation}><label htmlFor="weather-location">City or place name</label><div><input id="weather-location" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="e.g. Indore, India" disabled={!status?.available} /><button className="primary" type="submit" disabled={!status?.available || locationLoading || !query.trim()}><Search size={14} />{locationLoading ? 'Searching…' : 'Search'}</button></div></form>
        {error && <div className="error" role="alert">{error}</div>}
        {locations.length > 0 && <div className="location-results" aria-label="Location search results">{locations.map((place, index) => <button className={selected?.latitude === place.latitude && selected.longitude === place.longitude ? 'location-result selected' : 'location-result'} key={`${place.name}-${place.latitude}-${index}`} onClick={() => void loadWeather(place)}><MapPin size={16} /><span><strong>{placeLabel(place)}</strong><small>{number(place.latitude, 3)}, {number(place.longitude, 3)} · {place.timezone || 'Timezone unavailable'}</small></span></button>)}</div>}
      </section>
      {selected && <>
        <section className="weather-location-banner"><div><span className="eyebrow">SELECTED LOCATION</span><h2>{placeLabel(selected)}</h2><p><MapPin size={13} /> {number(selected.latitude, 4)}, {number(selected.longitude, 4)} <span>·</span> {selected.timezone || 'Timezone unavailable'}</p></div><span className="weather-updated">{current?.current?.time ? `Observed ${new Date(current.current.time).toLocaleString()}` : weatherLoading ? 'Loading weather…' : 'Observation time unavailable'}</span></section>
        {weatherLoading && <p className="muted" role="status">Loading current weather and forecast…</p>}
        {current?.current && <section className="weather-current-grid" aria-label="Current weather">
          <article className="weather-current-card weather-primary-card"><span className="eyebrow">CURRENT CONDITIONS</span><div className="temperature-row"><CloudSun size={35} /><strong>{number(current.current.temperatureC, 1)}<small>°C</small></strong></div><h3>{current.current.conditions || 'Conditions unavailable'}</h3><p>{current.current.isDay === undefined ? 'Day/night unavailable' : current.current.isDay ? 'Daytime' : 'Nighttime'}</p></article>
          <WeatherMetric icon={<Cloud size={17} />} label="Feels like" value={`${number(current.current.feelsLikeC, 1)} °C`} />
          <WeatherMetric icon={<Droplets size={17} />} label="Humidity" value={`${number(current.current.humidityPercent)}%`} />
          <WeatherMetric icon={<Wind size={17} />} label="Wind speed" value={`${number(current.current.windSpeedKmh, 1)} km/h`} />
          <WeatherMetric icon={<Droplets size={17} />} label="Precipitation" value={`${number(current.current.precipitationMm, 1)} mm`} />
        </section>}
        <section className="list-panel forecast-panel"><div className="panel-heading"><div><span className="eyebrow">DAILY OUTLOOK</span><h2>Forecast</h2></div><div className="duration-picker" aria-label="Forecast duration">{[1, 3, 5, 7].map((duration) => <button key={duration} className={days === duration ? 'duration active' : 'duration'} aria-pressed={days === duration} disabled={weatherLoading} onClick={() => void changeDuration(duration)}>{duration} day{duration > 1 ? 's' : ''}</button>)}</div></div>
          {forecast?.forecast?.length ? <div className="forecast-grid">{forecast.forecast.map((day: DailyForecast, index) => <article className={index === 0 ? 'forecast-card forecast-today' : 'forecast-card'} key={`${day.date}-${index}`}><span className="eyebrow">{index === 0 ? 'TODAY' : new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short' }).toUpperCase()}</span><time dateTime={day.date}>{new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</time><CloudSun size={25} /><strong>{day.conditions || 'Conditions unavailable'}</strong><div className="forecast-temperatures"><span>{number(day.minTemperatureC)}°</span><b>{number(day.maxTemperatureC)}°C</b></div><small><Droplets size={12} /> {number(day.precipitationProbabilityPercent)}% precipitation</small><small><Wind size={12} /> {number(day.maxWindSpeedKmh, 1)} km/h max wind</small></article>)}</div> : !weatherLoading && <p className="muted">Forecast data is unavailable for this location.</p>}
        </section>
      </>}
      <section className="list-panel weather-tools-panel"><div className="panel-heading"><div><span className="eyebrow">GATEWAY CAPABILITIES</span><h2>Weather MCP tools</h2></div><span className="tool-count">{tools.length} reported</span></div>{tools.length ? <div className="weather-tool-list">{tools.map((tool) => <article key={tool}><code>{tool}</code><p>{descriptions[tool] || 'Tool reported by the Weather MCP gateway.'}</p></article>)}</div> : <p className="muted">The backend reports no Weather MCP tools.</p>}</section>
    </>}
  </>
}

function WeatherMetric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <article className="weather-metric-card"><span>{icon}{label}</span><strong>{value}</strong></article>
}
