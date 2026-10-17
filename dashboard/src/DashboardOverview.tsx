import { useCallback, useEffect, useMemo, useState } from 'react'
import { Activity, ArrowRight, ArrowUpRight, CloudSun, ExternalLink, GitBranch, RefreshCw, Server, ShieldCheck, ShoppingBag, Store, Users } from 'lucide-react'
import { auditApi, commerceApi, errorMessage, githubApi, storesApi, teamApi, weatherApi } from './api'
import type { AuditEvent, BackendStatus, Credentials, GitHubStatus, ManagedStore, TeamMember, WeatherStatus } from './api'

type Section = 'woocommerce' | 'github' | 'weather' | 'stores' | 'team' | 'audit'
type Integration = { id: 'woocommerce' | 'github' | 'weather'; label: string; description: string; available: boolean | null; toolCount: number | null; tools: string[] | null; tone: string; icon: typeof ShoppingBag }

function statusTone(status: ManagedStore['status']) {
  return status === 'READY' ? 'ready' : status === 'PROVISIONING' ? 'provisioning' : status === 'FAILED' ? 'failed' : 'deleting'
}

function statusLabel(status: ManagedStore['status']) {
  return status === 'READY' ? 'Ready' : status === 'PROVISIONING' ? 'Provisioning' : status === 'FAILED' ? 'Failed' : 'Deleting'
}

