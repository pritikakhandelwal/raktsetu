import { useState, type FormEvent } from "react";
import { api, type Role } from "../api";
import { errorText, useLoad } from "../shared";

type JoinRole = Extract<Role, "CENTRE_STAFF" | "HOSPITAL_STAFF">;

/**
 * Asking to join, from the sign-in page. The account stays locked until a network administrator approves it on the
 * People page, so nobody can give themselves access to the network.
 */
export function RequestAccount({ onBack }: { onBack: () => void }) {
  const { data: facilities, error: loadError } = useLoad(api.signupFacilities, []);
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<JoinRole>("HOSPITAL_STAFF");
  const [facilityId, setFacilityId] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  const choices = (facilities ?? []).filter((f) => (f.type === "HOSPITAL") === (role === "HOSPITAL_STAFF"));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (password !== confirm) {
      setError("The two passwords are different");
      return;
    }
    setBusy(true);
    try {
      await api.signup({ username, displayName, password, role, facilityId });
      setSent(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <div className="panel on-light login-panel">
        <h2 className="login-heading">Request sent</h2>
        <p>
          A network administrator has been told. You can sign in as <strong>{username}</strong> once they approve your
          request.
        </p>
        <button className="btn dark" type="button" onClick={onBack}>
          Back to sign in
        </button>
      </div>
    );
  }

  return (
    <form className="panel on-light login-panel" onSubmit={submit}>
      <h2 className="login-heading">Request an account</h2>
      <p className="login-help">
        For blood centre and hospital staff. A network administrator checks every request before the account can be
        used.
      </p>
      <label>
        Your name
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required autoFocus />
      </label>
      <label>
        Username
        <input
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value.toLowerCase())}
          placeholder="e.g. asha.patil"
          required
        />
      </label>
      <label>
        I work at a
        <select
          value={role}
          onChange={(e) => {
            setRole(e.target.value as JoinRole);
            setFacilityId("");
          }}
        >
          <option value="HOSPITAL_STAFF">Hospital</option>
          <option value="CENTRE_STAFF">Blood centre</option>
        </select>
      </label>
      <label>
        {role === "HOSPITAL_STAFF" ? "Hospital" : "Blood centre"}
        <select value={facilityId} onChange={(e) => setFacilityId(e.target.value)} required>
          <option value="">Choose…</option>
          {choices.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
      </label>
      {facilities && choices.length === 0 && (
        <p className="login-help">
          No {role === "HOSPITAL_STAFF" ? "hospitals" : "blood centres"} have been added yet. Ask your network
          administrator.
        </p>
      )}
      <label>
        Password
        <input
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </label>
      <label>
        Password again
        <input
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
      </label>
      <p className="login-help">At least 10 characters, with a letter and a digit.</p>
      <button className="btn dark" type="submit" disabled={busy}>
        {busy ? "Sending…" : "Send request"}
      </button>
      {(error || loadError) && (
        <p className="message error" role="alert">
          {error || loadError}
        </p>
      )}
      <button className="btn ghost small" type="button" onClick={onBack}>
        Back to sign in
      </button>
    </form>
  );
}
