import { Component } from 'react';

export default class AppErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() {
    // Count only. Never pass the exception, component stack or user data to RUM.
    window.dispatchEvent(new Event('error'));
  }
  render() {
    if (!this.state.failed) return this.props.children;
    return <main className="shell"><section className="card login" role="alert">
      <p className="eyebrow">Payment Platform</p><h1>We couldn’t display this page</h1>
      <p>Your transaction outcome may still be pending. Sign in again and check history before starting another payment.</p>
      <button onClick={() => window.location.reload()}>Reload and sign in</button>
    </section></main>;
  }
}
