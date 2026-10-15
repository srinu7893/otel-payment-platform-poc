import { useState } from 'react';
import { login } from './api';
import { formatApiError } from './errors';
import OperationsPanel from './OperationsPanel';
import WalletDashboard from './WalletDashboard';

export default function App() {
  const [session, setSession] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  async function handleLogin(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true); setError('');
    try { setSession(await login(form.get('username'), form.get('password'))); }
    catch (e) { setError(formatApiError(e)); }
    finally { setLoading(false); }
  }
  if (!session) return <main className="shell"><section className="card login">
    <p className="eyebrow">Payment sandbox</p><h1>Payment Platform</h1>
    <p>Check your balance, send money, pay merchants, and track every transaction with demo funds.</p>
    <form onSubmit={handleLogin}><label>Username<input name="username" autoComplete="username" defaultValue="demo" required /></label><label>Password<input name="password" type="password" autoComplete="current-password" defaultValue="demo123" required /></label><button disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button></form>
    <div className="demoCredentials"><b>Demo accounts · test funds only</b><span>demo / demo123 — ACC1001</span><span>receiver / receiver123 — ACC2001</span><span>support / support123 — SUPPORT</span><span>admin / admin123 — ADMIN</span></div>
    {error && <p role="alert" className="error">{error}</p>}
  </section></main>;
  const roles = session.roles || [];
  const privileged = roles.some(role => role === 'SUPPORT' || role === 'ADMIN');
  return <main className="shell"><header><div><p className="eyebrow">Payment Platform</p><h1>{privileged ? 'Operations' : 'Your wallet'}</h1></div><button className="secondary" onClick={() => { setSession(null); setError(''); }}>Sign out</button></header>{privileged ? <OperationsPanel token={session.accessToken} roles={roles} /> : <WalletDashboard key={session.customerId} session={session} />}</main>;
}
