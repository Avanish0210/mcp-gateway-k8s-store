import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

export type Credentials = { username: string; password: string }
export type Page<T> = { items: T[]; hasMore: boolean }
export type Product = { id: number; name: string; price: string; status: string; type: string }
export type Order = { id: number; number: string; status: string; total: string; currency: string; date_created: string }
export type Customer = { id: number; first_name: string; last_name: string; email: string; username: string }
export type BackendStatus = { configuredClients: number; initializedClients: number; available: boolean }
export type GitHubStatus = { service?: string; configuredClients: number; githubConnected: boolean; availableTools: number }
export type GitHubTool = string

function config(credentials: Credentials) {
  return { auth: credentials }
}

async function list<T>(path: string, credentials: Credentials, page: number, perPage: number, extra = {}) {
  const response = await api.get<T[]>(path, { ...config(credentials), params: { page, perPage, ...extra } })
  const items = response.data
  return { items, hasMore: items.length === perPage }
}

export const commerceApi = {
  status: async (credentials: Credentials) =>
    (await api.get<BackendStatus>('/api/woocommerce/status', config(credentials))).data,
  products: (credentials: Credentials, page: number, search: string) =>
    list<Product>('/api/woocommerce/products', credentials, page, 10, search ? { search } : {}),
  product: async (credentials: Credentials, id: number) =>
    (await api.get<Product>(`/api/woocommerce/products/${id}`, config(credentials))).data,
  createProduct: async (credentials: Credentials, input: { name: string; regularPrice: string; description: string; type: string }) =>
    (await api.post<Product>('/api/woocommerce/products', input, config(credentials))).data,
  orders: (credentials: Credentials, page: number, status: string) =>
    list<Order>('/api/woocommerce/orders', credentials, page, 10, status ? { status } : {}),
  order: async (credentials: Credentials, id: number) =>
    (await api.get<Order>(`/api/woocommerce/orders/${id}`, config(credentials))).data,
  updateOrderStatus: async (credentials: Credentials, id: number, status: string) =>
    (await api.put<Order>(`/api/woocommerce/orders/${id}/status`, { status }, config(credentials))).data,
  customers: (credentials: Credentials, page: number) =>
    list<Customer>('/api/woocommerce/customers', credentials, page, 10),
  customer: async (credentials: Credentials, id: number) =>
    (await api.get<Customer>(`/api/woocommerce/customers/${id}`, config(credentials))).data,
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
  }
  return error instanceof Error ? error.message : 'The request failed.'
}
