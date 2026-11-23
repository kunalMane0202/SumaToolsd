import { PageHead } from '../components.jsx'

/** Sections that are in the plan but not built yet. They say plainly what will come. */
const PLANS = {
  knowledge: {
    title: 'Knowledge Base',
    text: 'SOPs and FAQs the agents use while solving tickets.',
    items: ['Articles per project and topic', 'Search while working on a ticket', 'Who wrote and updated each article', 'Most-used articles report'],
  },
  settings: {
    title: 'Settings',
    text: 'Platform-wide configuration.',
    items: ['Company holidays', 'Default time zone and working hours', 'Password and session rules', 'Email (SMTP) settings for notifications'],
  },
}

export default function PlannedPage({ which }) {
  const p = PLANS[which]
  return (
    <div className="page narrow">
      <PageHead title={p.title} text={p.text} />
      <section className="panel planned">
        <span className="tag">Planned, not built yet</span>
        <h2>What this section will do</h2>
        <ul>
          {p.items.map((i) => <li key={i}>{i}</li>)}
        </ul>
      </section>
    </div>
  )
}