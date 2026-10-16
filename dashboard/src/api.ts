import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

export type Credentials = { username: string; password: string }
export type ManagedStore = { id: string; name: string; namespace: string; helmRelease: string; storeUrl: string | null; status: 'PROVISIONING' | 'READY' | 'FAILED' | 'DELETING'; errorMessage: string | null; createdAt: string | null }
export type Page<T> = { items: T[]; hasMore: boolean }
export type Product = { id: number; name: string; price: string; status: string; type: string }
export type Order = { id: number; number: string; status: string; total: string; currency: string; date_created: string }
export type Customer = { id: number; first_name: string; last_name: string; email: string; username: string }
export type BackendStatus = { configuredClients: number; initializedClients: number; available: boolean }
export type GitHubStatus = { service?: string; configuredClients: number; githubConnected: boolean; availableTools: number }
export type GitHubTool = string
export type WeatherStatus = { configuredClients: number; initializedClients: number; available: boolean; tools: string[] }
export type WeatherTool = string
export type WeatherLocation = { name: string; country?: string; admin1?: string; latitude: number; longitude: number; timezone?: string }
export type WeatherCurrent = { time?: string; temperatureC?: number; feelsLikeC?: number; humidityPercent?: number; windSpeedKmh?: number; precipitationMm?: number; isDay?: boolean; weatherCode?: number; conditions?: string }
export type CurrentWeatherResponse = { location?: WeatherLocation; current?: WeatherCurrent; units?: Record<string, string> }
export type DailyForecast = { date: string; conditions?: string; weatherCode?: number; maxTemperatureC?: number; minTemperatureC?: number; precipitationProbabilityPercent?: number; maxWindSpeedKmh?: number }
export type ForecastResponse = { location?: WeatherLocation; forecast?: DailyForecast[]; units?: Record<string, string> }

function config(credentials: Credentials, storeId?: string) {
  return { auth: credentials, ...(storeId ? { params: { storeId } } : {}) }
}

async function list<T>(path: string, credentials: Credentials, page: number, perPage: number, extra = {}, storeId?: string) {
  const response = await api.get<T[]>(path, { auth: credentials, params: { page, perPage, ...extra, ...(storeId ? { storeId } : {}) } })
  const items = response.data
  return { items, hasMore: items.length === perPage }
}

export const commerceApi = {
  status: async (credentials: Credentials) =>
    (await api.get<BackendStatus>('/api/woocommerce/status', config(credentials))).data,
  products: (credentials: Credentials, page: number, search: string, storeId?: string) =>
    list<Product>('/api/woocommerce/products', credentials, page, 10, search ? { search } : {}, storeId),
  product: async (credentials: Credentials, id: number, storeId?: string) =>
    (await api.get<Product>(`/api/woocommerce/products/${id}`, config(credentials, storeId))).data,
  createProduct: async (credentials: Credentials, input: { name: string; regularPrice: string; description: string; type: string }, storeId?: string) =>
    (await api.post<Product>('/api/woocommerce/products', input, config(credentials, storeId))).data,
  orders: (credentials: Credentials, page: number, status: string, storeId?: string) =>
    list<Order>('/api/woocommerce/orders', credentials, page, 10, status ? { status } : {}, storeId),
  order: async (credentials: Credentials, id: number, storeId?: string) =>
    (await api.get<Order>(`/api/woocommerce/orders/${id}`, config(credentials, storeId))).data,
  updateOrderStatus: async (credentials: Credentials, id: number, status: string, storeId?: string) =>
    (await api.put<Order>(`/api/woocommerce/orders/${id}/status`, { status }, config(credentials, storeId))).data,
  customers: (credentials: Credentials, page: number, storeId?: string) =>
    list<Customer>('/api/woocommerce/customers', credentials, page, 10, {}, storeId),
  customer: async (credentials: Credentials, id: number, storeId?: string) =>
    (await api.get<Customer>(`/api/woocommerce/customers/${id}`, config(credentials, storeId))).data,
}

export const storesApi = {
  list: async (credentials: Credentials) => (await api.get<ManagedStore[]>('/api/stores', config(credentials))).data,
  create: async (credentials: Credentials, name: string) => (await api.post<ManagedStore>('/api/stores', { name }, config(credentials))).data,
  retry: async (credentials: Credentials, id: string) => (await api.post<ManagedStore>(`/api/stores/${id}/retry`, {}, config(credentials))).data,
  remove: async (credentials: Credentials, id: string) => (await api.delete<ManagedStore>(`/api/stores/${id}`, config(credentials))).data,
}

export const storeWooApi = {
  tools: async (credentials: Credentials, storeId: string) => (await api.get<string[]>('/api/woocommerce/store-tools', { ...config(credentials), params: { storeId } })).data,
}

export const githubApi = {
  status: async (credentials: Credentials) =>
    (await api.get<GitHubStatus>('/api/github/status', config(credentials))).data,
  tools: async (credentials: Credentials) =>
    (await api.get<GitHubTool[]>('/api/github/tools', config(credentials))).data,
  repositories: async (credentials: Credentials, query: string) =>
    (await api.get<unknown>('/api/github/repositories', { ...config(credentials), params: { query } })).data,
  code: async (credentials: Credentials, query: string) =>
    (await api.get<unknown>('/api/github/code', { ...config(credentials), params: { query } })).data,
  issues: async (credentials: Credentials, owner: string, repo: string, state: string) =>
    (await api.get<unknown>(`/api/github/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/issues`, {
      ...config(credentials), params: { state },
    })).data,
}

export const weatherApi = {
  status: async (credentials: Credentials) =>
    (await api.get<WeatherStatus>('/api/weather/status', config(credentials))).data,
  tools: async (credentials: Credentials) =>
    (await api.get<WeatherTool[]>('/api/weather/tools', config(credentials))).data,
  locations: async (credentials: Credentials, location: string) =>
    (await api.get<{ locations: WeatherLocation[] }>('/api/weather/location', { ...config(credentials), params: { location } })).data,
  current: async (credentials: Credentials, location: string) =>
    (await api.get<CurrentWeatherResponse>('/api/weather/current', { ...config(credentials), params: { location } })).data,
  forecast: async (credentials: Credentials, location: string, days: number) =>
    (await api.get<ForecastResponse>('/api/weather/forecast', { ...config(credentials), params: { location, days } })).data,
}

export function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) return 'Spring Boot rejected these credentials. Use its HTTP Basic login, not the WooCommerce consumer key and secret. Local default: username “user” and the generated password printed when the backend starts; deployed credentials come from SPRING_SECURITY_USER_NAME and SPRING_SECURITY_USER_PASSWORD.'
    if (error.response?.status === 403) return 'The backend authenticated this account but denied access to this action.'
    if (error.response?.status === 502) {
      const detail = error.response?.data?.detail
      return detail ? `MCP request failed: ${String(detail)}` : 'The backend could not complete the request through the MCP server.'
    }
    const detail = error.response?.data?.detail
    if (detail) return String(detail)
    if (error.code === 'ECONNABORTED') return 'The request timed out. Try again.'
    if (!error.response) return 'Cannot reach the backend. Check that Spring Boot is running.'
    if (error.response.status >= 500) return `Spring Boot returned HTTP ${error.response.status}. Check the backend logs for details.`
    if (error.response.status >= 400) return `The backend rejected the request (HTTP ${error.response.status}). Check the request values and current store status.`
  }
  return error instanceof Error ? error.message : 'The request failed.'
}
