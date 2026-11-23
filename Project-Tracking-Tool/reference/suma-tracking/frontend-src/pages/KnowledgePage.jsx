import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { Drawer, Field, PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE } from '../validation.js'
import { fmtDateTime } from '../format.js'

const EXAMPLES_SEARCH = ['add employee', 'import excel', 'password', 'SLA', 'escalate', 'holiday']
const EXAMPLES_ASK = ['how do i give a ticket to another person', 'naya karmachari kaise banaye', 'sheet upload karne pe error aa raha hai']

/** Opened guide comes from the address: #/knowledge/add-employee */
function slugFromHash() {
  const parts = window.location.hash.replace('#/', '').split('?')[0].split('/')
  return parts[0] === 'knowledge' && parts[1] ? decodeURIComponent(parts[1]) : ''
}

function openGuide(slug) {
  window.location.hash = slug ? `#/knowledge/${slug}` : '#/knowledge'
}

/**
 * Knowledge Base (Step 38): simple guides on how Suma Tracking works.
 * Search with simple words, or ask in your own words. It only explains the tool; it never opens or changes tickets.
 */
export default function KnowledgePage({ user }) {
  const can = useCan()
  const isAdmin = can('KB_MANAGE')
  const [slug, setSlug] = useState(slugFromHash())
  const [articles, setArticles] = useState([])
  const [tab, setTab] = useState('guides')
  const [editing, setEditing] = useState(null)
  const [toast, setToast] = useState('')
  const [error, setError] = useState('')

  const loadList = useCallback(() => {
    api.kbArticles(isAdmin).then((a) => setArticles(a || [])).catch((e) => setError(e.message))
  }, [isAdmin])
  useEffect(() => { loadList() }, [loadList])
  useEffect(() => {
    const onHash = () => setSlug(slugFromHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  return (
    <div className="page narrow">
      <PageHead title="Knowledge Base" text="Simple guides on how Suma Tracking works. Search with simple words, or ask in your own words.">
        {isAdmin && <button className="btn primary" type="button" onClick={() => setEditing({})}>Add guide</button>}
      </PageHead>
      {isAdmin && !slug && (
        <div className="segmented" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'guides'} className={tab === 'guides' ? 'on' : ''} onClick={() => setTab('guides')}>Guides</button>
          <button type="button" role="tab" aria-selected={tab === 'missing'} className={tab === 'missing' ? 'on' : ''} onClick={() => setTab('missing')}>Questions with no answer</button>
        </div>
      )}
      {error && <div className="alert error" role="alert">{error}</div>}
      {slug && (
        <GuideView slug={slug} isAdmin={isAdmin} onEdit={(a) => setEditing(a)} />
      )}
      {!slug && tab === 'guides' && (
        <>
          <Finder />
          <Browse articles={articles} isAdmin={isAdmin} />
        </>
      )}
      {!slug && tab === 'missing' && isAdmin && <Unanswered onWrite={(q) => setEditing({ title: q })} />}
      {editing && (
        <GuideEditor
          article={editing}
          articles={articles}
          onClose={() => setEditing(null)}
          onSaved={(a, wasEdit) => {
            setEditing(null)
            setToast(wasEdit ? 'Guide updated' : 'Guide added')
            loadList()
            openGuide(a.slug)
            setSlug(a.slug)
          }}
        />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

// ================================================================ search + ask

function Finder() {
  const [mode, setMode] = useState('search')
  const [text, setText] = useState('')
  const [results, setResults] = useState(null)
  const [answer, setAnswer] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const timer = useRef(null)

  const runSearch = useCallback((q) => {
    if (!q.trim()) { setResults(null); return }
    setBusy(true)
    api.kbSearch(q).then((r) => { setResults(r || []); setError('') }).catch((e) => setError(e.message)).finally(() => setBusy(false))
  }, [])

  function onType(value) {
    setText(value)
    if (mode !== 'search') return
    clearTimeout(timer.current)
    timer.current = setTimeout(() => runSearch(value), 350)
  }
  useEffect(() => () => clearTimeout(timer.current), [])

  async function ask(e) {
    if (e) e.preventDefault()
    const q = text.trim()
    if (!q) { setError('Type your question first.'); return }
    if (q.length > 300) { setError('Please keep the question under 300 characters.'); return }
    setError('')
    setBusy(true)
    try {
      setAnswer(await api.kbAsk(q))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function switchTo(next) {
    setMode(next)
    setError('')
    if (next === 'search') { setAnswer(null); runSearch(text) } else { setResults(null); if (text.trim()) setAnswer(null) }
  }

  function pickExample(q) {
    setText(q)
    if (mode === 'search') runSearch(q)
    else {
      setBusy(true)
      api.kbAsk(q).then(setAnswer).catch((e) => setError(e.message)).finally(() => setBusy(false))
    }
  }

  return (
    <section className="panel kb-finder">
      <div className="segmented" role="tablist" aria-label="How to find help">
        <button type="button" role="tab" aria-selected={mode === 'search'} className={mode === 'search' ? 'on' : ''} onClick={() => switchTo('search')}>Search</button>
        <button type="button" role="tab" aria-selected={mode === 'ask'} className={mode === 'ask' ? 'on' : ''} onClick={() => switchTo('ask')}>Ask in your own words</button>
      </div>
      <form className="kb-search-row" onSubmit={mode === 'ask' ? ask : (e) => { e.preventDefault(); runSearch(text) }}>
        <label htmlFor="kb-q" className="sr-only">{mode === 'ask' ? 'Your question' : 'Search the guides'}</label>
        <input id="kb-q" type="search" value={text} maxLength={300} autoComplete="off"
          placeholder={mode === 'ask' ? 'Type your question, e.g. how do I give a ticket to another person?' : 'Search, e.g. add employee, import excel, password'}
          onChange={(e) => onType(e.target.value)} />
        <button className="btn primary" type="submit" disabled={busy}>{busy ? 'Looking...' : mode === 'ask' ? 'Ask' : 'Search'}</button>
      </form>
      <div className="kb-examples">
        <span className="muted small">Try:</span>
        {(mode === 'ask' ? EXAMPLES_ASK : EXAMPLES_SEARCH).map((q) => (
          <button key={q} type="button" className="kb-chip" onClick={() => pickExample(q)}>{q}</button>
        ))}
      </div>
      {mode === 'ask' && (
        <p className="fine">
          Ask mode understands normal sentences, small spelling mistakes and common Hindi words. It picks the best part of our guides.
          It is not connected to an AI model yet, and it does not open or change tickets.
        </p>
      )}
      {error && <div className="alert error" role="alert">{error}</div>}

      {mode === 'search' && results && (
        results.length === 0 ? (
          <div className="kb-none">
            <strong>No guide found for "{text}".</strong>
            <p>Try one or two simpler words, or ask the question in your own words.</p>
            <button className="btn" type="button" onClick={() => { setMode('ask'); ask() }}>Ask "{text}" in my own words</button>
          </div>
        ) : (
          <ul className="kb-results">
            {results.map((r) => (
              <li key={r.slug}>
                <a href={`#/knowledge/${r.slug}`}><strong>{r.title}</strong></a>
                <span className="kb-cat">{r.category}</span>
                <p>{r.snippet}</p>
              </li>
            ))}
          </ul>
        )
      )}

      {mode === 'ask' && answer && (
        answer.found ? (
          <div className="kb-answer">
            <div className="kb-answer-head">
              <span className="kb-cat">Best answer</span>
              <a href={`#/knowledge/${answer.answer.slug}`}>{answer.answer.title}</a>
            </div>
            {answer.answer.heading && <h3>{answer.answer.heading}</h3>}
            <GuideText text={answer.answer.text} />
            <a className="btn" href={`#/knowledge/${answer.answer.slug}`}>Read the full guide</a>
            {answer.understood.length > 0 && <p className="fine">Words I understood: {answer.understood.join(', ')}</p>}
            {answer.related.length > 0 && (
              <div className="kb-related">
                <strong>Also helpful</strong>
                <ul>{answer.related.map((r) => <li key={r.slug}><a href={`#/knowledge/${r.slug}`}>{r.title}</a></li>)}</ul>
              </div>
            )}
          </div>
        ) : (
          <div className="kb-none">
            <strong>Sorry, I could not find an answer to that.</strong>
            <p>Your question is saved, and the Super Admin will add a guide for it. Meanwhile, these guides are read the most:</p>
            <ul>{answer.suggestions.map((r) => <li key={r.slug}><a href={`#/knowledge/${r.slug}`}>{r.title}</a></li>)}</ul>
          </div>
        )
      )}
    </section>
  )
}

// ================================================================ browse

function Browse({ articles, isAdmin }) {
  const groups = useMemo(() => {
    const map = new Map()
    articles.forEach((a) => {
      if (!map.has(a.category)) map.set(a.category, [])
      map.get(a.category).push(a)
    })
    return [...map.entries()]
  }, [articles])
  if (!articles.length) return null
  return (
    <section>
      <h2 className="kb-browse-title">All guides by topic</h2>
      <div className="kb-groups">
        {groups.map(([cat, list]) => (
          <div className="panel kb-group" key={cat}>
            <h3>{cat}</h3>
            <ul>
              {list.map((a) => (
                <li key={a.slug}>
                  <a href={`#/knowledge/${a.slug}`}>{a.title}</a>
                  {isAdmin && a.status === 'HIDDEN' && <span className="tag">Hidden</span>}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

// ================================================================ one guide

function GuideView({ slug, isAdmin, onEdit }) {
  const [a, setA] = useState(null)
  const [error, setError] = useState('')
  const [voted, setVoted] = useState('')

  useEffect(() => {
    setA(null)
    setVoted('')
    setError('')
    api.kbArticle(slug).then(setA).catch((e) => setError(e.message))
  }, [slug])

  async function vote(helpful) {
    try {
      await api.kbFeedback(slug, helpful)
      setVoted(helpful ? 'yes' : 'no')
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <article className="panel kb-article">
      <a className="kb-back" href="#/knowledge">{'\u2190'} All guides</a>
      {error && <div className="alert error" role="alert">{error === 'Guide not found' ? 'This guide does not exist or is hidden.' : error}</div>}
      {!a && !error && <p className="muted">Loading...</p>}
      {a && (
        <>
          <div className="kb-article-head">
            <span className="kb-cat">{a.category}</span>
            {a.status === 'HIDDEN' && <span className="tag">Hidden: only the Super Admin sees it</span>}
            {isAdmin && <button className="btn" type="button" onClick={() => onEdit(a)}>Edit guide</button>}
          </div>
          <h2 className="kb-title">{a.title}</h2>
          <p className="kb-summary">{a.summary}</p>
          <GuideText text={a.body} />
          <div className="kb-vote">
            {voted ? <span className="muted">Thank you for telling us.</span> : (
              <>
                <span>Was this guide helpful?</span>
                <button className="btn" type="button" onClick={() => vote(true)}>Yes</button>
                <button className="btn" type="button" onClick={() => vote(false)}>No</button>
              </>
            )}
          </div>
          <p className="fine">Last updated {fmtDateTime(a.updatedAt)}{a.updatedBy ? ` by ${a.updatedBy}` : ''} {'\u00b7'} version {a.versionNo}</p>
        </>
      )}
    </article>
  )
}

/**
 * Shows guide text. Simple writing rules:
 *   "## Heading", "1. step", "- point", "Tip: ...", "Note: ...", "Warning: ...",
 *   an indented line starting with "powershell" or "cd " is a command (with a Copy button).
 */
export function GuideText({ text }) {
  const blocks = []
  let list = null
  const flush = () => { if (list) { blocks.push(list); list = null } }
  ;(text || '').split('\n').forEach((raw) => {
    const line = raw.replace(/\s+$/, '')
    const trimmed = line.trim()
    const indented = /^\s{2,}/.test(line)
    if (!trimmed) { flush(); return }
    if (indented && /^(powershell|cd |npm |git |\.\\)/i.test(trimmed)) {
      const target = list && list.items.length ? list.items[list.items.length - 1] : null
      if (target) target.extra.push({ kind: 'code', text: trimmed })
      else { flush(); blocks.push({ kind: 'code', text: trimmed }) }
      return
    }
    if (indented && list && list.items.length) {
      list.items[list.items.length - 1].extra.push({ kind: 'text', text: trimmed.replace(/^- /, ''), dot: trimmed.startsWith('- ') })
      return
    }
    if (trimmed.startsWith('## ')) { flush(); blocks.push({ kind: 'h', text: trimmed.slice(3) }); return }
    const num = trimmed.match(/^(\d+)\.\s+(.*)$/)
    if (num) {
      if (!list || list.kind !== 'ol') { flush(); list = { kind: 'ol', items: [] } }
      list.items.push({ text: num[2], extra: [] })
      return
    }
    if (trimmed.startsWith('- ')) {
      if (!list || list.kind !== 'ul') { flush(); list = { kind: 'ul', items: [] } }
      list.items.push({ text: trimmed.slice(2), extra: [] })
      return
    }
    const box = trimmed.match(/^(Tip|Note|Warning):\s*(.*)$/)
    if (box) { flush(); blocks.push({ kind: 'box', tone: box[1].toLowerCase(), label: box[1], text: box[2] }); return }
    flush()
    blocks.push({ kind: 'p', text: trimmed })
  })
  flush()

  return (
    <div className="kb-text">
      {blocks.map((b, i) => {
        if (b.kind === 'h') return <h3 key={i}>{b.text}</h3>
        if (b.kind === 'p') return <p key={i}>{b.text}</p>
        if (b.kind === 'code') return <Command key={i} text={b.text} />
        if (b.kind === 'box') return <div key={i} className={`kb-box ${b.tone}`}><strong>{b.label}:</strong> {b.text}</div>
        const Tag = b.kind === 'ol' ? 'ol' : 'ul'
        return (
          <Tag key={i}>
            {b.items.map((it, j) => (
              <li key={j}>
                {it.text}
                {it.extra.map((x, k) => (x.kind === 'code' ? <Command key={k} text={x.text} /> : <div key={k} className={`kb-sub${x.dot ? ' dot' : ''}`}>{x.text}</div>))}
              </li>
            ))}
          </Tag>
        )
      })}
    </div>
  )
}

function Command({ text }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="kb-code">
      <code>{text}</code>
      <button type="button" className="btn small" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
    </div>
  )
}

// ================================================================ Super Admin: unanswered + editor

function Unanswered({ onWrite }) {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => { api.kbUnanswered().then((r) => setRows(r || [])).catch((e) => setError(e.message)) }, [])
  return (
    <section className="panel flush">
      <div className="panel-head sla-pad"><h2>Questions with no answer</h2><p>What people searched or asked, where no guide matched. Write a guide for the common ones.</p></div>
      {error && <div className="alert error" role="alert">{error}</div>}
      <div className="table-wrap">
        <table className="table grid-table">
          <thead><tr><th scope="col">Question</th><th scope="col">How</th><th scope="col">Who</th><th scope="col">When</th><th scope="col">Action</th></tr></thead>
          <tbody>
            {rows && rows.length === 0 && <tr><td colSpan={5} className="empty-row">Nothing yet. Every search found a guide.</td></tr>}
            {(rows || []).map((r) => (
              <tr key={r.id}>
                <td className="strong">{r.question}</td>
                <td>{r.mode === 'ASK' ? 'Asked' : 'Searched'}</td>
                <td>{r.askedBy || '-'}</td>
                <td className="nowrap">{fmtDateTime(r.askedAt)}</td>
                <td><button className="btn small" type="button" onClick={() => onWrite(r.question)}>Write a guide</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function toSlug(s) {
  return (s || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120)
}

function GuideEditor({ article, articles, onClose, onSaved }) {
  const isEdit = Boolean(article.id)
  const [f, setF] = useState({
    title: article.title || '',
    slug: article.slug || '',
    category: article.category || '',
    summary: article.summary || '',
    keywords: article.keywords || '',
    status: article.status || 'PUBLISHED',
    body: article.body || '## Steps\n1. First step\n2. Second step\n\nTip: A helpful tip.',
  })
  const [slugTouched, setSlugTouched] = useState(isEdit)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState(false)
  const [versions, setVersions] = useState([])
  const categories = useMemo(() => [...new Set(articles.map((a) => a.category))], [articles])

  useEffect(() => {
    if (isEdit) api.kbVersions(article.id).then((v) => setVersions(v || [])).catch(() => setVersions([]))
  }, [isEdit, article.id])

  function set(k, v) {
    const next = { ...f, [k]: v }
    if (k === 'title' && !slugTouched) next.slug = toSlug(v)
    setF(next)
    if (errors[k]) setErrors({ ...errors, [k]: check(next)[k] || '' })
  }

  function check(v) {
    const e = {}
    if (v.title.trim().length < 3) e.title = 'Title must have at least 3 characters.'
    else if (v.title.trim().length > 200) e.title = 'Title is too long (maximum 200 characters).'
    if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(v.slug)) e.slug = 'Use small letters, numbers and single hyphens, like add-employee.'
    if (!v.category.trim()) e.category = 'Choose or type a category.'
    else if (v.category.trim().length > 60) e.category = 'Category is too long (maximum 60 characters).'
    if (!v.summary.trim()) e.summary = 'Write one short sentence about the guide.'
    else if (v.summary.trim().length > 500) e.summary = 'Summary is too long (maximum 500 characters).'
    if (v.keywords.length > 1000) e.keywords = 'Keywords are too long (maximum 1000 characters).'
    if (v.body.trim().length < 20) e.body = 'The guide text is too short.'
    return e
  }

  async function save() {
    setError('')
    const e = check(f)
    setErrors(e)
    if (Object.keys(e).length) { setError(FIX_MESSAGE); return }
    const body = { ...f, title: f.title.trim(), category: f.category.trim(), summary: f.summary.trim(), keywords: f.keywords.trim() }
    setBusy(true)
    try {
      const saved = isEdit ? await api.kbUpdate(article.id, body) : await api.kbCreate(body)
      onSaved(saved, isEdit)
    } catch (err) {
      setError(err.message)
      setErrors(err.fieldErrors || {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer title={isEdit ? `Edit guide: ${article.title}` : 'Add guide'} onClose={onClose} wide
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="button" onClick={save} disabled={busy}>{busy ? 'Saving...' : isEdit ? 'Update' : 'Add'}</button>
        </>
      )}>
      <div className="form">
        <Field label="Title *" error={errors.title}><input value={f.title} maxLength={200} onChange={(e) => set('title', e.target.value)} placeholder="e.g. Add a new employee" /></Field>
        <Field label="Short web name *" error={errors.slug} hint="Used in the link. Small letters and hyphens.">
          <input value={f.slug} maxLength={120} onChange={(e) => { setSlugTouched(true); set('slug', e.target.value) }} />
        </Field>
        <Field label="Category *" error={errors.category}>
          <input value={f.category} maxLength={60} list="kb-cats" onChange={(e) => set('category', e.target.value)} placeholder="e.g. Team" />
          <datalist id="kb-cats">{categories.map((c) => <option key={c} value={c} />)}</datalist>
        </Field>
        <Field label="Short summary *" error={errors.summary}><input value={f.summary} maxLength={500} onChange={(e) => set('summary', e.target.value)} /></Field>
        <Field label="Keywords" error={errors.keywords} hint="Words people may type, also Hindi words. Example: add employee new staff karmachari">
          <input value={f.keywords} maxLength={1000} onChange={(e) => set('keywords', e.target.value)} />
        </Field>
        <Field label="Status">
          <select value={f.status} onChange={(e) => set('status', e.target.value)}>
            <option value="PUBLISHED">Published (everyone sees it)</option>
            <option value="HIDDEN">Hidden (only Super Admin)</option>
          </select>
        </Field>
        <div className={`field${errors.body ? ' invalid' : ''}`}>
          <span>Guide text *</span>
          <div className="segmented small-seg">
            <button type="button" className={!preview ? 'on' : ''} onClick={() => setPreview(false)}>Write</button>
            <button type="button" className={preview ? 'on' : ''} onClick={() => setPreview(true)}>Preview</button>
          </div>
          {preview ? <div className="kb-preview"><GuideText text={f.body} /></div> : (
            <textarea rows={16} value={f.body} onChange={(e) => set('body', e.target.value)} className="kb-editor" aria-label="Guide text" />
          )}
          <small className="hint">Writing rules: "## " for a heading, "1. " for steps, "- " for points, "Tip:", "Note:" or "Warning:" for boxes. Indent a command line (start with spaces) to show a Copy button.</small>
          {errors.body && <small className="field-error" role="alert">{'\u26a0 '}{errors.body}</small>}
        </div>
        {error && <div className="alert error" role="alert">{error}</div>}
        {isEdit && versions.length > 0 && (
          <div className="sub-section">
            <h3>Change history</h3>
            <ul className="kb-versions">
              {versions.map((v) => <li key={v.versionNo}>Version {v.versionNo}: "{v.title}" ({v.status.toLowerCase()}), {fmtDateTime(v.changedAt)}{v.changedBy ? ` by ${v.changedBy}` : ''}</li>)}
            </ul>
          </div>
        )}
      </div>
    </Drawer>
  )
}