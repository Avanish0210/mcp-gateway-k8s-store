import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { commerceApi, errorMessage } from './api'
import type { Credentials, Customer, Order, Product } from './api'
import './App.css'

type Section = 'products' | 'orders' | 'customers'
const orderStatuses = ['pending', 'processing', 'on-hold', 'completed', 'cancelled', 'refunded', 'failed']

function App() {
  const [credentials, setCredentials] = useState<Credentials>({ username: '', password: '' })
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

  useEffect(() => {
    if (!connected) return
    let active = true
    setLoading(true)
    setError('')
    setDetail(null)
    const request = section === 'products'
      ? commerceApi.products(credentials, page, search)
      : section === 'orders'
        ? commerceApi.orders(credentials, page, statusFilter)
        : commerceApi.customers(credentials, page)
    request.then((result) => {
      if (active) { setItems(result.items); setHasMore(result.hasMore) }
    }).catch((reason: unknown) => {
      if (active) { setItems([]); setHasMore(false); setError(errorMessage(reason)) }
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [connected, credentials, section, page, search, statusFilter])

  async function connect(event: FormEvent) {
    event.preventDefault()
    setPage(1)
    setConnecting(true)
    setError('')
    try {
      const status = await commerceApi.status(credentials)
      setBackendStatus(status)
      if (!status.available) {
        throw new Error('Spring Boot is reachable, but no WooCommerce MCP server is initialized. Check that the backend can launch the MCP process and that its environment contains the WooCommerce URL and consumer credentials.')
      }
      const result = await commerceApi.products(credentials, 1, '')
      setItems(result.items)
      setHasMore(result.hasMore)
      setConnected(true)
    } catch (reason) {
      setConnected(false)
      setError(errorMessage(reason))
    } finally { setConnecting(false) }
  }

  async function openDetail(id: number) {
    setError('')
    setDetail(null)
    setLoading(true)
    try {
      const value = section === 'products' ? await commerceApi.product(credentials, id)
        : section === 'orders' ? await commerceApi.order(credentials, id)
          : await commerceApi.customer(credentials, id)
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
      })
      setNotice('Product created.')
      setShowCreate(false)
      setPage(1)
      setSearch('')
      const result = await commerceApi.products(credentials, 1, '')
      setItems(result.items)
    } catch (reason) { setError(errorMessage(reason)) }
  }

  async function changeOrderStatus(order: Order, nextStatus: string) {
    setError('')
    try {
      const updated = await commerceApi.updateOrderStatus(credentials, order.id, nextStatus)
      setDetail(updated)
      setItems((current) => current.map((item) => 'id' in item && item.id === order.id ? updated : item))
      setNotice(`Order ${order.number || order.id} updated.`)
    } catch (reason) { setError(errorMessage(reason)) }
  }

  const sectionLabel = section[0].toUpperCase() + section.slice(1)
  const detailFields = detail ? Object.entries(detail).filter(([key, value]) =>
    key !== 'id' && value !== null && value !== undefined && typeof value !== 'object' && value !== '',
  ) : []

  return (
    <main className="app-shell">
      <header className="topbar">
        <div><span className="eyebrow">MCP GATEWAY · STORE CONSOLE</span><h1>WooCommerce</h1></div>
        <div className="connection"><span className={connected ? 'status-dot online' : 'status-dot'} />{connecting ? 'Checking connection…' : connected ? `Store connected · ${backendStatus?.initializedClients ?? 0} MCP server${backendStatus?.initializedClients === 1 ? '' : 's'}` : 'Not connected'}</div>
      </header>

      {!connected ? <form className="connect-card" onSubmit={connect}>
        <h2>Connect to your backend</h2>
        <p>Connect using the Spring Boot HTTP Basic credentials. WooCommerce OAuth keys stay on the MCP server and are never entered here.</p>
        <div className="credentials">
          <label>Username<input autoComplete="username" required value={credentials.username} onChange={(event) => setCredentials({ ...credentials, username: event.target.value })} /></label>
          <label>Password<input type="password" autoComplete="current-password" required value={credentials.password} onChange={(event) => setCredentials({ ...credentials, password: event.target.value })} /></label>
        </div>
        <button className="primary" type="submit" disabled={connecting}>{connecting ? 'Checking…' : 'Connect to store'}</button>
        {backendStatus && !backendStatus.available && <div className="connection-diagnostic" role="status"><strong>Backend is reachable; WooCommerce is not connected.</strong><span>{backendStatus.configuredClients} MCP client configured · {backendStatus.initializedClients} initialized</span></div>}
        {error && <div className="error" role="alert">{error}</div>}
      </form> : <>
        <nav className="tabs" aria-label="Store resources">
          {(['products', 'orders', 'customers'] as Section[]).map((item) => <button key={item} className={section === item ? 'tab active' : 'tab'} onClick={() => { setSection(item); setPage(1); setDetail(null); setNotice('') }}>{item}</button>)}
          <button className="disconnect" onClick={() => { setConnected(false); setCredentials({ username: '', password: '' }); setItems([]); setDetail(null); setBackendStatus(null); setError('') }}>Disconnect</button>
        </nav>

        <section className="workspace">
          <div className="list-panel">
            <div className="panel-heading"><div><span className="eyebrow">STORE CATALOG</span><h2>{sectionLabel}</h2></div>
              {section === 'products' && <button className="primary" onClick={() => { setShowCreate((value) => !value); setError('') }}>{showCreate ? 'Close' : 'New product'}</button>}
            </div>

            {section === 'products' && <label className="filter">Search products<input value={search} onChange={(event) => { setSearch(event.target.value); setPage(1) }} placeholder="Name or keyword" /></label>}
            {section === 'orders' && <label className="filter">Order status<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1) }}><option value="">All statuses</option>{orderStatuses.map((value) => <option key={value}>{value}</option>)}</select></label>}

            {showCreate && <form className="create-form" onSubmit={createProduct}>
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
              {section === 'orders' && <div className="status-editor"><label>Status<select value={(detail as Order).status} onChange={(event) => changeOrderStatus(detail as Order, event.target.value)}>{orderStatuses.map((value) => <option key={value}>{value}</option>)}</select></label></div>}
              <dl className="detail-fields">{detailFields.map(([key, value]) => <div key={key}><dt>{key.replaceAll('_', ' ')}</dt><dd>{String(value)}</dd></div>)}</dl>
            </> : <p className="muted">Select a {section.slice(0, -1)} to view its details.</p>}
          </aside>
        </section>
      </>}
      {notice && <div className="toast" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss">×</button></div>}
    </main>
  )
}

export default App
