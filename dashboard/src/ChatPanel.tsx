import { useState } from 'react'
import type { FormEvent } from 'react'
import { chatApi, errorMessage } from './api'
import type { Credentials } from './api'

type Message = { role: 'user' | 'assistant'; text: string }

export default function ChatPanel({ credentials, storeId }: { credentials: Credentials; storeId?: string }) {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent) {
    event.preventDefault()
    const message = input.trim()
    if (!message || pending) return
    setMessages((current) => [...current, { role: 'user', text: message }])
    setInput('')
    setPending(true)
    setError('')
    try {
      const response = await chatApi.send(credentials, message, storeId)
      setMessages((current) => [...current, { role: 'assistant', text: response.reply }])
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  return <section className="list-panel chat-panel">
    <div className="panel-heading"><div><span className="eyebrow">AI ASSISTANT</span><h2>Chat with your MCP Gateway</h2></div></div>
    <div className="chat-messages" aria-live="polite">
      {messages.length === 0 && <p className="muted">Ask about your integrations, store products or orders, current weather, or GitHub repositories.</p>}
      {messages.map((message, index) => <article className={`chat-message ${message.role}`} key={`${index}-${message.role}`}><strong>{message.role === 'user' ? 'You' : 'Urmi.AI'}</strong><p>{message.text}</p></article>)}
      {pending && <p className="muted" role="status">Urmi.AI is thinking…</p>}
    </div>
    {error && <div className="error" role="alert">{error}</div>}
    <form className="chat-form" onSubmit={submit}>
      <label className="sr-only" htmlFor="chat-message">Message</label>
      <textarea id="chat-message" value={input} onChange={(event) => setInput(event.target.value)} maxLength={4000} rows={3} placeholder="Ask a question…" disabled={pending} />
      <button className="primary" type="submit" disabled={pending || !input.trim()}>{pending ? 'Sending…' : 'Send'}</button>
    </form>
  </section>
}
