import { useState, type FormEvent } from "react";
import { api, type User } from "../api";
import { Drop, errorText } from "../shared";

/** First run only: creates the first administrator, then signs them in. */
export function Setup({ onDone }: { onDone: (user: User) => void }) {
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("The two passwords do not match");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const account = await api.setup({ username, displayName, password });
      onDone(await api.login(account.username, password));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login section">
      <div className="glow" style={{ width: 420, height: 420, left: "-140px", top: "10%" }} />
      <div className="wrap login-grid">
        <div>
          <p className="kicker">
            <Drop /> RaktSetu
          </p>
          <h1 className="login-title">
            Let's set
            <span>you up.</span>
          </h1>
          <p className="login-lede">
            Create the first administrator account. You will then add your blood centres and hospitals, and create
            accounts for their staff.
          </p>
        </div>
        <form className="panel on-light login-panel" onSubmit={submit}>
          <h2 className="login-heading">Administrator</h2>
          <label>
            Your name
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required autoFocus />
          </label>
          <label>
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value.toLowerCase())}
              autoComplete="username"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password"
              required
            />
          </label>
          <label>
            Password again
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
            {busy ? "Setting up…" : "Create administrator"}
          </button>
          {error && (
            <p className="message error" role="alert">
              {error}
            </p>
          )}
        </form>
      </div>
    </main>
  );
}
