import { useCallback, useEffect, useState } from 'react'
import { auditApi, errorMessage } from './api'
import type { AuditEvent, Credentials } from './api'

export default function AuditPanel({ credentials }: { credentials: Credentials }) {
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [page, setPage] = useState(0)
  const [pages, setPages] = useState(0)
  const [eventType, setEventType] = useState('')
  const [outcome, setOutcome] = useState('')
  const [serverId, setServerId] = useState('')
  const [toolName, setToolName] = useState('')
  const [storeId, setStoreId] = useState('')
  const [actorId, setActorId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const result = await auditApi.list(credentials, { page, eventType, outcome: outcome || undefined, serverId, toolName, storeId,
        ...(credentials.role === 'ADMIN' ? { actorId } : {}), ...(from ? { from: new Date(from).toISOString() } : {}), ...(to ? { to: new Date(to).toISOString() } : {}) })
      setEvents(result.content); setPages(result.totalPages)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }, [credentials, page, eventType, outcome, serverId, toolName, storeId, actorId, from, to])
  useEffect(() => { void load() }, [load])
  return <main className="audit-page">
    <section className="list-panel audit-intro"><div className="panel-heading"><div><span className="eyebrow">SECURITY & ACTIVITY</span><h2>{credentials.role === 'ADMIN' ? 'Global audit log' : 'My activity'}</h2></div><button className="secondary" onClick={() => void load()} disabled={loading}>Refresh</button></div><p>{credentials.role === 'ADMIN' ? 'Administrative actions and MCP tool invocations across the gateway.' : 'Your MCP tool invocations and account actions.'}</p></section>
    <section className="list-panel audit-list">
      <div className="audit-filters"><label>Event type<input value={eventType} onChange={e => { setEventType(e.target.value); setPage(0) }} placeholder="e.g. MCP_TOOL_INVOCATION" /></label><label>Outcome<select value={outcome} onChange={e => { setOutcome(e.target.value); setPage(0) }}><option value="">All outcomes</option><option>SUCCESS</option><option>FAILURE</option></select></label><label>Server<input value={serverId} onChange={e => { setServerId(e.target.value); setPage(0) }} placeholder="woocommerce" /></label><label>Tool<input value={toolName} onChange={e => { setToolName(e.target.value); setPage(0) }} placeholder="Tool name" /></label><label>Store ID<input value={storeId} onChange={e => { setStoreId(e.target.value); setPage(0) }} placeholder="Namespace or key ID" /></label>{credentials.role === 'ADMIN' && <label>Actor ID<input value={actorId} onChange={e => { setActorId(e.target.value); setPage(0) }} placeholder="User ID" /></label>}<label>From<input type="datetime-local" value={from} onChange={e => { setFrom(e.target.value); setPage(0) }} /></label><label>To<input type="datetime-local" value={to} onChange={e => { setTo(e.target.value); setPage(0) }} /></label></div>
      {error && <div className="error" role="alert">{error}</div>}{loading && <p className="muted">Loading audit events…</p>}
      {!loading && !events.length && !error && <p className="empty">No matching audit events.</p>}
      {!loading && events.map(event => <article className="audit-row" key={event.id}><div className="audit-row-title"><div><strong>{event.toolName || event.eventType}</strong><small>{event.actorUsername || 'System'} · {new Date(event.occurredAt).toLocaleString()}</small></div><span className={`audit-outcome ${event.outcome.toLowerCase()}`}>{event.outcome}</span></div><div className="audit-meta"><span>{event.serverId ? `Server: ${event.serverId}` : 'Administrative action'}</span>{event.storeId && <span>Target: {event.storeId}</span>}{event.durationMs != null && <span>{event.durationMs} ms</span>}{event.errorCategory && <span className="audit-error">{event.errorCategory}</span>}</div>{event.description && <small className="audit-description">{event.description}</small>}</article>)}
      <div className="pagination"><button disabled={page <= 0 || loading} onClick={() => setPage(p => p - 1)}>Previous</button><span>{pages ? `${page + 1} / ${pages}` : '0 events'}</span><button disabled={page + 1 >= pages || loading} onClick={() => setPage(p => p + 1)}>Next</button></div>
    </section>
  </main>
}
