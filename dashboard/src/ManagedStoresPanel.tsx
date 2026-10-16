import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { errorMessage, storesApi, storeWooApi } from './api'
import type { Credentials, ManagedStore } from './api'

export default function ManagedStoresPanel({ credentials, onStoresChanged }: { credentials: Credentials; onStoresChanged?: (stores: ManagedStore[]) => void }) {
  const [stores, setStores] = useState<ManagedStore[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [toolLists, setToolLists] = useState<Record<string, string[]>>({})
  const refresh = useCallback(async () => {
    try { const updated = await storesApi.list(credentials); setStores(updated); onStoresChanged?.(updated); setError('') }
    catch (reason) { setError(errorMessage(reason)) }
  }, [credentials, onStoresChanged])

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh() }, 0)
    return () => window.clearTimeout(timer)
  }, [refresh])
  useEffect(() => {
    if (!stores.some((store) => store.status === 'PROVISIONING' || store.status === 'DELETING')) return
    const timer = window.setInterval(() => { void refresh() }, 5000)
    return () => window.clearInterval(timer)
  }, [stores, refresh])

  async function create(event: FormEvent) {
    event.preventDefault(); if (busy) return
    setBusy(true); setError('')
    try { await storesApi.create(credentials, name.trim()); setName(''); await refresh() }
    catch (reason) { setError(errorMessage(reason)) }
    finally { setBusy(false) }
  }
  async function retry(id: string) {
    setBusy(true); setError(''); try { await storesApi.retry(credentials, id); await refresh() }
    catch (reason) { setError(errorMessage(reason)) } finally { setBusy(false) }
  }
  async function remove(store: ManagedStore) {
    if (!window.confirm(`Delete ${store.name} (${store.namespace}) and its persistent data?`)) return
    setBusy(true); setError(''); try { await storesApi.remove(credentials, store.id); await refresh() }
    catch (reason) { setError(errorMessage(reason)) } finally { setBusy(false) }
  }
  async function loadTools(store: ManagedStore) {
    setError('')
    try { const tools = await storeWooApi.tools(credentials, store.id); setToolLists((current) => ({ ...current, [store.id]: tools })) }
    catch (reason) { setError(errorMessage(reason)) }
  }

  const canCreate = stores.length < 3
  const remainingSlots = Math.max(0, 3 - stores.length)
  return <section className="workspace"><div className="list-panel"><div className="panel-heading"><div><span className="eyebrow">KUBERNETES LIFECYCLE</span><h2>Managed stores</h2></div><button className="disconnect" onClick={() => void refresh()}>Refresh</button></div>
    <p className="muted">Store 1 is the existing ecommerce namespace. {remainingSlots} additional {remainingSlots === 1 ? 'store is' : 'stores are'} available.</p>
    {error && <div className="error" role="alert">{error}</div>}
    <form className="create-form" onSubmit={create}><h3>Create a store</h3><label>Store name<input required minLength={2} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} /></label><button className="primary" type="submit" disabled={busy || !canCreate}>{busy ? 'Working…' : canCreate ? 'Create store' : 'Store limit reached'}</button></form>
    <div className="records">{stores.map((store) => <article className="record" key={store.id}><span><strong>{store.name}</strong><small>{store.status} · {store.namespace} · {store.createdAt ? new Date(store.createdAt).toLocaleString() : 'Existing deployment'}</small>{store.status === 'READY' && store.storeUrl && <a href={store.storeUrl} target="_blank" rel="noreferrer">Open store</a>}{store.status === 'READY' && store.namespace !== 'ecommerce' && <button onClick={() => void loadTools(store)}>Show MCP tools</button>}{toolLists[store.id] && <small>Tools: {toolLists[store.id].join(', ')}</small>}{store.errorMessage && <small className="error-text">{store.errorMessage}</small>}</span><span className="store-actions">{(store.status === 'FAILED' || (store.status === 'DELETING' && store.errorMessage?.startsWith('Deletion failed'))) && <button onClick={() => void retry(store.id)} disabled={busy}>Retry</button>}{store.namespace !== 'ecommerce' && store.status !== 'DELETING' && <button onClick={() => void remove(store)} disabled={busy}>Delete</button>}</span></article>)}</div>
  </div></section>
}
