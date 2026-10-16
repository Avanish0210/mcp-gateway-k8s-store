import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, commerceApi, errorMessage, githubApi, storesApi } from './api'
import type { Credentials, Customer, GitHubStatus, GitHubTool, ManagedStore, Order, Product } from './api'
import GitHubPanel from './GitHubPanel'
import WeatherPanel from './WeatherPanel'
import ManagedStoresPanel from './ManagedStoresPanel'
import './App.css'

type Section = 'products' | 'orders' | 'customers'
type Dashboard = 'woocommerce' | 'github' | 'weather' | 'stores'
const orderStatuses = ['pending', 'processing', 'on-hold', 'completed', 'cancelled', 'refunded', 'failed']

function App() {
  const [credentials, setCredentials] = useState<Credentials>({ accessToken: '', username: '', role: 'MEMBER' })
  const [loginForm, setLoginForm] = useState({ username: '', password: '' })
  const [connected, setConnected] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [backendStatus, setBackendStatus] = useState<{ configuredClients: number; initializedClients: number; available: boolean } | null>(null)
  const [section, setSection] = useState<Section>('products')
  const [page, setPage] = useState(1)
  const [items, setItems] = useState<Array<Product | Order | Customer>>([])
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [detail, setDetail] = useState<Product | Order | Customer | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [notice, setNotice] = useState('')
  const [dashboard, setDashboard] = useState<Dashboard>('woocommerce')
  const [githubStatus, setGithubStatus] = useState<GitHubStatus | null>(null)
  const [githubTools, setGithubTools] = useState<GitHubTool[]>([])
  const [githubRefreshing, setGithubRefreshing] = useState(false)
  const [managedStores, setManagedStores] = useState<ManagedStore[]>([])
  const [activeStoreId, setActiveStoreId] = useState('')
  const syncManagedStores = useCallback((stores: ManagedStore[]) => {
    setManagedStores(stores)
    setActiveStoreId((current) => current && stores.some((store) => store.id === current && store.status === 'READY') ? current : '')
  }, [])

  useEffect(() => {
    if (!connected || !backendStatus?.available) return
    let active = true
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError('')
      setDetail(null)
      const request = section === 'products'
        ? commerceApi.products(credentials, page, search, activeStoreId || undefined)
        : section === 'orders'
          ? commerceApi.orders(credentials, page, statusFilter, activeStoreId || undefined)
          : commerceApi.customers(credentials, page, activeStoreId || undefined)
      request.then((result) => {
        if (active) { setItems(result.items); setHasMore(result.hasMore) }
      }).catch((reason: unknown) => {
        if (active) { setItems([]); setHasMore(false); setError(errorMessage(reason)) }
      }).finally(() => { if (active) setLoading(false) })
    }, 0)
    return () => { active = false; window.clearTimeout(timer) }
  }, [connected, backendStatus?.available, credentials, section, page, search, statusFilter, activeStoreId])

  async function connect(event: FormEvent) {
    event.preventDefault()
    setConnecting(true)
    setError('')
    try {
      const result = await authApi.login(loginForm.username, loginForm.password)
      const session: Credentials = { accessToken: result.accessToken, username: result.user.username, role: result.user.role }
      setLoginForm({ username: result.user.username, password: '' })
      setCredentials(session)
      setPage(1)
      setConnected(true)
      setDashboard('woocommerce')
      setError('')
      const [woo, github] = await Promise.allSettled([
        commerceApi.status(session),
        Promise.all([githubApi.status(session), githubApi.tools(session)]),
      ])
      if (woo.status === 'fulfilled') setBackendStatus(woo.value)
      if (github.status === 'fulfilled') {
        setGithubStatus(github.value[0])
        setGithubTools(github.value[1])
      }
      if (session.role === 'ADMIN') {
        try { syncManagedStores(await storesApi.list(session)) } catch { syncManagedStores([]) }
      } else syncManagedStores([])
    } catch (reason) {
      setError(errorMessage(reason))
    } finally { setConnecting(false) }
  }

  async function refreshGitHub() {
    setError('')
    setGithubRefreshing(true)
    try {
      const [status, tools] = await Promise.all([githubApi.status(credentials), githubApi.tools(credentials)])
      setGithubStatus(status)
      setGithubTools(tools)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setGithubRefreshing(false) }
  }

  async function openDetail(id: number) {
    setError('')
    setDetail(null)
    setLoading(true)
    try {
      const value = section === 'products' ? await commerceApi.product(credentials, id, activeStoreId || undefined)
        : section === 'orders' ? await commerceApi.order(credentials, id, activeStoreId || undefined)
          : await commerceApi.customer(credentials, id, activeStoreId || undefined)
      setDetail(value)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  async function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError('')
    try {
      await commerceApi.createProduct(credentials, {
        name: String(form.get('name')),
        regularPrice: String(form.get('regularPrice')),
        description: String(form.get('description')),
        type: String(form.get('type')),
      }, activeStoreId || undefined)
      setNotice('Product created.')
      setShowCreate(false)
      setPage(1)
      setSearch('')
      const result = await commerceApi.products(credentials, 1, '', activeStoreId || undefined)
      setItems(result.items)
    } catch (reason) { setError(errorMessage(reason)) }
  }

  async function changeOrderStatus(order: Order, nextStatus: string) {
    setError('')
    try {
      const updated = await commerceApi.updateOrderStatus(credentials, order.id, nextStatus, activeStoreId || undefined)
      setDetail(updated)
      setItems((current) => current.map((item) => 'id' in item && item.id === order.id ? updated : item))
      setNotice(`Order ${order.number || order.id} updated.`)
    } catch (reason) { setError(errorMessage(reason)) }
  }

  const sectionLabel = section[0].toUpperCase() + section.slice(1)
  const detailFields = detail ? Object.entries(detail).filter(([key, value]) =>
    key !== 'id' && value !== null && value !== undefined && typeof value !== 'object' && value !== '',
  ) : []

  if (!credentials.accessToken) return <main className="app-shell auth-shell">
    <header className="auth-brand"><span className="brand-mark">M</span><div><span className="eyebrow">MCP GATEWAY</span><strong>Store Console</strong></div></header>
    <form className="connect-card auth-card" onSubmit={connect}>
      <span className="eyebrow">SECURE WORKSPACE</span>
      <h1>Sign in to your gateway</h1>
      <p>Use your application account to open the connected MCP servers and store tools.</p>
      <div className="credentials auth-fields">
        <label>Username<input autoComplete="username" required autoFocus value={loginForm.username} onChange={(event) => setLoginForm({ ...loginForm, username: event.target.value })} /></label>
        <label>Password<input type="password" autoComplete="current-password" required value={loginForm.password} onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })} /></label>
      </div>
      <button className="primary auth-submit" type="submit" disabled={connecting}>{connecting ? 'Signing in…' : 'Sign in'}</button>
      {error && <div className="error" role="alert">{error}</div>}
      <small className="auth-footnote">Your access is managed by your gateway administrator.</small>
    </form>
  </main>

  return (
    <main className="app-shell">
      <header className="topbar">
        <div><span className="eyebrow">MCP GATEWAY · STORE CONSOLE</span><h1>{dashboard === 'github' ? 'GitHub' : dashboard === 'weather' ? 'Weather MCP' : 'WooCommerce'}</h1></div>
        <div className="user-badge"><span className="user-avatar">{credentials.username.slice(0, 1).toUpperCase()}</span><span><strong>{credentials.username}</strong><small>{credentials.role}</small></span><button className="disconnect" onClick={() => { setConnected(false); setCredentials({ accessToken: '', username: '', role: 'MEMBER' }); setLoginForm({ username: '', password: '' }); setItems([]); setDetail(null); setBackendStatus(null); setGithubStatus(null); setGithubTools([]); syncManagedStores([]); setDashboard('woocommerce'); setError('') }}>Sign out</button></div>
      </header>

      <nav className="tabs dashboard-tabs" aria-label="MCP integrations">
        <button className={dashboard === 'woocommerce' ? 'tab active' : 'tab'} onClick={() => { setDashboard('woocommerce'); setError('') }}>WooCommerce</button>
        <button className={dashboard === 'github' ? 'tab active' : 'tab'} onClick={() => { setDashboard('github'); setError('') }}>GitHub</button>
        <button className={dashboard === 'weather' ? 'tab active' : 'tab'} onClick={() => { setDashboard('weather'); setError('') }}>Weather MCP</button>
        {credentials.role === 'ADMIN' && <button className={dashboard === 'stores' ? 'tab active' : 'tab'} onClick={() => { setDashboard('stores'); setError('') }}>Stores</button>}
      </nav>

      {dashboard === 'stores' && credentials.role === 'ADMIN' ? <ManagedStoresPanel credentials={credentials} onStoresChanged={syncManagedStores} /> : dashboard === 'weather' ? <WeatherPanel credentials={credentials} /> : dashboard === 'github' ? <>
        {githubStatus?.githubConnected ? <GitHubPanel credentials={credentials} status={githubStatus} tools={githubTools} refreshing={githubRefreshing} onRefresh={refreshGitHub} /> : <section className="list-panel integration-unavailable"><div className="panel-heading"><div><span className="eyebrow">MCP SERVER</span><h2>GitHub</h2></div><span className="connection"><span className="status-dot" />Not connected</span></div><p>Sign-in succeeded. The GitHub MCP server is currently unavailable.</p><button className="secondary" onClick={() => void refreshGitHub()} disabled={githubRefreshing}>{githubRefreshing ? 'Checking…' : 'Check connection'}</button></section>}
        {error && <div className="error github-global-error" role="alert">{error}</div>}
      </> : dashboard === 'woocommerce' && !backendStatus?.available ? <section className="list-panel integration-unavailable"><div className="panel-heading"><div><span className="eyebrow">MCP SERVER</span><h2>WooCommerce</h2></div><span className="connection"><span className="status-dot" />Not connected</span></div><p>You are signed in, but the WooCommerce MCP server is unavailable. Check its backend configuration and try again.</p><button className="secondary" onClick={() => void commerceApi.status(credentials).then(setBackendStatus).catch((reason: unknown) => setError(errorMessage(reason)))}>Check connection</button></section> : dashboard === 'woocommerce' ? <>
        <nav className="tabs" aria-label="Store resources">
          {(['products', 'orders', 'customers'] as Section[]).map((item) => <button key={item} className={section === item ? 'tab active' : 'tab'} onClick={() => { setSection(item); setPage(1); setDetail(null); setNotice('') }}>{item}</button>)}
          <label className="store-picker">Store<select value={activeStoreId} onChange={(event) => { setActiveStoreId(event.target.value); setPage(1); setDetail(null) }}><option value="">Store 1 · ecommerce</option>{managedStores.filter((store) => store.namespace !== 'ecommerce' && store.status === 'READY').map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}</select></label>
        </nav>

        <section className="workspace">
          <div className="list-panel">
            <div className="panel-heading"><div><span className="eyebrow">STORE CATALOG</span><h2>{sectionLabel}</h2></div>
              {section === 'products' && credentials.role === 'ADMIN' && <button className="primary" onClick={() => { setShowCreate((value) => !value); setError('') }}>{showCreate ? 'Close' : 'New product'}</button>}
            </div>

            {section === 'products' && <label className="filter">Search products<input value={search} onChange={(event) => { setSearch(event.target.value); setPage(1) }} placeholder="Name or keyword" /></label>}
            {section === 'orders' && <label className="filter">Order status<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1) }}><option value="">All statuses</option>{orderStatuses.map((value) => <option key={value}>{value}</option>)}</select></label>}

            {showCreate && credentials.role === 'ADMIN' && <form className="create-form" onSubmit={createProduct}>
              <h3>Create product</h3>
              <label>Name<input name="name" required maxLength={200} /></label>
              <div className="form-row"><label>Price<input name="regularPrice" inputMode="decimal" pattern="[0-9]+(\.[0-9]{1,2})?" required placeholder="0.00" /></label><label>Type<select name="type"><option value="simple">Simple</option><option value="virtual">Virtual</option><option value="downloadable">Downloadable</option></select></label></div>
              <label>Description<textarea name="description" maxLength={10000} rows={3} /></label>
              <button className="primary" type="submit">Create</button>
            </form>}

            {loading && <p className="muted">Loading…</p>}
            {error && <div className="error" role="alert">{error}</div>}
            {!loading && !error && items.length === 0 && <div className="empty">No {section} found.</div>}
            {!loading && items.length > 0 && <div className="records">{items.map((item) => {
              const id = item.id
              const title = section === 'customers' ? `${(item as Customer).first_name} ${(item as Customer).last_name}`.trim() || (item as Customer).email
                : section === 'orders' ? `Order #${(item as Order).number || id}` : (item as Product).name
              const summary = section === 'customers' ? (item as Customer).email
                : section === 'orders' ? `${(item as Order).currency || ''} ${(item as Order).total || ''} · ${(item as Order).status}`
                  : `${(item as Product).price || '—'} · ${(item as Product).status || ''}`
              return <button className="record" key={id} onClick={() => openDetail(id)}><span><strong>{title}</strong><small>{summary}</small></span><span className="arrow">›</span></button>
            })}</div>}
            <div className="pagination"><button disabled={page <= 1 || loading} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page}</span><button disabled={!hasMore || loading} onClick={() => setPage((value) => value + 1)}>Next</button></div>
          </div>

          <aside className="detail-panel">
            <span className="eyebrow">DETAILS</span>
            {detail ? <><h2>{section === 'products' ? (detail as Product).name : section === 'orders' ? `Order #${(detail as Order).number || detail.id}` : `${(detail as Customer).first_name} ${(detail as Customer).last_name}`}</h2>
              {section === 'orders' && credentials.role === 'ADMIN' && <div className="status-editor"><label>Status<select value={(detail as Order).status} onChange={(event) => changeOrderStatus(detail as Order, event.target.value)}>{orderStatuses.map((value) => <option key={value}>{value}</option>)}</select></label></div>}
              <dl className="detail-fields">{detailFields.map(([key, value]) => <div key={key}><dt>{key.replaceAll('_', ' ')}</dt><dd>{String(value)}</dd></div>)}</dl>
            </> : <p className="muted">Select a {section.slice(0, -1)} to view its details.</p>}
          </aside>
        </section>
      </> : null}
      {notice && <div className="toast" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss">×</button></div>}
    </main>
  )
}

export default App
