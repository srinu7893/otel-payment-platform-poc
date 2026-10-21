import { useState } from 'react';
import { login, register } from './api';
import { formatApiError } from './errors';
import OperationsPanel from './OperationsPanel';
import WalletDashboard from './WalletDashboard';

export default function App() {
  const [session, setSession] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [registering, setRegistering] = useState(false);
  async function handleLogin(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true); setError('');
    try {
      if (registering) {
        if (form.get('password') !== form.get('confirmPassword')) throw new Error('Passwords do not match.');
        const result = await register({ username: String(form.get('username')).trim(), password: form.get('password'), name: String(form.get('name')).trim(), email: String(form.get('email')).trim() });
        setSession({ ...result.session, registeredAccount: result.accountNumber }); setRegistering(false);
      } else setSession(await login(form.get('username'), form.get('password')));
    }
    catch (e) { setError(formatApiError(e)); }
    finally { setLoading(false); }
  }
  if (!session) return <main className="shell"><section className="card login">
    <p className="eyebrow">Payment sandbox</p><h1>{registering ? 'Create your account' : 'Payment Platform'}</h1>
    <p>{registering ? 'Choose your credentials. We create a linked demo bank account with a unique account number and zero opening balance. Add test funds after registration.' : 'Check your balance, send money, pay merchants, and track every transaction with demo funds.'}</p>
    <form key={registering ? 'register' : 'login'} onSubmit={handleLogin}>
      {registering && <><label>Full name<input name="name" autoComplete="name" maxLength={120} required /></label><label>Email<input name="email" type="email" autoComplete="email" maxLength={160} required /></label></>}
      <label>Username<input name="username" autoComplete="username" defaultValue={registering ? '' : 'demo'} pattern={registering ? '[A-Za-z0-9][A-Za-z0-9._-]{2,39}' : undefined} maxLength={40} required /></label>
      {registering && <small>3–40 letters, numbers, dots, underscores or hyphens. Starts with a letter or number.</small>}
      <label>Password<input name="password" type="password" autoComplete={registering ? 'new-password' : 'current-password'} defaultValue={registering ? '' : 'demo123'} minLength={registering ? 12 : undefined} maxLength={72} required /></label>
      {registering && <><small>Use a unique password with at least 12 characters (maximum 72 UTF-8 bytes).</small><label>Confirm password<input name="confirmPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} required /></label><p>Sandbox only: this creates a simulated bank account, not a real bank or UPI account.</p></>}
      <button disabled={loading}>{loading ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
      <button type="button" className="secondary" disabled={loading} onClick={() => { setRegistering(r => !r); setError(''); }}>{registering ? 'Back to sign in' : 'New user? Create account'}</button>
    </form>
    {!registering && <div className="demoCredentials"><b>Demo accounts · test funds only</b><span>demo / demo123 — ACC1001</span><span>receiver / receiver123 — ACC2001</span><span>support / support123 — SUPPORT</span><span>admin / admin123 — ADMIN</span></div>}
    {error && <p role="alert" className="error">{error}</p>}
    <p><a href="/api-docs/" target="_blank" rel="noreferrer">API explorer and Swagger documentation</a></p>
  </section></main>;
  const roles = session.roles || [];
  const privileged = roles.some(role => role === 'SUPPORT' || role === 'ADMIN');
  return <main className="shell"><header><div><p className="eyebrow">Payment Platform</p><h1>{privileged ? 'Operations' : 'Your wallet'}</h1></div><div className="headerActions"><a href="/api-docs/" target="_blank" rel="noreferrer">API explorer</a><button className="secondary" onClick={() => { setSession(null); setError(''); }}>Sign out</button></div></header>{privileged ? <OperationsPanel token={session.accessToken} roles={roles} /> : <WalletDashboard key={session.customerId} session={session} />}</main>;
}
