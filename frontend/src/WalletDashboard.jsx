import { useEffect, useRef, useState } from 'react';
import * as api from './api';
import { formatApiError } from './errors';

const money = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);
const date = value => value ? new Date(value).toLocaleString() : '—';
const pending = status => ['PENDING', 'PROCESSING', 'RECONCILIATION_REQUIRED'].includes(status);

export default function WalletDashboard({ session }) {
  const token = session.accessToken;
  const [tab, setTab] = useState('overview');
  const [profile, setProfile] = useState(null);
  const [wallet, setWallet] = useState(null);
  const [activity, setActivity] = useState([]);
  const [payments, setPayments] = useState([]);
  const [transfers, setTransfers] = useState([]);
  const [requests, setRequests] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(session.registeredAccount ? `Account created: ${session.registeredAccount}. Your opening balance is INR 0. Add demo money to get started.` : '');
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [lastChecked, setLastChecked] = useState(null);
  const refreshSequence = useRef(0);
  const [confirmation, setConfirmation] = useState(null);
  const [detail, setDetail] = useState(null);
  // Retain the key on network failure: retrying unchanged details must not move money twice.
  const keys = useRef(new Map());
  function idempotency(kind, payload) {
    const signature = `${kind}:${JSON.stringify(payload)}`;
    if (!keys.current.has(signature)) keys.current.set(signature, crypto.randomUUID());
    return keys.current.get(signature);
  }
  async function refresh(nextPage = page) {
    const sequence = ++refreshSequence.current;
    setRefreshing(true);
    const jobs = [
      ['Profile', () => api.getCustomer(token, session.customerId), setProfile],
      ['Balance', () => api.getWallet(token), setWallet],
      ['Activity', () => api.getActivity(token, nextPage), setActivity],
      ['Payments', () => api.listPayments(token, { page: nextPage }), r => setPayments(r.content || [])],
      ['Transfers', () => api.listTransfers(token, { page: nextPage }), r => setTransfers(r.content || [])],
      ['Requests', () => api.listMoneyRequests(token, nextPage), r => setRequests(r.content || [])],
      ['Notifications', () => api.listNotifications(token, { page: nextPage }), r => setNotifications(r.content || [])]
    ];
    const results = await Promise.allSettled(jobs.map(([, load]) => load()));
    if (sequence !== refreshSequence.current) return;
    const failures = [];
    results.forEach((result, i) => {
      if (result.status === 'fulfilled') jobs[i][2](result.value);
      else {
        if (jobs[i][0] === 'Balance') setWallet(null);
        failures.push(`${jobs[i][0]}: ${formatApiError(result.reason)}`);
      }
    });
    setError(failures.join('\n'));
    setLoaded(true); setLastChecked(new Date());
    setRefreshing(false);
  }
  useEffect(() => { refresh(page); return () => { refreshSequence.current++; }; }, [token, page]);

  function openTab(id) {
    setTab(id); setPage(0); setConfirmation(null); setNotice(''); setError('');
  }
  async function perform(action, message, completedKey) {
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await action();
      const status = result?.status;
      setNotice(status ? `${message}: ${status}. ${pending(status) ? 'The outcome is still being checked. Refresh history; do not submit a new transaction.' : ''}` : message);
      setConfirmation(null);
      // A different successful operation must not erase an uncertain payment's retry key.
      if (completedKey && !pending(status)) {
        for (const [signature, key] of keys.current) {
          if (key === completedKey) keys.current.delete(signature);
        }
      }
      await refresh();
      return true;
    } catch (e) { setError(formatApiError(e)); return false; }
    finally { setBusy(false); }
  }
  async function prepare(event, kind) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const amount = form.get('amount');
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) { setError('Enter a positive amount with at most two decimal places.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      if (kind === 'transfer' || kind === 'request') {
        const account = String(form.get('account')).trim().toUpperCase();
        if (account === profile?.accountNumber) throw new Error('Choose a different account.');
        const recipient = await api.lookupRecipient(token, account);
        const payload = kind === 'transfer'
          ? { senderAccount: profile.accountNumber, receiverAccount: recipient.accountNumber, amount: Number(amount), currency: 'INR' }
          : { payerAccount: recipient.accountNumber, amount: Number(amount), note: String(form.get('note') || '').trim() };
        setConfirmation({ kind, payload, name: recipient.customerName, account: recipient.accountNumber });
      } else {
        const payload = kind === 'payment'
          ? { accountNumber: profile.accountNumber, merchant: String(form.get('merchant')).trim(), amount: Number(amount) }
          : { amount: Number(amount) };
        if (kind === 'payment' && !payload.merchant) throw new Error('Enter a merchant name.');
        setConfirmation({ kind, payload, name: kind === 'payment' ? payload.merchant : 'Your demo wallet', account: profile?.accountNumber });
      }
    } catch (e) { setError(formatApiError(e)); }
    finally { setBusy(false); }
  }
  function confirm() {
    const { kind, payload } = confirmation;
    const body = { ...payload, idempotencyKey: idempotency(kind, payload) };
    const actions = { payment: api.createPayment, transfer: api.createTransfer, deposit: api.depositMoney, request: api.requestMoney };
    perform(() => actions[kind](token, body), { payment: 'Payment submitted', transfer: 'Transfer submitted', deposit: 'Demo deposit', request: 'Money request sent' }[kind], body.idempotencyKey);
  }
  async function showDetail(type, id) {
    setBusy(true); setError('');
    try {
      const record = await (type === 'payment' ? api.getPayment(token, id) : api.getTransfer(token, id));
      let refund = null;
      if (type === 'payment' && ['COMPLETED', 'REFUNDED'].includes(record.status)) {
        try { refund = await api.getRefund(token, id); } catch (e) { if (e.status !== 404) throw e; }
      }
      setDetail({ type, record, refund });
    } catch (e) { setError(formatApiError(e)); }
    finally { setBusy(false); }
  }
  const ready = !!profile?.active && !!wallet?.active && !busy && !refreshing;
  const tabs = [['overview', 'My account'], ['deposit', 'Add demo money'], ['payment', 'Pay merchant'], ['transfer', 'Send money'], ['request', 'Request money'], ['history', 'History'], ['notifications', 'Notifications'], ['help', 'How to use']];
  return <section className="stack wallet" aria-busy={busy || refreshing}>
    <div className="demoBanner">Demo payment sandbox · INR test funds only · No real bank account or card is charged.</div>
    <div className="row"><div><h2>Hello, {profile?.name || session.customerId}</h2><p>Account {profile?.accountNumber || 'loading…'} · {profile?.email}</p></div><button className="secondary" disabled={refreshing || busy} onClick={() => refresh()}>{refreshing ? 'Refreshing…' : 'Refresh account'}</button></div>
    <nav className="tabs" aria-label="Payment features">{tabs.map(([id, label]) => <button key={id} aria-current={tab === id ? 'page' : undefined} disabled={busy} className={tab === id ? 'active' : 'secondary'} onClick={() => openTab(id)}>{label}</button>)}</nav>
    <div className="accountStatus" role="status">{refreshing ? 'Checking account services… This can take a moment after idle.' : lastChecked ? `Last account check: ${lastChecked.toLocaleTimeString()}` : 'Loading your account…'}{loaded && !wallet && !refreshing && ' Account data is unavailable. Refresh before making a transaction.'}</div>
    {error && <p role="alert" className="error">{error}</p>}
    {notice && <p role="status" className={/FAILED|DECLINED/.test(notice) ? 'error' : 'success'}>{notice}</p>}
    {confirmation && <section className="card confirmation" aria-label="Review transaction"><h2>Review {confirmation.kind}</h2><p>{money(confirmation.payload.amount)} {confirmation.kind === 'request' ? 'requested from' : 'to'} <b>{confirmation.name}</b></p><p>Account: {confirmation.account}</p>{confirmation.payload.note && <p>Note: {confirmation.payload.note}</p>}<p>{confirmation.kind === 'request' ? 'This sends a request only. The payer must approve it.' : confirmation.kind === 'deposit' ? 'This adds simulated funds to your demo account.' : 'Confirm the recipient and amount before continuing.'}</p><div className="headerActions"><button disabled={busy} onClick={confirm}>{busy ? 'Processing…' : 'Confirm transaction'}</button><button className="secondary" disabled={busy} onClick={() => setConfirmation(null)}>Go back</button></div></section>}
    {tab === 'overview' && <>
      <section className="dashboardGrid"><article className="card metric balanceCard"><span>Available balance</span><strong data-testid="balance">{wallet ? money(wallet.balance) : loaded ? 'Unavailable' : 'Loading…'}</strong><small>{wallet?.active ? 'INR · Account active' : 'Refresh to check account status'}</small></article><article className="card metric"><span>Your account number</span><strong>{profile?.accountNumber || '—'}</strong><small>Share this to receive a transfer</small></article><article className="card metric"><span>Money requests</span><strong>{requests.filter(r => r.status === 'OPEN' && r.payerAccount === profile?.accountNumber).length}</strong><small>Awaiting your response on this page</small></article><article className="card metric"><span>Quick actions</span><button disabled={busy} onClick={() => openTab('deposit')}>Add demo funds</button><button className="secondary" disabled={busy} onClick={() => openTab('transfer')}>Send money</button></article></section>
      <Activity rows={activity} />
      <section className="card"><h2>Try a complete payment journey</h2><ol><li>Add INR 500 in <b>Add demo money</b>.</li><li>Send INR 25 to <b>ACC2001</b>, the receiver account.</li><li>Open History for the result and transaction details.</li><li>Sign out and sign in as <b>receiver / receiver123</b> to see the credit.</li></ol></section>
    </>}
    {['deposit', 'payment', 'transfer', 'request'].includes(tab) && <section className="twoCol">
      <article className="card"><h2>{tabs.find(([id]) => id === tab)[1]}</h2><p>Available: {wallet ? money(wallet.balance) : 'Unavailable'} · Your account: {profile?.accountNumber}</p>
        {tab === 'deposit' && <p>Add test funds instantly. Maximum INR 100,000 per deposit. No external funding source is connected.</p>}
        {tab === 'transfer' && <p>Enter an existing recipient account, then check their name before confirming. Try ACC2001 from demo or ACC1001 from receiver.</p>}
        {tab === 'request' && <p>Ask another account to send you money. They will see the request after signing in and can pay or decline.</p>}
        <form key={tab} onSubmit={event => prepare(event, tab)}>
          {['transfer', 'request'].includes(tab) && <label>{tab === 'request' ? 'Payer account' : 'Recipient account'}<input name="account" placeholder={profile?.accountNumber === 'ACC2001' ? 'ACC1001' : 'ACC2001'} pattern="[A-Za-z0-9-]{3,64}" maxLength={64} required /></label>}
          {tab === 'payment' && <label>Merchant name<input name="merchant" placeholder="Demo Store" maxLength={120} required /></label>}
          <label>Amount (INR)<input name="amount" type="number" min="0.01" max="100000" step="0.01" inputMode="decimal" required /></label>
          {tab === 'request' && <label>Note (optional)<input name="note" maxLength={200} placeholder="Dinner share" /></label>}
          <button disabled={!ready || !!confirmation}>Review {tab === 'request' ? 'request' : tab}</button>
        </form>
      </article><Activity rows={activity} />
    </section>}
    {tab === 'request' && <section className="card"><h2>Sent and received requests</h2><p>Only the requested payer can approve. Sending a request never debits an account.</p><div className="tableWrap"><table><thead><tr><th>From / To</th><th>Note</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead><tbody>{requests.map(r => <tr key={r.id}><td>{r.requesterAccount === profile?.accountNumber ? `To ${r.payerAccount}` : `From ${r.requesterName} (${r.requesterAccount})`}</td><td>{r.note || '—'}</td><td>{money(r.amount)}</td><td>{r.status}</td><td>{r.status === 'OPEN' && (r.payerAccount === profile?.accountNumber ? <><button disabled={busy} onClick={() => { if (window.confirm(`Pay ${money(r.amount)} to ${r.requesterName} (${r.requesterAccount})?`)) perform(() => api.respondToRequest(token, r.id, 'pay'), 'Request payment'); }}>Pay request</button> <button className="secondary" disabled={busy} onClick={() => perform(() => api.respondToRequest(token, r.id, 'decline'), 'Request declined')}>Decline</button></> : <button className="secondary" disabled={busy} onClick={() => perform(() => api.respondToRequest(token, r.id, 'cancel'), 'Request cancelled')}>Cancel request</button>)}{r.status === 'PROCESSING' && r.payerAccount === profile?.accountNumber && <button disabled={busy} onClick={() => perform(() => api.respondToRequest(token, r.id, 'pay'), 'Request status')}>Check / retry safely</button>}</td></tr>)}{!requests.length && <tr><td colSpan="5">No requests on this page.</td></tr>}</tbody></table></div><Paging page={page} setPage={setPage} busy={busy || refreshing} hasNext={requests.length === 20} /></section>}
    {tab === 'history' && <>
      <Activity rows={activity} /><Paging page={page} setPage={setPage} busy={busy || refreshing} hasNext={activity.length === 20 || payments.length === 20 || transfers.length === 20} />
      <section className="card"><h2>Merchant payments</h2><p>Open Details to view the complete transaction ID, status, timestamps, and refund.</p><TransactionTable rows={payments} type="payment" detail={showDetail} busy={busy} /></section>
      <section className="card"><h2>Outgoing transfers</h2><p>Incoming transfers appear as credits in Account activity above.</p><TransactionTable rows={transfers} type="transfer" detail={showDetail} busy={busy} /></section>
    </>}
    {tab === 'notifications' && <section className="card"><h2>Notifications</h2><p>Events arrive asynchronously after payments, transfers and refunds.</p><div className="tableWrap"><table><thead><tr><th>Event</th><th>Channel</th><th>Status</th></tr></thead><tbody>{notifications.map(n => <tr key={n.id}><td>{n.eventType}</td><td>{n.channel}</td><td>{n.status}</td></tr>)}{!notifications.length && <tr><td colSpan="3">No events on this page. Refresh after a transaction.</td></tr>}</tbody></table></div><Paging page={page} setPage={setPage} busy={busy || refreshing} hasNext={notifications.length === 20} /></section>}
    {tab === 'help' && <section className="card help"><h2>How to use your demo account</h2><dl><dt>Register and open an account</dt><dd>Sign out → New user? Create account → enter your name, email and a unique username/password. A new mock-bank account number is created with zero opening balance. You are signed in when setup completes. If setup is interrupted, retry the same registration details.</dd><dt>Check balance</dt><dd>My account shows the bank balance. Refresh account after a transaction.</dd><dt>Deposit</dt><dd>Add demo money → enter amount → Review deposit → Confirm transaction.</dd><dt>Pay a merchant</dt><dd>Pay merchant → enter a merchant name and amount → review and confirm. A completed payment debits your balance.</dd><dt>Send to an account</dt><dd>Send money → enter an existing account number → enter amount → check the returned recipient name → confirm. Unknown or inactive accounts cannot receive funds. Share your own account number to receive funds.</dd><dt>Receive money / request money</dt><dd>Request money → enter payer account and amount → review and confirm. Sign in as that payer to pay or decline. Requests themselves do not move funds.</dd><dt>History and refunds</dt><dd>History → Details shows the full payment or transfer. Completed merchant payments can be refunded once. Transfers cannot be reversed in this demo.</dd><dt>Pending outcome</dt><dd>If status is PROCESSING or RECONCILIATION_REQUIRED, refresh history. Do not create another transaction: the existing one may have moved funds and is being reconciled.</dd><dt>Demo users</dt><dd>demo / demo123 owns ACC1001. receiver / receiver123 owns ACC2001. Use two browser windows or sign out between accounts. Registration also supports new sandbox users. No real external bank or UPI account is created.</dd></dl></section>}
    {detail && <div className="modalBackdrop"><section className="card detail" role="dialog" aria-modal="true" aria-label="Transaction details"><h2>Transaction details</h2><dl>{Object.entries(detail.record).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value ?? '—')}</dd></div>)}</dl>{detail.refund && <p>Refund: {detail.refund.status} · {detail.refund.refundId}</p>}{detail.type === 'payment' && detail.record.status === 'COMPLETED' && !detail.refund && <button disabled={busy} onClick={async () => { if (!window.confirm('Refund this merchant payment to your account?')) return; const id = detail.record.paymentId; const key = idempotency('refund', { id }); if (await perform(() => api.createRefund(token, id, key), 'Refund submitted', key)) setDetail(null); }}>Refund payment</button>}<button className="secondary" disabled={busy} onClick={() => setDetail(null)}>Close details</button></section></div>}
  </section>;
}

