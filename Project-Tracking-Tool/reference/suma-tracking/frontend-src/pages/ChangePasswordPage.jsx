import { useState } from 'react'
import { api } from '../api.js'

export default function ChangePasswordPage({ user, onDone, onLogout }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    if (!current) {
      setError('Enter your current (temporary) password.')
      return
    }
    if (next.length < 10 || !/[A-Za-z]/.test(next) || !/[0-9]/.test(next) || /\s/.test(next)) {
      setError('The new password must be at least 10 characters with at least one letter and one digit, and no spaces.')
      return
    }
    if (next !== again) {
      setError('The two new passwords are not the same.')
      return
    }
    if (next === current) {
      setError('The new password must be different from the current one.')
      return
    }
    setBusy(true)
    try {
      await api.changePassword(current, next)
      const me = await api.me()
      onDone(me)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login single">
      <section className="login-form-wrap">
        <form className="login-form" onSubmit={submit}>
          <h2>Choose your own password</h2>
          <p className="muted">
            Hello {user.fullName}. You are using a temporary password. Choose a new one before you continue:
            at least 10 characters, with a letter and a digit.
          </p>
          <label className="field">
            <span>Current (temporary) password</span>
            <input type="password" autoComplete="current-password" value={current}
              onChange={(e) => setCurrent(e.target.value)} required />
          </label>
          <label className="field">
            <span>New password</span>
            <input type="password" autoComplete="new-password" value={next}
              onChange={(e) => setNext(e.target.value)} required minLength={10} />
          </label>
          <label className="field">
            <span>New password again</span>
            <input type="password" autoComplete="new-password" value={again}
              onChange={(e) => setAgain(e.target.value)} required minLength={10} />
          </label>
          {error && <div className="alert error" role="alert">{error}</div>}
          <button className="btn primary wide" type="submit" disabled={busy}>
            {busy ? 'Saving...' : 'Save new password'}
          </button>
          <button className="btn ghost wide" type="button" onClick={onLogout}>Log out</button>
        </form>
      </section>
    </div>
  )
}