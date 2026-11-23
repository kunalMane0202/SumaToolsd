import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { PageHead } from '../components.jsx'
import ImportPage from './ImportPage.jsx'

export default function IntegrationsPage() {
  const [tab, setTab] = useState('import')
  const [projects, setProjects] = useState([])
  useEffect(() => { api.projects().then(setProjects).catch(() => setProjects([])) }, [])

  return (
    <div className="page">
      <PageHead title="Integration Management" text="How tickets from each client's tool reach this platform." />
      <div className="segmented" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'import'} className={tab === 'import' ? 'on' : ''} onClick={() => setTab('import')}>Excel import</button>
        <button type="button" role="tab" aria-selected={tab === 'tools'} className={tab === 'tools' ? 'on' : ''} onClick={() => setTab('tools')}>Connected tools</button>
      </div>
      {tab === 'import' && <ImportPage embedded />}
      {tab === 'tools' && (
        <section className="panel flush">
          <div className="table-wrap">
            <table className="table grid-table">
              <thead>
                <tr>
                  <th scope="col">Project</th>
                  <th scope="col">Client</th>
                  <th scope="col">Ticketing tool</th>
                  <th scope="col">Excel import</th>
                  <th scope="col">Direct API connection</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((p) => (
                  <tr key={p.id}>
                    <td className="strong">{p.projectName}</td>
                    <td>{p.clientName}</td>
                    <td>{p.externalTicketSystem || <span className="muted">None</span>}</td>
                    <td><span className="tag ok">Working</span></td>
                    <td><span className="tag">Not connected yet</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="fine pad">A direct connection to Zendesk or Keypanion needs an API access key from each client. Until then, tickets come in through the daily Excel import.</p>
        </section>
      )}
    </div>
  )
}