function Activity({ rows }) {
  return <section className="card"><h2>Account activity</h2><p>Bank ledger records include deposits, payments, refunds, and incoming/outgoing transfers. Failed entries do not affect the balance.</p><div className="tableWrap"><table><thead><tr><th>Time</th><th>Type</th><th>Direction</th><th>Amount</th><th>Status</th></tr></thead><tbody>{rows.map(row => <tr key={`${row.type}-${row.id}`}><td>{date(row.createdAt)}</td><td>{row.type}</td><td>{row.direction}</td><td>{money(row.amount)}</td><td><span className="badge">{row.status}</span></td></tr>)}{!rows.length && <tr><td colSpan="5">No activity on this page.</td></tr>}</tbody></table></div></section>;
}
function TransactionTable({ rows, type, detail, busy }) {
  return <div className="tableWrap"><table><thead><tr><th>Time</th><th>Destination</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead><tbody>{rows.map(row => <tr key={row.paymentId || row.transferId}><td>{date(row.createdAt)}</td><td>{row.merchant || row.receiverAccount}</td><td>{money(row.amount)}</td><td>{row.status}</td><td><button className="secondary compact" disabled={busy} onClick={() => detail(type, row.paymentId || row.transferId)}>Details</button></td></tr>)}{!rows.length && <tr><td colSpan="5">No transactions on this page.</td></tr>}</tbody></table></div>;
}
function Paging({ page, setPage, busy, hasNext }) { return <div className="headerActions"><button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(p => p - 1)}>Previous page</button><span>Page {page + 1}</span><button className="secondary" disabled={!hasNext || busy} onClick={() => setPage(p => p + 1)}>Next page</button></div>; }
