import { useState, type FormEvent } from "react";
import { api, type User } from "./api";
import { Drop, errorText } from "./shared";
import { RequestAccount } from "./pages/RequestAccount";

/**
 * The sign-in page. Accounts are created by a network administrator on the People page, or requested here and
 * approved by one.
 */
export function Login({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const [requesting, setRequesting] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onSignedIn(await api.login(username, password));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login section">
      <div className="glow" style={{ width: 420, height: 420, left: "-140px", top: "10%" }} />
      <div
        className="glow"
        style={{
          width: 220,
          height: 220,
          right: "8%",
          bottom: "12%",
          opacity: 0.7,
        }}
      />
      <div className="wrap login-grid">
        <div>
          <p className="kicker">
            <Drop /> RaktSetu
          </p>
          <h1 className="login-title">
            Every unit
            <span>counts.</span>
          </h1>
          <p className="login-lede">
            Expiry-aware blood redistribution for blood centres, hospitals and the network team.
          </p>
        </div>
        {requesting ? (
          <RequestAccount onBack={() => setRequesting(false)} />
        ) : (
          <form className="panel on-light login-panel" onSubmit={submit}>
            <h2 className="login-heading">Sign in</h2>
            <label>
              Username
              <input
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoFocus
              />
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>
            <button className="btn dark" type="submit" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
            {error && (
              <p className="message error" role="alert">
                {error}
              </p>
            )}
            <button className="btn ghost small" type="button" onClick={() => setRequesting(true)}>
              New here? Request an account
            </button>
            <p className="login-help">
              Forgotten your password? Ask your network administrator; they will give you a one-time password.{" "}
              <a href="#/privacy">Privacy notice</a>
            </p>
          </form>
        )}
      </div>
    </main>
  );
}
