import { useEffect, useState } from 'react';
import ObservabilityPanel from './ObservabilityPanel';
import {
  getOperationsHealth,
  getPayment,
  getTransfer,
  listNotifications,
  listPayments,
  listTransfers
} from './api';

export default function OperationsPanel({ token, roles }) {
  const [health, setHealth] = useState(null);
  const [payments, setPayments] = useState([]);
  const [transfers, setTransfers] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [searchType, setSearchType] = useState('payment');
  const [searchId, setSearchId] = useState('');
  const [searchResult, setSearchResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function refresh() {
    setLoading(true);
    setError('');
    try {
      const [healthResult, paymentPage, transferPage, notificationPage] = await Promise.all([
        getOperationsHealth(token),
        listPayments(token, { size: 50 }),
        listTransfers(token, { size: 50 }),
        listNotifications(token, { size: 50 })
      ]);
      setHealth(healthResult);
      setPayments(paymentPage?.content || []);
      setTransfers(transferPage?.content || []);
      setNotifications(notificationPage?.content || []);
    } catch (e) {
      setError(`${e.message}${e.correlationId ? ` (correlation ${e.correlationId})` : ''}`);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, [token]);

  async function runSearch(event) {
    event.preventDefault();
    if (!searchId.trim()) return;
    setError('');
    setSearchResult(null);
    try {
      const result = searchType === 'payment'
        ? await getPayment(token, searchId.trim())
        : await getTransfer(token, searchId.trim());
      setSearchResult(result);
    } catch (e) {
      setError(`${e.message}${e.correlationId ? ` (correlation ${e.correlationId})` : ''}`);
    }
  }

  const failedPayments = payments.filter(p => ['FAILED', 'DECLINED', 'RECONCILIATION_REQUIRED'].includes(p.status));
  const failedTransfers = transfers.filter(t => ['FAILED', 'RECONCILIATION_REQUIRED'].includes(t.status));
  const failedNotifications = notifications.filter(n => n.status === 'FAILED');

  return (
    <section className="stack">
      <div className="row">
        <div>
          <p className="eyebrow">Operations workspace</p>
          <h2>Support / Admin dashboard</h2>
          <p>Roles: {roles.join(', ')}</p>
        </div>
        <button className="secondary" onClick={refresh} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh operations'}</button>
      </div>

      {error && <p className="error">{error}</p>}
      <ObservabilityPanel />

      <section className="dashboardGrid">
        <article className="card metric"><span>Services</span><strong>{health?.services?.length ?? '—'}</strong><small>{health?.allHealthy ? 'All healthy' : 'Attention required'}</small></article>
        <article className="card metric"><span>Problem payments</span><strong>{failedPayments.length}</strong><small>failed / declined / reconciliation</small></article>
        <article className="card metric"><span>Problem transfers</span><strong>{failedTransfers.length}</strong><small>failed / reconciliation</small></article>
        <article className="card metric"><span>Failed notifications</span><strong>{failedNotifications.length}</strong><small>delivery failures</small></article>
      </section>

      <section className="card">
        <div className="row"><div><h2>Service health</h2><p>Live actuator checks aggregated by the Edge Gateway.</p></div><span className="badge">{health?.allHealthy ? 'UP' : 'CHECK'}</span></div>
        <div className="tableWrap"><table><thead><tr><th>Service</th><th>Status</th><th>Latency</th><th>Error</th></tr></thead><tbody>
          {(health?.services || []).map(item => <tr key={item.service}><td>{item.service}</td><td><span className="badge">{item.status}</span></td><td>{item.latencyMs} ms</td><td>{item.error || '—'}</td></tr>)}
          {!health?.services?.length && <tr><td colSpan="4">No health data loaded.</td></tr>}
        </tbody></table></div>
      </section>

      <section className="card">
        <h2>Operational lookup</h2>
        <p>Search a specific payment or transfer ID. Use Observability above to search correlation IDs in centralized logs.</p>
        <form className="inlineForm" onSubmit={runSearch}>
          <label>Type<select value={searchType} onChange={e => setSearchType(e.target.value)}><option value="payment">Payment</option><option value="transfer">Transfer</option></select></label>
          <label className="grow">ID<input value={searchId} onChange={e => setSearchId(e.target.value)} placeholder="UUID" required /></label>
          <button>Search</button>
        </form>
        {searchResult && <pre className="resultBox">{JSON.stringify(searchResult, null, 2)}</pre>}
      </section>

      <section className="twoCol">
        <ProblemTable title="Payments requiring attention" rows={failedPayments} idKey="paymentId" />
        <ProblemTable title="Transfers requiring attention" rows={failedTransfers} idKey="transferId" />
      </section>

      <section className="card">
        <h2>Recent notification activity</h2>
        <div className="tableWrap"><table><thead><tr><th>ID</th><th>Event</th><th>Channel</th><th>Status</th></tr></thead><tbody>
          {notifications.slice(0, 20).map(n => <tr key={n.id}><td>{short(n.id)}</td><td>{n.eventType}</td><td>{n.channel}</td><td><span className="badge">{n.status}</span></td></tr>)}
          {notifications.length === 0 && <tr><td colSpan="4">No notifications.</td></tr>}
        </tbody></table></div>
      </section>
    </section>
  );
}

function ProblemTable({ title, rows, idKey }) {
  return <div className="card"><h2>{title}</h2><div className="tableWrap"><table><thead><tr><th>ID</th><th>Status</th><th>Amount</th></tr></thead><tbody>
    {rows.slice(0, 20).map(row => <tr key={row[idKey]}><td>{short(row[idKey])}</td><td><span className="badge">{row.status}</span></td><td>{row.amount}</td></tr>)}
    {rows.length === 0 && <tr><td colSpan="3">Nothing requires attention.</td></tr>}
  </tbody></table></div></div>;
}

function short(value) {
  return value ? String(value).slice(0, 8) : '—';
}
