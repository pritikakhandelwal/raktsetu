import { useEffect, useState, type FormEvent } from "react";
import { api, type Place, type Role } from "../api";
import { errorText } from "../shared";

type JoinRole = Extract<Role, "CENTRE_STAFF" | "HOSPITAL_STAFF">;

/**
 * Asking to join, from the sign-in page. People search for their hospital or blood centre among those already in the
 * network and a directory of Indian facilities. The account stays locked until a network administrator approves it
 * on the People page, so nobody can give themselves access to the network.
 */
export function RequestAccount({ onBack }: { onBack: () => void }) {
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<JoinRole>("HOSPITAL_STAFF");
  const [place, setPlace] = useState<Place>();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!place) {
      setError(`Search for your ${role === "HOSPITAL_STAFF" ? "hospital" : "blood centre"} and choose it`);
      return;
    }
    if (password !== confirm) {
      setError("The two passwords are different");
      return;
    }
    setBusy(true);
    try {
      await api.signup({
        username,
        displayName,
        password,
        role,
        facilityId: place.facilityId ?? undefined,
        directoryId: place.directoryId ?? undefined,
      });
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
            setPlace(undefined);
          }}
        >
          <option value="HOSPITAL_STAFF">Hospital</option>
          <option value="CENTRE_STAFF">Blood centre</option>
        </select>
      </label>
      <PlaceSearch role={role} chosen={place} onChoose={setPlace} />
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
      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}
      <button className="btn ghost small" type="button" onClick={onBack}>
        Back to sign in
      </button>
    </form>
  );
}

/** Type-ahead search for a hospital or blood centre; waits until typing pauses before asking the server. */
function PlaceSearch({
  role,
  chosen,
  onChoose,
}: {
  role: JoinRole;
  chosen: Place | undefined;
  onChoose: (place: Place | undefined) => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Place[]>([]);
  const [error, setError] = useState("");
  const noun = role === "HOSPITAL_STAFF" ? "hospital" : "blood centre";

  useEffect(() => {
    let live = true;
    const timer = setTimeout(() => {
      api
        .places(role, query)
        .then((found) => {
          if (live) {
            setResults(found);
            setError("");
          }
        })
        .catch((err: unknown) => live && setError(errorText(err)));
    }, 300);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [role, query]);

  if (chosen) {
    return (
      <div className="place-chosen">
        <span>
          <strong>{chosen.name}</strong>
          {chosen.where && <span className="muted"> · {chosen.where}</span>}
          {!chosen.onNetwork && <span className="muted"> · new to RaktSetu</span>}
        </span>
        <button className="btn ghost small" type="button" onClick={() => onChoose(undefined)}>
          Change
        </button>
      </div>
    );
  }

  return (
    <div className="place-search">
      <label>
        Your {noun}
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={role === "HOSPITAL_STAFF" ? "e.g. district hospital pune" : "e.g. kem hospital pune"}
          autoComplete="off"
        />
      </label>
      {error && <p className="message error">{error}</p>}
      {results.length > 0 && (
        <ul className="place-results" aria-label={`Matching ${noun}s`}>
          {results.map((p) => (
            <li key={p.facilityId ?? p.directoryId}>
              <button type="button" onClick={() => onChoose(p)}>
                <strong>{p.name}</strong>
                <span className="muted">
                  {p.where}
                  {p.onNetwork ? " · on RaktSetu" : ""}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {query.trim().length >= 2 && results.length === 0 && !error && (
        <p className="login-help">
          No match. Try fewer words or the town name; the directory lists licensed blood centres and government
          hospitals. If yours is missing, ask your network administrator to add it.
        </p>
      )}
      <p className="login-help">Directory: National Health Portal (Government of India) open data.</p>
    </div>
  );
}