function dateLabel(value: string | null) {
  return value ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

function relativeTime(value: string) {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  const intervals: Array<[Intl.RelativeTimeFormatUnit, number]> = [['year', 31536000], ['month', 2592000], ['week', 604800], ['day', 86400], ['hour', 3600], ['minute', 60]]
  for (const [unit, length] of intervals) if (Math.abs(seconds) >= length) return formatter.format(Math.round(seconds / length), unit)
  return formatter.format(seconds, 'second')
}

export default function DashboardOverview({ credentials, onNavigate }: { credentials: Credentials; onNavigate: (section: Section) => void }) {
  const [stores, setStores] = useState<ManagedStore[] | null>(null)
  const [members, setMembers] = useState<TeamMember[] | null>(null)
  const [woocommerce, setWoocommerce] = useState<BackendStatus | null>(null)
  const [woocommerceTools, setWoocommerceTools] = useState<string[] | null>(null)
  const [github, setGithub] = useState<GitHubStatus | null>(null)
  const [githubTools, setGithubTools] = useState<string[] | null>(null)
  const [weather, setWeather] = useState<WeatherStatus | null>(null)
  const [weatherTools, setWeatherTools] = useState<string[] | null>(null)
  const [activity, setActivity] = useState<AuditEvent[] | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const refresh = useCallback(async () => {
    setRefreshing(true)
    const requests = await Promise.allSettled([
      commerceApi.status(credentials), commerceApi.tools(credentials),
      githubApi.status(credentials), githubApi.tools(credentials),
      weatherApi.status(credentials), weatherApi.tools(credentials),
      auditApi.list(credentials, { page: 0 }),
      ...(credentials.role === 'ADMIN' ? [storesApi.list(credentials), teamApi.members(credentials)] : []),
    ])
    const nextErrors: Record<string, string> = {}
    const assign = <T,>(index: number, key: string, setter: (value: T) => void) => {
      const result = requests[index]
      if (result?.status === 'fulfilled') setter(result.value as T)
      else nextErrors[key] = result?.status === 'rejected' ? errorMessage(result.reason) : 'Data is unavailable.'
    }
    assign<BackendStatus>(0, 'woocommerce', setWoocommerce)
    assign<string[]>(1, 'woocommerceTools', setWoocommerceTools)
    assign<GitHubStatus>(2, 'github', setGithub)
    assign<string[]>(3, 'githubTools', setGithubTools)
    assign<WeatherStatus>(4, 'weather', setWeather)
    assign<string[]>(5, 'weatherTools', setWeatherTools)
    assign<{ content: AuditEvent[] }>(6, 'activity', (value) => setActivity(value.content))
    if (credentials.role === 'ADMIN') {
      assign<ManagedStore[]>(7, 'stores', setStores)
      assign<TeamMember[]>(8, 'team', setMembers)
    }
    setErrors(nextErrors)
    setLoading(false)
    setRefreshing(false)
  }, [credentials])

  useEffect(() => { const timer = window.setTimeout(() => { void refresh() }, 0); return () => window.clearTimeout(timer) }, [refresh])

  const integrations = useMemo<Integration[]>(() => [
    { id: 'woocommerce', label: 'WooCommerce', description: 'Products, orders, and customer operations.', available: woocommerce?.available ?? null, toolCount: woocommerceTools?.length ?? null, tools: woocommerceTools, tone: 'violet', icon: ShoppingBag },
    { id: 'github', label: 'GitHub', description: 'Repositories, issues, and code search.', available: github?.githubConnected ?? null, toolCount: github?.availableTools ?? githubTools?.length ?? null, tools: githubTools, tone: 'slate', icon: GitBranch },
    { id: 'weather', label: 'Weather', description: 'Location search, current conditions, and forecasts.', available: weather?.available ?? null, toolCount: weatherTools?.length ?? null, tools: weatherTools, tone: 'sky', icon: CloudSun },
  ], [woocommerce, woocommerceTools, github, githubTools, weather, weatherTools])

  const readyStores = stores?.filter((store) => store.status === 'READY').length ?? 0
  const provisioningStores = stores?.filter((store) => store.status === 'PROVISIONING').length ?? 0
  const attentionStores = stores?.filter((store) => store.status === 'FAILED' || store.status === 'DELETING').length ?? 0
  const connectedServers = integrations.filter((item) => item.available === true).length
  const knownServers = integrations.filter((item) => item.available !== null).length
  const anyIntegrationDown = integrations.some((item) => item.available === false)
  const backendReachable = woocommerce !== null || github !== null || weather !== null
  const healthLabel = !backendReachable ? loading ? 'Checking' : 'Unavailable' : anyIntegrationDown || attentionStores > 0 || knownServers < integrations.length || (credentials.role === 'ADMIN' && !!errors.stores) ? 'Degraded' : 'Healthy'
  const healthTone = healthLabel === 'Healthy' ? 'green' : healthLabel === 'Degraded' ? 'orange' : 'blue'

  return <div className="overview-page">
    <div className="overview-toolbar">
      <div><span className="eyebrow">WORKSPACE OVERVIEW</span><p>Live status from your connected MCP services and gateway APIs.</p></div>
      <button className="secondary overview-refresh" onClick={() => void refresh()} disabled={refreshing} aria-label="Refresh dashboard data"><RefreshCw size={15} className={refreshing ? 'spin' : ''} />{refreshing ? 'Refreshing' : 'Refresh'}</button>
    </div>

    <section className="summary-grid" aria-label="Workspace summary">
      <article className="summary-card blue">
        <span className="summary-icon"><Store size={21} /></span><div className="summary-copy"><small>Total stores</small><strong>{credentials.role !== 'ADMIN' ? 'Admin only' : loading && !stores ? '—' : stores?.length ?? 'Unavailable'}</strong><span>{credentials.role !== 'ADMIN' ? 'Store management is restricted' : errors.stores ? 'Could not load stores' : `${readyStores} ready · ${provisioningStores} provisioning${attentionStores ? ` · ${attentionStores} need attention` : ''}`}</span></div>
        {credentials.role === 'ADMIN' && <button className="summary-link" onClick={() => onNavigate('stores')} aria-label="Open store management"><ArrowUpRight size={18} /></button>}
      </article>
      <article className="summary-card teal">
        <span className="summary-icon"><Server size={21} /></span><div className="summary-copy"><small>MCP servers</small><strong>{knownServers ? `${connectedServers} / ${integrations.length}` : loading ? 'Checking' : 'Unavailable'}</strong><span>{knownServers ? `${connectedServers} connected · ${integrations.length - connectedServers} unavailable or checking` : 'Connection status unavailable'}</span></div>
        <button className="summary-link" onClick={() => onNavigate('woocommerce')} aria-label="Open MCP integrations"><ArrowUpRight size={18} /></button>
      </article>
      <article className="summary-card purple">
        <span className="summary-icon"><Users size={21} /></span><div className="summary-copy"><small>Team members</small><strong>{credentials.role !== 'ADMIN' ? 'Admin only' : loading && !members ? '—' : members?.length ?? 'Unavailable'}</strong><span>{credentials.role !== 'ADMIN' ? 'Team information is restricted' : errors.team ? 'Could not load team' : 'Registered accounts'}</span></div>
        {credentials.role === 'ADMIN' && <button className="summary-link" onClick={() => onNavigate('team')} aria-label="Open team management"><ArrowUpRight size={18} /></button>}
      </article>
      <article className={`summary-card ${healthTone}`}>
        <span className="summary-icon"><ShieldCheck size={21} /></span><div className="summary-copy"><small>System health</small><strong>{healthLabel}</strong><span>{backendReachable ? `${connectedServers} of ${integrations.length} MCP integrations connected` : 'Waiting for service checks'}</span></div>
        <button className="summary-link" onClick={() => onNavigate('audit')} aria-label="Open audit activity"><ArrowUpRight size={18} /></button>
      </article>
    </section>

    <div className="overview-columns">
      <div className="overview-primary">
        <section className="overview-panel store-status-panel">
          <div className="overview-panel-heading"><div className="overview-title"><span className="section-icon blue-icon"><Store size={18} /></span><div><h2>Store Status</h2><p>Provisioning and availability at a glance</p></div></div>{credentials.role === 'ADMIN' && <button className="text-action" onClick={() => onNavigate('stores')}>View all <ArrowRight size={15} /></button>}</div>
          {credentials.role !== 'ADMIN' ? <div className="overview-empty">Store management is available to administrators.</div> : errors.stores && !stores ? <div className="overview-inline-error" role="alert">{errors.stores}</div> : loading && !stores ? <div className="overview-skeleton rows-skeleton"><i /><i /><i /></div> : !stores?.length ? <div className="overview-empty">No stores are registered yet. <button className="text-action" onClick={() => onNavigate('stores')}>Create a store <ArrowRight size={14} /></button></div> : <div className="store-table-wrap"><table className="store-table"><thead><tr><th>Store</th><th>Status</th><th>URL</th><th>Namespace</th><th>Created</th><th>Actions</th></tr></thead><tbody>{stores.map((store) => <tr key={store.id}><td><strong>{store.name}</strong><small>{store.helmRelease}</small></td><td><span className={`status-pill ${statusTone(store.status)}`}><i />{statusLabel(store.status)}</span></td><td>{store.storeUrl ? <a href={store.storeUrl} target="_blank" rel="noreferrer" className="store-url">Open store <ExternalLink size={12} /></a> : <span className="table-muted">—</span>}</td><td><code>{store.namespace}</code></td><td>{dateLabel(store.createdAt)}</td><td><button className="table-action" onClick={() => onNavigate('stores')}>Manage</button></td></tr>)}</tbody></table></div>}
          {errors.stores && stores && <p className="overview-data-note">Store data may be out of date: {errors.stores}</p>}
        </section>

        <section className="overview-panel provisioning-panel">
          <div className="overview-panel-heading"><div className="overview-title"><span className="section-icon teal-icon"><Server size={18} /></span><div><h2>Store Provisioning Overview</h2><p>Store resources, namespace, and lifecycle details</p></div></div>{credentials.role === 'ADMIN' && <button className="text-action" onClick={() => onNavigate('stores')}>Manage stores <ArrowRight size={15} /></button>}</div>
          {credentials.role !== 'ADMIN' ? <div className="overview-empty">Provisioning details are available to administrators.</div> : errors.stores && !stores ? <div className="overview-inline-error" role="alert">{errors.stores}</div> : loading && !stores ? <div className="overview-skeleton cards-skeleton"><i /><i /></div> : !stores?.length ? <div className="overview-empty">Create a managed store to see its provisioning details here.</div> : <div className="provisioning-grid">{stores.map((store) => <article className={`provisioning-card ${statusTone(store.status)}`} key={store.id}>
            <div className="provisioning-card-head"><span className="provisioning-store-icon"><Store size={17} /></span><div><strong>{store.name}</strong><small>{store.helmRelease}</small></div><span className={`status-pill ${statusTone(store.status)}`}><i />{statusLabel(store.status)}</span></div>
            <dl className="provisioning-details"><div><dt>Namespace</dt><dd>{store.namespace}</dd></div><div><dt>Database</dt><dd>{store.namespace === 'ecommerce' ? 'Existing deployment' : `${store.namespace}-mariadb`}</dd></div><div><dt>Store URL</dt><dd>{store.storeUrl ? <a href={store.storeUrl} target="_blank" rel="noreferrer">{store.storeUrl}</a> : 'Not available yet'}</dd></div><div><dt>Created</dt><dd>{dateLabel(store.createdAt)}</dd></div></dl>
            {store.errorMessage && <p className="provisioning-error">{store.errorMessage}</p>}
            <button className="provisioning-manage" onClick={() => onNavigate('stores')}>Open store management <ArrowRight size={14} /></button>
          </article>)}</div>}
        </section>

        <section className="overview-panel integrations-panel">
          <div className="overview-panel-heading"><div className="overview-title"><span className="section-icon purple-icon"><Activity size={18} /></span><div><h2>MCP Tools &amp; Integrations</h2><p>Explore tools from your connected servers</p></div></div></div>
          <div className="integration-grid">{integrations.map((integration) => {
            const Icon = integration.icon
            return <article className={`integration-card ${integration.tone}`} key={integration.id}>
              <div className="integration-card-head"><span className="integration-icon"><Icon size={21} /></span><span className={`mini-connection ${integration.available === true ? 'is-online' : integration.available === false ? 'is-offline' : 'is-unknown'}`}><i />{integration.available === true ? 'Connected' : integration.available === false ? 'Unavailable' : 'Checking'}</span></div>
              <h3>{integration.label}</h3><p>{integration.description}</p>
              <div className="integration-card-foot"><span>{integration.toolCount === null ? 'Tool inventory unavailable' : `${integration.toolCount} available ${integration.toolCount === 1 ? 'tool' : 'tools'}`}</span><button onClick={() => onNavigate(integration.id)}>Open tools <ArrowRight size={14} /></button></div>
              {integration.tools && integration.tools.length > 0 && <details className="integration-tools"><summary>Browse available tools</summary><div>{integration.tools.map((tool) => <code key={tool}>{tool}</code>)}</div></details>}
            </article>
          })}</div>
        </section>
      </div>

      <aside className="overview-secondary">
        <section className="overview-panel server-panel">
          <div className="overview-panel-heading"><div className="overview-title"><span className="section-icon blue-icon"><Server size={18} /></span><div><h2>MCP Server Status</h2><p>Live connection checks</p></div></div><button className="text-action" onClick={() => onNavigate('woocommerce')}>View all <ArrowRight size={15} /></button></div>
          <div className="server-list">{integrations.map((integration) => {
            const Icon = integration.icon
            return <button className="server-row" key={integration.id} onClick={() => onNavigate(integration.id)}><span className={`server-icon ${integration.tone}`}><Icon size={19} /></span><span className="server-copy"><strong>{integration.label}</strong><small>{integration.description}</small></span><span className={`mini-connection ${integration.available === true ? 'is-online' : integration.available === false ? 'is-offline' : 'is-unknown'}`}><i />{integration.available === true ? 'Running' : integration.available === false ? 'Unavailable' : 'Unknown'}</span></button>
          })}</div>
          {Object.entries(errors).filter(([key]) => ['woocommerce', 'github', 'weather'].includes(key)).map(([key, message]) => <p className="overview-data-note" key={key}>{key}: {message}</p>)}
        </section>

        <section className="overview-panel activity-panel">
          <div className="overview-panel-heading"><div className="overview-title"><span className="section-icon orange-icon"><Activity size={18} /></span><div><h2>Recent Activity</h2><p>Latest recorded gateway events</p></div></div><button className="text-action" onClick={() => onNavigate('audit')}>View all <ArrowRight size={15} /></button></div>
          {errors.activity && !activity ? <div className="overview-inline-error" role="alert">{errors.activity}</div> : loading && !activity ? <div className="overview-skeleton rows-skeleton"><i /><i /><i /><i /></div> : !activity?.length ? <div className="overview-empty">No recent activity has been recorded.</div> : <div className="activity-list">{activity.slice(0, 6).map((event) => <article className="activity-row" key={event.id}><span className={`activity-icon ${event.outcome.toLowerCase()}`}>{event.outcome === 'SUCCESS' ? <ShieldCheck size={15} /> : <Activity size={15} />}</span><span className="activity-copy"><strong>{(event.toolName || event.eventType).replaceAll('_', ' ').toLowerCase()}</strong><small>{event.actorUsername || 'System'}{event.description ? ` · ${event.description}` : ''}</small></span><time title={new Date(event.occurredAt).toLocaleString()}>{relativeTime(event.occurredAt)}</time></article>)}</div>}
          {errors.activity && activity && <p className="overview-data-note">Activity may be out of date: {errors.activity}</p>}
        </section>
      </aside>
    </div>
  </div>
}
