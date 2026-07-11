'use client'

import { useState, FormEvent, useEffect } from 'react'
import styles from './page.module.css'

export default function LandingPage() {
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState('')
  const [count, setCount] = useState(0)

  const fetchCount = async () => {
    try {
      const res = await fetch('/api/waitlist')
      const data = await res.json()
      setCount(data.count || 0)
    } catch {}
  }

  useEffect(() => { fetchCount() }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setStatus('loading')
    setMessage('')

    try {
      const res = await fetch('/api/waitlist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, name, phone, source: 'landing' })
      })
      const data = await res.json()
      if (data.success) {
        setStatus('success')
        setMessage(data.message || 'Welcome to the waitlist')
        setEmail('')
        setName('')
        setPhone('')
        setCount(data.count || count + 1)
      } else {
        setStatus('error')
        setMessage(data.error || 'Something went wrong')
      }
    } catch {
      setStatus('error')
      setMessage('Network error. Please try again.')
    }
  }

  return (
    <main className={styles.main}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <svg viewBox="0 0 32 32" fill="none" width="32" height="32">
            <rect width="32" height="32" rx="6" fill="#0C0C0E"/>
            <path d="M10 10h12M10 16h8M10 22h10" stroke="#E8D9C0" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          <span>INSIDHER</span>
        </div>
      </header>

      <section className={styles.hero}>
        <h1 className={styles.title}>
          Private Receptionist.<br/>Sovereign Assistant.
        </h1>
        <p className={styles.subtitle}>
          Clients meet your custom front desk. You work with <strong>Anita</strong> — who remembers everything, drafts with care, and never confuses the two rooms.
        </p>

        <div className={styles.stats}>
          <div className={styles.stat}>
            <span className={styles.statNumber}>{count}+</span>
            <span className={styles.statLabel}>On the waitlist</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statNumber}>2</span>
            <span className={styles.statLabel}>Agents</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statNumber}>3</span>
            <span className={styles.statLabel}>Platforms</span>
          </div>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.fieldGroup}>
            <label htmlFor="name" className={styles.label}>Name</label>
            <input id="name" type="text" value={name} onChange={e => setName(e.target.value)} placeholder="Your name" className={styles.input} />
          </div>
          <div className={styles.fieldGroup}>
            <label htmlFor="email" className={styles.label}>Email</label>
            <input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@domain.com" required className={styles.input} />
          </div>
          <div className={styles.fieldGroup}>
            <label htmlFor="phone" className={styles.label}>Phone (optional)</label>
            <input id="phone" type="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="+1 555 123 4567" className={styles.input} />
          </div>
          <button type="submit" className={styles.submitBtn} disabled={status === 'loading'}>
            {status === 'loading' ? 'Joining...' : 'Join Waitlist'}
          </button>
          <p className={styles.privacy}>We respect your privacy. No spam. Unsubscribe anytime.</p>
        </form>

        {status === 'success' && (
          <div className={styles.success}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#4CAF50" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/><path d="M8 12l2 2 4-4"/>
            </svg>
            <span>{message}</span>
          </div>
        )}
        {status === 'error' && (
          <div className={styles.error}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#CF6679" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span>{message}</span>
          </div>
        )}
      </section>

      <section className={styles.features}>
        <h2 className={styles.sectionTitle}>How it works</h2>
        <div className={styles.grid}>
          {[
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6M9 15h6"/></svg>), title: 'Custom Receptionist', text: 'Name, tone, hours, boundaries — your clients see a front desk that feels like you.' },
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>), title: 'Anita — Your PA', text: 'Separate chat for you. Drafts replies, recalls context, protects your calendar.' },
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>), title: 'Native SMS + Deposits', text: 'Send/receive from your real number. Request deposits with secure payment links.' },
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>), title: 'Encrypted Vault', text: 'SQLCipher + Android Keystore. Export or delete anytime. Your data, your keys.' },
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>), title: 'Cross-Platform', text: 'iOS, Android, Web — same vault, same Anita, same sovereignty.' },
            { icon: (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>), title: 'Referral Rewards', text: 'Earn subscription credits when colleagues join. Built-in, fraud-resistant.' }
          ].map((feature, i) => (
            <article key={i} className={styles.card}>
              <div className={styles.icon}>{feature.icon}</div>
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </article>
          ))}
        </div>
      </section>

      <footer className={styles.footer}>
        <p>Insidher — Discreet. Personalized. Sovereign.</p>
        <div className={styles.links}>
          <a href="/privacy">Privacy</a>
          <a href="/terms">Terms</a>
          <a href="mailto:hello@insidher.app">Contact</a>
        </div>
      </footer>
    </main>
  )
}