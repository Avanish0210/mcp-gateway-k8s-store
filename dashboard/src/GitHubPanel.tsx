import { useState } from 'react'
import type { FormEvent } from 'react'
import { errorMessage, githubApi } from './api'
import type { Credentials, GitHubStatus, GitHubTool } from './api'

type Props = { credentials: Credentials; status: GitHubStatus | null; tools: GitHubTool[]; refreshing: boolean; onRefresh: () => Promise<void> }
type GithubRecord = Record<string, unknown>

function recordsFrom(value: unknown): GithubRecord[] {
  if (Array.isArray(value)) return value.map((item) => item && typeof item === 'object' ? item as GithubRecord : { value: item })
  if (value && typeof value === 'object') {
    const object = value as GithubRecord
    for (const key of ['items', 'repositories', 'issues', 'results', 'matches', 'data']) {
      if (key in object) return recordsFrom(object[key])
    }
    return [object]
  }
  return []
}

function text(value: unknown) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

function field(record: GithubRecord, ...keys: string[]) {
  const key = keys.find((candidate) => record[candidate] !== undefined && record[candidate] !== null && record[candidate] !== '')
  return key ? record[key] : undefined
}

function ResultList({ data, kind }: { data: unknown; kind: 'repositories' | 'code' | 'issues' }) {
  const records = recordsFrom(data)
  if (records.length === 0) {
    if (data === null || data === undefined || (Array.isArray(data) && data.length === 0)) return <div className="empty">No {kind === 'issues' ? 'issues' : kind === 'code' ? 'matching files' : 'repositories'} found.</div>
    return <pre className="github-raw-result">{typeof data === 'string' ? data : JSON.stringify(data, null, 2)}</pre>
  }
  return <div className="records">{records.map((record, index) => {
    const ownerValue = field(record, 'owner', 'repository_owner')
    const owner = ownerValue && typeof ownerValue === 'object' ? text((ownerValue as GithubRecord).login ?? (ownerValue as GithubRecord).name) : text(ownerValue)
    const repoName = text(field(record, 'full_name', 'repository', 'repository_name'))
    const title = text(field(record, 'value')) || (kind === 'repositories' ? text(field(record, 'full_name', 'name')) || 'Repository'
      : kind === 'issues' ? text(field(record, 'title', 'name')) || 'Issue'
        : text(field(record, 'name', 'file_name', 'path')) || 'Code match')
    const url = text(field(record, 'html_url', 'url', 'repository_url', 'htmlUrl'))
    const details = kind === 'repositories'
      ? [owner, field(record, 'description'), field(record, 'stargazers_count', 'stars') !== undefined ? `${text(field(record, 'stargazers_count', 'stars'))} stars` : '', field(record, 'forks_count', 'forks') !== undefined ? `${text(field(record, 'forks_count', 'forks'))} forks` : '', text(field(record, 'language')), text(field(record, 'visibility'))].filter(Boolean).join(' · ')
      : kind === 'issues'
        ? [`#${text(field(record, 'number'))}`, text(field(record, 'state')), text(field(record, 'user', 'author') && typeof field(record, 'user', 'author') === 'object' ? (field(record, 'user', 'author') as GithubRecord).login : field(record, 'user', 'author')), text(field(record, 'created_at', 'createdAt')), text(field(record, 'labels'))].filter((part) => part && part !== '#').join(' · ')
        : [repoName, text(field(record, 'path', 'file_path')), text(field(record, 'language'))].filter(Boolean).join(' · ')
    const description = kind === 'repositories' ? text(field(record, 'description')) : kind === 'code' ? text(field(record, 'text_matches', 'snippet', 'description')) : ''
    return <article className="github-result" key={`${url || title}-${index}`}>
      <span><strong>{url ? <a href={url} target="_blank" rel="noreferrer">{title}</a> : title}</strong>
        {details && <small>{details}</small>}{description && <small>{description}</small>}
      </span>
    </article>
  })}</div>
}

