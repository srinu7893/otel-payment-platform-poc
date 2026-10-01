import { useEffect, useMemo, useState } from 'react';
import {
  createPayment,
  createRefund,
  createTransfer,
  getCustomer,
  listNotifications,
  listPayments,
  listTransfers,
  login
} from './api';
import OperationsPanel from './OperationsPanel';

export default function App() {
  const [session, setSession] = useState(null);
  const [profile, setProfile] = useState(null);
  const [payments, setPayments] = useState([]);
  const [transfers, setTransfers] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [tab, setTab] = useState('overview');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const roles = useMemo(() => session?.roles || [], [session]);
  const privileged = roles.includes('SUPPORT') || roles.includes('ADMIN');

  async function handleLogin(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const result = await login(form.get('username'), form.get('password'));
      setSession(result);
      setTab((result.roles || []).some(r => r === 'SUPPORT' || r === 'ADMIN') ? 'operations' : 'overview');
    } catch (e) {
      setError(`${e.message}${e.correlationId ? ` (correlation ${e.correlationId})` : ''}`);
    } finally {
      setLoading(false);
    }
  }

  async function refreshAll() {
    if (!session?.accessToken || privileged) return;
    setError('');
    try {
      const [customer, paymentPage, transferPage, notificationPage] = await Promise.all([
        getCustomer(session.accessToken, session.customerId),
        listPayments(session.accessToken),
        listTransfers(session.accessToken),
        listNotifications(session.accessToken)
      ]);
      setProfile(customer);
      setPayments(paymentPage?.content || []);
      setTransfers(transferPage?.content || []);
      setNotifications(notificationPage?.content || []);
    } catch (e) {
      setError(`${e.message}${e.correlationId ? ` (correlation ${e.correlationId})` : ''}`);
    }
  }

  useEffect(() => {
    refreshAll();
  }, [session, privileged]);

  async function handlePayment(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const result = await createPayment(session.accessToken, {
        idempotencyKey: crypto.randomUUID(),
        accountNumber: profile?.accountNumber || form.get('accountNumber'),
        merchant: form.get('merchant'),
        amount: Number(form.get('amount'))
      });
      setSuccess(`Payment ${String(result.paymentId).slice(0, 8)} is ${result.status}`);
      event.currentTarget.reset();
      await refreshAll();
    } catch (e) {
      setError(formatError(e));
    } finally {
      setLoading(false);
    }
  }

  async function handleTransfer(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const result = await createTransfer(session.accessToken, {
        idempotencyKey: crypto.randomUUID(),
        senderAccount: profile?.accountNumber,
        receiverAccount: form.get('receiverAccount'),
        amount: Number(form.get('amount')),
        currency: form.get('currency') || 'INR'
      });
      setSuccess(`Transfer ${String(result.transferId).slice(0, 8)} is ${result.status}`);
      event.currentTarget.reset();
      await refreshAll();
    } catch (e) {
      setError(formatError(e));
    } finally {
      setLoading(false);
    }
  }

  async function handleRefund(paymentId) {
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const result = await createRefund(session.accessToken, paymentId);
      setSuccess(`Refund ${String(result.refundId).slice(0, 8)} is ${result.status}`);
      await refreshAll();
    } catch (e) {
      setError(formatError(e));
    } finally {
      setLoading(false);
    }
  }

  function signOut() {
    setSession(null);
    setProfile(null);
    setPayments([]);
    setTransfers([]);
    setNotifications([]);
    setError('');
    setSuccess('');
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="card login">
          <p className="eyebrow">Enterprise observability lab</p>
          <h1>Payment Platform</h1>
          <p>Sign in to run real microservice flows before and after OpenTelemetry instrumentation.</p>
          <form onSubmit={handleLogin}>
            <label>Username<input name="username" defaultValue="demo" required /></label>
            <label>Password<input name="password" type="password" defaultValue="demo123" required /></label>
            <button disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button>
          </form>
          <div className="demoCredentials"><b>Demo users</b><span>demo / demo123 — CUSTOMER</span><span>receiver / receiver123 — CUSTOMER</span><span>support / support123 — SUPPORT</span><span>admin / admin123 — ADMIN</span></div>
          {error && <p className="error">{error}</p>}
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <header>
        <div>
          <p className="eyebrow">Full-stack payment platform</p>
          <h1>Payment Platform</h1>
          <p>{privileged ? session.customerId : (profile?.name || session.customerId)} · {roles.join(', ') || 'USER'}</p>
        </div>
        <div className="headerActions">{!privileged && <button className="secondary" onClick={refreshAll}>Refresh</button>}<button className="secondary" onClick={signOut}>Sign out</button></div>
      </header>

      {privileged ? (
        <OperationsPanel token={session.accessToken} roles={roles} />
      ) : (
        <>
          <nav className="tabs">
            {['overview', 'payment', 'transfer', 'history', 'notifications'].map(name => (
              <button key={name} className={tab === name ? 'active' : 'secondary'} onClick={() => setTab(name)}>{name}</button>
            ))}
          </nav>

          {error && <p className="error">{error}</p>}
          {success && <p className="success">{success}</p>}

          {tab === 'overview' && <section className="dashboardGrid">
            <article className="card metric"><span>Customer</span><strong>{profile?.name || 'Loading…'}</strong><small>{profile?.email}</small></article>
            <article className="card metric"><span>Linked account</span><strong>{profile?.accountNumber || '—'}</strong><small>{profile?.active ? 'Active' : 'Inactive'}</small></article>
            <article className="card metric"><span>Payments</span><strong>{payments.length}</strong><small>recent records</small></article>
            <article className="card metric"><span>Transfers</span><strong>{transfers.length}</strong><small>recent records</small></article>
            <article className="card architecture wide"><h2>Request flow</h2><code>React → API Gateway → JWT authorization → Payment Service → Gateway Service → Mock Bank → PostgreSQL<br/>Payment/Transfer/Refund → Outbox → RabbitMQ → Notification Service<br/>Later: OTel Java Agent → Collector → traces / metrics / correlated logs</code></article>
          </section>}

          {tab === 'payment' && <section className="twoCol">
            <div className="card"><h2>Merchant payment</h2><p>The source account comes from the authenticated customer profile. Risk limits are checked before money movement.</p><form onSubmit={handlePayment}><label>Source account<input name="accountNumber" value={profile?.accountNumber || ''} readOnly /></label><label>Merchant<input name="merchant" placeholder="Demo Store" required /></label><label>Amount<input name="amount" type="number" min="0.01" step="0.01" required /></label><button disabled={loading}>Submit payment</button></form></div>
            <HistoryTable title="Recent payments" rows={payments} type="payment" onRefund={handleRefund} loading={loading} />
          </section>}

          {tab === 'transfer' && <section className="twoCol">
            <div className="card"><h2>Send money</h2><p>Use <b>ACC2001</b> to transfer to the seeded receiver customer.</p><form onSubmit={handleTransfer}><label>From<input value={profile?.accountNumber || ''} readOnly /></label><label>Receiver account<input name="receiverAccount" defaultValue="ACC2001" required /></label><label>Amount<input name="amount" type="number" min="0.01" step="0.01" required /></label><label>Currency<input name="currency" defaultValue="INR" maxLength="3" required /></label><button disabled={loading}>Send transfer</button></form></div>
            <HistoryTable title="Recent transfers" rows={transfers} type="transfer" />
          </section>}

          {tab === 'history' && <section className="stack"><HistoryTable title="Payment history" rows={payments} type="payment" onRefund={handleRefund} loading={loading} /><HistoryTable title="Transfer history" rows={transfers} type="transfer" /></section>}

          {tab === 'notifications' && <section className="card"><div className="row"><div><h2>Notification events</h2><p>RabbitMQ consumer results from payment, transfer and refund events.</p></div><button className="secondary" onClick={refreshAll}>Refresh</button></div><div className="tableWrap"><table><thead><tr><th>ID</th><th>Event</th><th>Channel</th><th>Status</th></tr></thead><tbody>{notifications.map(n => <tr key={n.id}><td>{short(n.id)}</td><td>{n.eventType || '—'}</td><td>{n.channel || 'LOG'}</td><td><span className="badge">{n.status}</span></td></tr>)}{notifications.length === 0 && <tr><td colSpan="4">No notifications yet.</td></tr>}</tbody></table></div></section>}
        </>
      )}
    </main>
  );
}

function HistoryTable({ title, rows, type, onRefund, loading }) {
  return <div className="card"><h2>{title}</h2><div className="tableWrap"><table><thead><tr><th>ID</th><th>Destination</th><th>Amount</th><th>Status</th>{type === 'payment' && <th>Action</th>}</tr></thead><tbody>{rows.map(row => <tr key={row.paymentId || row.transferId}><td>{short(row.paymentId || row.transferId)}</td><td>{type === 'payment' ? row.merchant : row.receiverAccount}</td><td>{row.amount} {type === 'transfer' ? row.currency : ''}</td><td><span className="badge">{row.status}</span></td>{type === 'payment' && <td>{row.status === 'COMPLETED' && onRefund ? <button className="secondary compact" disabled={loading} onClick={() => onRefund(row.paymentId)}>Refund</button> : '—'}</td>}</tr>)}{rows.length === 0 && <tr><td colSpan={type === 'payment' ? '5' : '4'}>No records yet.</td></tr>}</tbody></table></div></div>;
}

function formatError(e) {
  return `${e.code ? `${e.code}: ` : ''}${e.message}${e.correlationId ? ` (correlation ${e.correlationId})` : ''}`;
}

function short(value) {
  return value ? String(value).slice(0, 8) : '—';
}
