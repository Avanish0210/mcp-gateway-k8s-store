import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, commerceApi, errorMessage, githubApi, storesApi } from './api'
import type { Credentials, Customer, GitHubStatus, GitHubTool, ManagedStore, Order, Product } from './api'
import GitHubPanel from './GitHubPanel'
import WeatherPanel from './WeatherPanel'
import ManagedStoresPanel from './ManagedStoresPanel'
import ApiKeysPanel from './ApiKeysPanel'
import TeamPanel from './TeamPanel'
import AuditPanel from './AuditPanel'
import DashboardOverview from './DashboardOverview'
import ChatPanel from './ChatPanel'
import { ChevronDown } from 'lucide-react'
import './App.css'
import './DashboardOverview.css'

type Section = 'products' | 'orders' | 'customers'
type Dashboard = 'overview' | 'chat' | 'woocommerce' | 'github' | 'weather' | 'stores' | 'api-keys' | 'team' | 'audit'
const orderStatuses = ['pending', 'processing', 'on-hold', 'completed', 'cancelled', 'refunded', 'failed']

function App() {
  const [credentials, setCredentials] = useState<Credentials>({ accessToken: '', username: '', role: 'MEMBER' })
  const [loginForm, setLoginForm] = useState({ username: '', password: '' })
  const [acceptForm, setAcceptForm] = useState({ username: '', password: '' })
  const [invitationAccepted, setInvitationAccepted] = useState(false)
  const invitationToken = new URLSearchParams(window.location.search).get('token') || ''
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
  const [dashboard, setDashboard] = useState<Dashboard>('overview')
  const [githubStatus, setGithubStatus] = useState<GitHubStatus | null>(null)
  const [githubTools, setGithubTools] = useState<GitHubTool[]>([])
  const [githubRefreshing, setGithubRefreshing] = useState(false)
  const [managedStores, setManagedStores] = useState<ManagedStore[]>([])
  const [activeStoreId, setActiveStoreId] = useState('')
  const syncManagedStores = useCallback((stores: ManagedStore[]) => {
    setManagedStores(stores)
  }, [])

  useEffect(() => {
    if (!connected || !backendStatus?.available || (activeStoreId && !managedStores.some((store) => store.id === activeStoreId && store.status === 'READY'))) return
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
  }, [connected, backendStatus?.available, credentials, section, page, search, statusFilter, activeStoreId, managedStores])

  useEffect(() => {
    if (!connected) return
    let active = true
    const refresh = async () => {
      try {
        const status = await commerceApi.status(credentials, activeStoreId || undefined)
        if (active) setBackendStatus(status)
      } catch { if (active) setBackendStatus(null) }
      if (credentials.role === 'ADMIN') {
        try { const stores = await storesApi.list(credentials); if (active) syncManagedStores(stores) } catch { /* keep the selected store */ }
      }
    }
    void refresh()
    const timer = window.setInterval(() => { void refresh() }, 15000)
    return () => { active = false; window.clearInterval(timer) }
  }, [connected, credentials, activeStoreId, syncManagedStores])

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
      setDashboard('overview')
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

  async function acceptInvitation(event: FormEvent) {
    event.preventDefault()
    setError('')
    try {
      const result = await authApi.acceptInvitation(invitationToken, acceptForm.username, acceptForm.password)
      setInvitationAccepted(true)
      setLoginForm({ username: result.username, password: '' })
      setAcceptForm({ username: '', password: '' })
      window.history.replaceState({}, '', window.location.pathname)
    } catch (reason) { setError(errorMessage(reason)) }
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

  function signOut() {
    setConnected(false)
    setCredentials({ accessToken: '', username: '', role: 'MEMBER' })
    setLoginForm({ username: '', password: '' })
    setItems([])
    setDetail(null)
    setBackendStatus(null)
    setGithubStatus(null)
    setGithubTools([])
    syncManagedStores([])
    setDashboard('overview')
    setError('')
  }

  if (!credentials.accessToken && invitationToken && !invitationAccepted) return <main className="app-shell auth-shell">
    <header className="auth-brand"><span className="brand-mark">M</span><div><span className="eyebrow">MCP GATEWAY</span><strong>Team invitation</strong></div></header>
    <form className="connect-card auth-card" onSubmit={acceptInvitation}>
      <span className="eyebrow">JOIN THE TEAM</span><h1>Accept your invitation</h1>
      <p>Choose a username and password. Your account will be created with the MEMBER role.</p>
      <div className="credentials auth-fields"><label>Username<input autoComplete="username" minLength={3} maxLength={80} required autoFocus value={acceptForm.username} onChange={(event) => setAcceptForm({ ...acceptForm, username: event.target.value })} /></label>
        <label>Password<input type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={acceptForm.password} onChange={(event) => setAcceptForm({ ...acceptForm, password: event.target.value })} /></label></div>
      <button className="primary auth-submit" type="submit">Create member account</button>
      {error && <div className="error" role="alert">{error}</div>}
      <small className="auth-footnote">Use a unique password with at least 12 characters.</small>
    </form>
  </main>

  if (!credentials.accessToken && invitationAccepted) return <main className="app-shell auth-shell">
    <header className="auth-brand"><span className="brand-mark">M</span><div><span className="eyebrow">MCP GATEWAY</span><strong>Team invitation</strong></div></header>
    <section className="connect-card auth-card"><span className="eyebrow">ACCOUNT CREATED</span><h1>You’re on the team</h1><p>Your MEMBER account is ready. Sign in with the username and password you just created.</p><button className="primary auth-submit" onClick={() => { setInvitationAccepted(false); setError('') }}>Continue to sign in</button></section>
  </main>

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
      <header className={`topbar ${dashboard === 'overview' ? 'overview-topbar' : ''}`}>
        <div className="topbar-copy"><span className="eyebrow">MCP GATEWAY · STORE CONSOLE</span><h1>{dashboard === 'overview' ? `Welcome back, ${credentials.username}!` : dashboard === 'github' ? 'GitHub' : dashboard === 'weather' ? 'Weather MCP' : dashboard === 'stores' ? 'Managed stores' : dashboard === 'api-keys' ? 'API keys' : dashboard === 'team' ? 'Team management' : dashboard === 'audit' ? 'Audit log' : 'WooCommerce'}</h1>{dashboard === 'overview' && <p>Here’s what’s happening with your MCP platform today.</p>}</div>
        <details className="profile-menu"><summary aria-label="Open profile menu"><span className="user-avatar">{credentials.username.slice(0, 1).toUpperCase()}</span><span className="profile-copy"><strong>{credentials.username}</strong><small>{credentials.role}</small></span><ChevronDown size={15} /></summary><div className="profile-dropdown"><div className="profile-dropdown-heading"><strong>{credentials.username}</strong><small>{credentials.role === 'ADMIN' ? 'Administrator' : 'Team member'}</small></div><button onClick={() => setDashboard('api-keys')}>API keys</button>{credentials.role === 'ADMIN' && <button onClick={() => setDashboard('team')}>Team management</button>}<button onClick={() => setDashboard('audit')}>Audit log</button><div className="profile-divider" /><button className="profile-signout" onClick={signOut}>Sign out</button></div></details>
      </header>

      <nav className="tabs dashboard-tabs" aria-label="Workspace sections">
        <button className={dashboard === 'overview' ? 'tab active' : 'tab'} onClick={() => { setDashboard('overview'); setError('') }}>Overview</button>
        <button className={dashboard === 'chat' ? 'tab active' : 'tab'} onClick={() => { setDashboard('chat'); setError('') }}>AI Chat</button>
        <button className={dashboard === 'woocommerce' ? 'tab active' : 'tab'} onClick={() => { setDashboard('woocommerce'); setError('') }}>WooCommerce</button>
        <button className={dashboard === 'github' ? 'tab active' : 'tab'} onClick={() => { setDashboard('github'); setError('') }}>GitHub</button>
        <button className={dashboard === 'weather' ? 'tab active' : 'tab'} onClick={() => { setDashboard('weather'); setError('') }}>Weather MCP</button>
        {credentials.role === 'ADMIN' && <button className={dashboard === 'stores' ? 'tab active' : 'tab'} onClick={() => { setDashboard('stores'); setError('') }}>Stores</button>}
        {credentials.role === 'ADMIN' && <button className={dashboard === 'team' ? 'tab active' : 'tab'} onClick={() => { setDashboard('team'); setError('') }}>Team</button>}
        <button className={dashboard === 'api-keys' ? 'tab active' : 'tab'} onClick={() => { setDashboard('api-keys'); setError('') }}>API keys</button>
        <button className={dashboard === 'audit' ? 'tab active' : 'tab'} onClick={() => { setDashboard('audit'); setError('') }}>Audit log</button>
      </nav>

      {(dashboard === 'woocommerce' || dashboard === 'chat') && <label className="store-picker">Store<select value={activeStoreId} onChange={(event) => { setActiveStoreId(event.target.value); setItems([]); setBackendStatus(null); setPage(1); setDetail(null) }}><option value="">Store 1 · ecommerce</option>{activeStoreId && !managedStores.some((store) => store.id === activeStoreId) && <option value={activeStoreId} disabled>Deleted store - select another store</option>}{managedStores.filter((store) => store.namespace !== 'ecommerce').map((store) => <option key={store.id} value={store.id} disabled={store.status !== 'READY'}>{store.name} · {store.status}</option>)}</select></label>}
      {dashboard === 'overview' ? <DashboardOverview credentials={credentials} onNavigate={(next) => { setDashboard(next); setError('') }} /> : dashboard === 'chat' ? <ChatPanel key={activeStoreId} credentials={credentials} storeId={activeStoreId || undefined} /> : dashboard === 'audit' ? <AuditPanel credentials={credentials} /> : dashboard === 'team' && credentials.role === 'ADMIN' ? <TeamPanel credentials={credentials} /> : dashboard === 'api-keys' ? <ApiKeysPanel credentials={credentials} /> : dashboard === 'stores' && credentials.role === 'ADMIN' ? <ManagedStoresPanel credentials={credentials} onStoresChanged={syncManagedStores} /> : dashboard === 'weather' ? <WeatherPanel credentials={credentials} /> : dashboard === 'github' ? <>
        {githubStatus?.githubConnected ? <GitHubPanel credentials={credentials} status={githubStatus} tools={githubTools} refreshing={githubRefreshing} onRefresh={refreshGitHub} /> : <section className="list-panel integration-unavailable"><div className="panel-heading"><div><span className="eyebrow">MCP SERVER</span><h2>GitHub</h2></div><span className="connection"><span className="status-dot" />Not connected</span></div><p>Sign-in succeeded. The GitHub MCP server is currently unavailable.</p><button className="secondary" onClick={() => void refreshGitHub()} disabled={githubRefreshing}>{githubRefreshing ? 'Checking…' : 'Check connection'}</button></section>}
        {error && <div className="error github-global-error" role="alert">{error}</div>}
      </> : dashboard === 'woocommerce' && !backendStatus?.available ? <section className="list-panel integration-unavailable"><div className="panel-heading"><div><span className="eyebrow">MCP SERVER</span><h2>WooCommerce</h2></div><span className="connection"><span className="status-dot" />Not connected</span></div><p>You are signed in, but the WooCommerce MCP server is unavailable. Check its backend configuration and try again.</p><button className="secondary" onClick={() => void commerceApi.status(credentials, activeStoreId || undefined).then(setBackendStatus).catch((reason: unknown) => setError(errorMessage(reason)))}>Check connection</button></section> : dashboard === 'woocommerce' ? <>
        <nav className="tabs" aria-label="Store resources">
          {(['products', 'orders', 'customers'] as Section[]).map((item) => <button key={item} className={section === item ? 'tab active' : 'tab'} onClick={() => { setSection(item); setPage(1); setDetail(null); setNotice('') }}>{item}</button>)}

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