export default function GitHubPanel({ credentials, status, tools, refreshing, onRefresh }: Props) {
  const [view, setView] = useState<'repositories' | 'code' | 'issues'>('repositories')
  const [query, setQuery] = useState('')
  const [owner, setOwner] = useState('')
  const [repo, setRepo] = useState('')
  const [issueState, setIssueState] = useState('open')
  const [data, setData] = useState<unknown>(null)
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function search(event: FormEvent) {
    event.preventDefault()
    setError('')
    setLoading(true)
    setSearched(false)
    try {
      const result = view === 'repositories'
        ? await githubApi.repositories(credentials, query.trim())
        : view === 'code'
          ? await githubApi.code(credentials, query.trim())
          : await githubApi.issues(credentials, owner.trim(), repo.trim(), issueState)
      setData(result)
      setSearched(true)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  return <>
    <section className="github-status">
      <div><span className="eyebrow">MCP CONNECTION</span><h2>GitHub</h2><p className="muted">{status?.githubConnected ? 'Connected to the GitHub MCP server.' : 'GitHub MCP server is disconnected or unavailable.'}</p></div>
      <div className="github-status-actions"><span className="connection"><span className={status?.githubConnected ? 'status-dot online' : 'status-dot'} />{status?.githubConnected ? 'Connected' : 'Disconnected'}</span><button className="secondary" disabled={refreshing} onClick={() => void onRefresh()}>{refreshing ? 'Refreshing…' : 'Refresh status'}</button></div>
      <div className="github-metrics"><div><strong>{status?.availableTools ?? 0}</strong><small>Available tools</small></div><div><strong>{status?.configuredClients ?? 0}</strong><small>Configured clients</small></div></div>
      {tools.length > 0 && <details className="tool-inventory"><summary>Available MCP tools ({tools.length})</summary><div>{tools.map((tool) => <code key={tool}>{tool}</code>)}</div></details>}
    </section>
    <section className="list-panel github-workspace">
      <div className="panel-heading"><div><span className="eyebrow">GITHUB MCP</span><h2>Explore repositories</h2></div></div>
      <nav className="tabs github-tabs" aria-label="GitHub resources">{(['repositories', 'code', 'issues'] as const).map((item) => <button key={item} className={view === item ? 'tab active' : 'tab'} onClick={() => { setView(item); setData(null); setSearched(false); setError('') }}>{item === 'code' ? 'Code search' : item === 'issues' ? 'Repository issues' : 'Repositories'}</button>)}</nav>
      <form className="github-search" onSubmit={search}>
        {view !== 'issues' ? <label className="filter">{view === 'repositories' ? 'Search repositories' : 'Search code'}<input required minLength={1} maxLength={256} value={query} onChange={(event) => setQuery(event.target.value)} placeholder={view === 'repositories' ? 'e.g. spring boot' : 'e.g. language:java'} /></label>
          : <><label>Repository owner<input required value={owner} onChange={(event) => setOwner(event.target.value)} placeholder="owner" /></label><label>Repository name<input required value={repo} onChange={(event) => setRepo(event.target.value)} placeholder="repository" /></label><label>Issue state<select value={issueState} onChange={(event) => setIssueState(event.target.value)}><option value="open">Open</option><option value="closed">Closed</option><option value="all">All</option></select></label></>}
        <button className="primary" type="submit" disabled={loading || !status?.githubConnected}>{loading ? 'Searching…' : 'Search'}</button>
      </form>
      {error && <div className="error" role="alert">{error}<button className="retry" onClick={(event) => { const form = event.currentTarget.closest('section')?.querySelector('form'); form?.requestSubmit() }}>Retry</button></div>}
      {loading && <p className="muted" role="status">Loading GitHub results…</p>}
      {!loading && searched && !error && <ResultList data={data} kind={view} />}
      {!loading && !searched && !error && <p className="muted">Search repositories, code, or issues to see live results from GitHub.</p>}
    </section>
  </>
}
