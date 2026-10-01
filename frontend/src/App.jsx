import { useEffect, useState } from 'react';
import { createPayment, listPayments, login } from './api';

export default function App() {
  const [session, setSession] = useState(null);
  const [payments, setPayments] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleLogin(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError('');
    try {
      const result = await login(form.get('username'), form.get('password'));
      setSession(result);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function refreshPayments() {
    if (!session?.accessToken) return;
    setError('');
    try {
      const page = await listPayments(session.accessToken);
      setPayments(page.content || []);
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => { refreshPayments(); }, [session]);

  async function handlePayment(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError('');
    try {
      await createPayment(session.accessToken, {
        customerId: session.customerId,
        accountNumber: form.get('accountNumber'),
        merchantName: form.get('merchantName'),
        amount: Number(form.get('amount')),
        idempotencyKey: crypto.randomUUID()
      });
      event.currentTarget.reset();
      await refreshPayments();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  if (!session) {
    return <main className="shell"><section className="card login"><h1>Payment Platform</h1><p>Enterprise microservices POC console</p><form onSubmit={handleLogin}><label>Username<input name="username" defaultValue="demo" required /></label><label>Password<input name="password" type="password" defaultValue="demo123" required /></label><button disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button></form>{error && <p className="error">{error}</p>}</section></main>;
  }

  return <main className="shell"><header><div><h1>Payment Platform</h1><p>Customer: {session.customerId}</p></div><button className="secondary" onClick={() => setSession(null)}>Sign out</button></header><section className="grid"><div className="card"><h2>Send payment</h2><form onSubmit={handlePayment}><label>Account<input name="accountNumber" defaultValue="ACC1001" required /></label><label>Merchant / receiver<input name="merchantName" placeholder="Demo Store" required /></label><label>Amount<input name="amount" type="number" min="1" step="0.01" required /></label><button disabled={loading}>Submit payment</button></form></div><div className="card"><div className="row"><h2>Recent payments</h2><button className="secondary" onClick={refreshPayments}>Refresh</button></div><div className="tableWrap"><table><thead><tr><th>ID</th><th>Merchant</th><th>Amount</th><th>Status</th></tr></thead><tbody>{payments.map(p => <tr key={p.paymentId || p.id}><td>{String(p.paymentId || p.id || '').slice(0,8)}</td><td>{p.merchantName}</td><td>{p.amount}</td><td><span className="badge">{p.status}</span></td></tr>)}{payments.length === 0 && <tr><td colSpan="4">No payments yet.</td></tr>}</tbody></table></div></div></section>{error && <p className="error">{error}</p>}<section className="card architecture"><h2>POC flow</h2><code>Frontend → Auth/API Gateway → Payment → Gateway → Mock Bank → PostgreSQL<br/>Payment → RabbitMQ → Notification Service<br/>Later: OTel Agent → Collector → traces / metrics / logs</code></section></main>;
}
