import { useState, type FormEvent } from "react";
import { api } from "../api";
import { errorText } from "../shared";

/**
 * The change-password form. Used full-screen after signing in with a one-time password ({@code forced}), and on the
 * Account page.
 */
export function ChangePasswordForm({ onDone, forced = false }: { onDone: () => void; forced?: boolean }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setDone(false);
    if (next !== confirm) {
      setError("The two new passwords do not match");
      return;
    }
    setBusy(true);
    try {
      await api.changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      setDone(true);
      onDone();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel on-light login-panel" onSubmit={submit}>
      <h2 className="login-heading">{forced ? "Your password" : "Change password"}</h2>
      <label>
        {forced ? "One-time password" : "Current password"}
        <input
          type="password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          autoComplete="current-password"
          required
          autoFocus={forced}
        />
      </label>
      <label>
        New password
        <input
          type="password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          autoComplete="new-password"
          required
        />
      </label>
      <label>
        New password again
        <input
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          required
        />
      </label>
      <p className="login-help">At least 10 characters, with letters and numbers.</p>
      <button className="btn dark" type="submit" disabled={busy}>
        {busy ? "Saving…" : "Save password"}
      </button>
      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}
      {done && !forced && <p className="message success">Your password has been changed.</p>}
    </form>
  );
}

/** Full-screen: shown after signing in with a one-time password, before anything else. */
export function ForcedPasswordChange({ name, onDone, onSignOut }: { name: string; onDone: () => void; onSignOut: () => void }) {
  return (
    <main className="login section">
      <div className="glow" style={{ width: 420, height: 420, left: "-140px", top: "10%" }} />
      <div className="wrap login-grid">
        <div>
          <p className="kicker">Welcome, {name}</p>
          <h1 className="login-title">
            Choose your
            <span>password.</span>
          </h1>
          <p className="login-lede">
            You signed in with a one-time password from your administrator. Choose your own to continue; nobody else
            will know it.
          </p>
          <button className="btn ghost small" type="button" onClick={onSignOut} style={{ marginTop: 24 }}>
            Sign out
          </button>
        </div>
        <ChangePasswordForm forced onDone={onDone} />
      </div>
    </main>
  );
}
