import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { apiKeysApi, errorMessage } from './api'
import type { ApiKeyMetadata, CreatedApiKey, Credentials } from './api'

function dateLabel(value: string | null) {
  return value ? new Date(value).toLocaleString() : 'Never used'
}

export default function ApiKeysPanel({ credentials }: { credentials: Credentials }) {
  const [keys, setKeys] = useState<ApiKeyMetadata[]>([])
  const [name, setName] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [newKey, setNewKey] = useState<CreatedApiKey | null>(null)
  const [copied, setCopied] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [currentTime, setCurrentTime] = useState(0)

  const refresh = useCallback(async () => {
    setError('')
    try { setKeys(await apiKeysApi.list(credentials)); setCurrentTime(Date.now()) }
    catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }, [credentials])

  useEffect(() => {
    const timer = window.setTimeout(() => { void refresh() }, 0)
    return () => window.clearTimeout(timer)
  }, [refresh])

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const expiration = expiresAt ? new Date(expiresAt).toISOString() : undefined
      const created = await apiKeysApi.create(credentials, name.trim(), expiration)
      setNewKey(created)
      setCopied(false)
      setName('')
      setExpiresAt('')
      await refresh()
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setSaving(false) }
  }

  async function copyKey() {
    if (!newKey) return
    try {
      await navigator.clipboard.writeText(newKey.apiKey)
      setCopied(true)
      setNotice('API key copied. Store it in your MCP client configuration now.')
    } catch { setError('Clipboard access is unavailable. Select and copy the key manually before dismissing it.') }
  }

  async function revoke(key: ApiKeyMetadata) {
    setError('')
    setNotice('')
    try {
      await apiKeysApi.revoke(credentials, key.id)
      setKeys((current) => current.map((item) => item.id === key.id ? { ...item, revoked: true } : item))
      setNotice(`${key.name} was revoked.`)
      if (newKey?.key.id === key.id) setNewKey(null)
    } catch (reason) { setError(errorMessage(reason)) }
  }

  return <div className="api-keys-page">
    <section className="list-panel api-key-intro">
      <div className="panel-heading"><div><span className="eyebrow">PERSONAL ACCESS</span><h2>API keys</h2></div><span className="user-role-chip">{credentials.role}</span></div>
      <p>Create a separate key for external MCP clients such as Cursor or Claude Code. Each key belongs to your account and can be revoked at any time.</p>
    </section>

    {newKey && <section className="api-key-reveal" aria-live="polite">
      <div><span className="eyebrow">COPY THIS KEY NOW</span><h2>Key created</h2><p>The full secret is shown once. It disappears when dismissed or when you leave this page.</p></div>
      <code className="api-key-secret">{newKey.apiKey}</code>
      <div className="api-key-reveal-actions"><button className="primary" onClick={() => void copyKey()}>{copied ? 'Copied' : 'Copy key'}</button><button className="secondary" onClick={() => setNewKey(null)}>Dismiss secret</button></div>
    </section>}

    <section className="list-panel api-key-create">
      <div className="panel-heading"><div><span className="eyebrow">NEW CREDENTIAL</span><h2>Generate a key</h2></div></div>
      <form className="api-key-form" onSubmit={create}>
        <label>Key name<input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Cursor on my laptop" /></label>
        <label>Expires (optional)<input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} /></label>
        <button className="primary" type="submit" disabled={saving || !name.trim()}>{saving ? 'Generating…' : 'Generate API key'}</button>
      </form>
      <p className="api-key-hint">API keys are shown only at creation. Copy the secret before dismissing it.</p>
    </section>

    {error && <div className="error" role="alert">{error}</div>}
    {notice && <div className="api-key-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notice">×</button></div>}

    <section className="list-panel api-key-list">
      <div className="panel-heading"><div><span className="eyebrow">YOUR ACCOUNT ONLY</span><h2>Existing keys</h2></div><button className="secondary" onClick={() => void refresh()} disabled={loading}>Refresh</button></div>
      {loading ? <p className="muted">Loading keys…</p> : keys.length === 0 ? <p className="muted">You have not created any API keys yet.</p> : <div className="api-key-rows">{keys.map((key) => {
        const expired = !!key.expiresAt && currentTime > 0 && new Date(key.expiresAt).getTime() <= currentTime
        return <article className="api-key-row" key={key.id}>
          <div className="api-key-row-main"><div><strong>{key.name}</strong><code>{key.prefix}…</code></div><span className={key.revoked ? 'key-state revoked' : expired ? 'key-state expired' : 'key-state active'}>{key.revoked ? 'Revoked' : expired ? 'Expired' : 'Active'}</span></div>
          <div className="api-key-meta"><span>Created <b>{dateLabel(key.createdAt)}</b></span><span>Last used <b>{dateLabel(key.lastUsedAt)}</b></span><span>Expires <b>{key.expiresAt ? dateLabel(key.expiresAt) : 'Never'}</b></span></div>
          {!key.revoked && <button className="key-revoke" onClick={() => void revoke(key)}>Revoke</button>}
        </article>
      })}</div>}
    </section>
  </div>
}
