import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { errorMessage, teamApi } from './api'
import type { CreatedInvitation, Credentials, TeamInvitation, TeamMember } from './api'

function dateLabel(value: string | null) { return value ? new Date(value).toLocaleString() : '—' }

export default function TeamPanel({ credentials }: { credentials: Credentials }) {
  const [members, setMembers] = useState<TeamMember[]>([])
  const [invitations, setInvitations] = useState<TeamInvitation[]>([])
  const [email, setEmail] = useState('')
  const [created, setCreated] = useState<CreatedInvitation | null>(null)
  const [copied, setCopied] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const refresh = useCallback(async () => {
    setError('')
    try {
      const [nextMembers, nextInvitations] = await Promise.all([teamApi.members(credentials), teamApi.invitations(credentials)])
      setMembers(nextMembers)
      setInvitations(nextInvitations)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }, [credentials])

  useEffect(() => { const timer = window.setTimeout(() => { void refresh() }, 0); return () => window.clearTimeout(timer) }, [refresh])

  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const result = await teamApi.invite(credentials, email.trim())
      setCreated(result)
      setCopied(false)
      setEmail('')
      setNotice('Development invitation link created. No email was sent.')
      await refresh()
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setSaving(false) }
  }

  async function copyLink() {
    if (!created?.inviteUrl) return
    try { await navigator.clipboard.writeText(created.inviteUrl); setCopied(true) }
    catch { setError('Clipboard access is unavailable. Copy the invitation link manually before dismissing it.') }
  }

  async function revoke(invitation: TeamInvitation) {
    setError('')
    try {
      await teamApi.revokeInvitation(credentials, invitation.id)
      setNotice(`Invitation for ${invitation.email} was revoked.`)
      await refresh()
    } catch (reason) { setError(errorMessage(reason)) }
  }

  return <div className="team-page">
    <section className="list-panel team-intro">
      <div className="panel-heading"><div><span className="eyebrow">ADMINISTRATION</span><h2>Team management</h2></div><button className="secondary" onClick={() => void refresh()} disabled={loading}>Refresh</button></div>
      <p>Invite teammates as members and review registered accounts. Only administrators can manage invitations.</p>
    </section>

    <section className="list-panel team-invite">
      <div className="panel-heading"><div><span className="eyebrow">ADD A TEAMMATE</span><h2>Invite by email</h2></div></div>
      <form className="team-invite-form" onSubmit={invite}>
        <label>Email address<input type="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" /></label>
        <button className="primary" type="submit" disabled={saving || !email.trim()}>{saving ? 'Creating…' : 'Create invitation'}</button>
      </form>
      <p className="team-email-note">Email delivery is not configured. Invitation links are available only when local development mode is explicitly enabled.</p>
    </section>

    {created?.inviteUrl && <section className="invite-link-card" aria-live="polite">
      <div><span className="eyebrow">DEVELOPMENT LINK · COPY NOW</span><h2>Invitation ready</h2><p>This link is shown only in the creation response. Share it with the invitee using a trusted channel.</p></div>
      <code>{created.inviteUrl}</code>
      <div className="api-key-reveal-actions"><button className="primary" onClick={() => void copyLink()}>{copied ? 'Copied' : 'Copy invitation link'}</button><button className="secondary" onClick={() => setCreated(null)}>Dismiss link</button></div>
    </section>}

    {error && <div className="error" role="alert">{error}</div>}
    {notice && <div className="api-key-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notice">×</button></div>}

    <section className="list-panel team-list-panel">
      <div className="panel-heading"><div><span className="eyebrow">INVITATION STATUS</span><h2>Invitations</h2></div></div>
      {loading ? <p className="muted">Loading invitations…</p> : invitations.length === 0 ? <p className="muted">No invitations have been created.</p> : <div className="team-rows">{invitations.map((invitation) => <article className="team-row" key={invitation.id}>
        <div className="team-row-heading"><strong>{invitation.email}</strong><span className={`key-state ${invitation.status === 'PENDING' ? 'active' : invitation.status === 'EXPIRED' ? 'expired' : 'revoked'}`}>{invitation.status}</span></div>
        <div className="api-key-meta"><span>Created <b>{dateLabel(invitation.createdAt)}</b></span><span>Expires <b>{dateLabel(invitation.expiresAt)}</b></span>{invitation.acceptedAt && <span>Accepted <b>{dateLabel(invitation.acceptedAt)}</b></span>}</div>
        {invitation.status === 'PENDING' && <button className="key-revoke" onClick={() => void revoke(invitation)}>Revoke</button>}
      </article>)}</div>}
    </section>

    <section className="list-panel team-list-panel">
      <div className="panel-heading"><div><span className="eyebrow">REGISTERED ACCOUNTS</span><h2>Team members</h2></div><span className="tool-count">{members.length} accounts</span></div>
      {loading ? <p className="muted">Loading team…</p> : <div className="team-rows">{members.map((member) => <article className="team-row" key={member.id}>
        <div className="team-row-heading"><div><strong>{member.username}</strong><small>{member.email || 'No email on file'}</small></div><span className={`role-pill ${member.role.toLowerCase()}`}>{member.role}</span></div>
        <div className="api-key-meta"><span>Joined <b>{dateLabel(member.createdAt)}</b></span><span>Account <b>{member.enabled ? 'Enabled' : 'Disabled'}</b></span></div>
      </article>)}</div>}
    </section>
  </div>
}
