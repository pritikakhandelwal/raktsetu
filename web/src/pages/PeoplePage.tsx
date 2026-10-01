import { useState, type FormEvent } from "react";
import { api, type Account, type NewAccount, type Role, type User } from "../api";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";
import { AlertsPanel } from "../components/AlertsPanel";

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Network administrator",
  CENTRE_STAFF: "Blood centre staff",
  HOSPITAL_STAFF: "Hospital staff",
};

/** Administrators: everyone who can sign in, adding people, resetting passwords, disabling accounts. */
export function PeoplePage(props: SectionProps & { me: User }) {
  const { rev, changed, notify, me } = props;
  const { data: everyone, error } = useLoad(api.users, [rev]);
  const people = everyone?.filter((a) => !a.pending);
  const requests = everyone?.filter((a) => a.pending) ?? [];
  const { data: facilities } = useLoad(api.facilities, [rev]);
  const [adding, setAdding] = useState(false);
  const [issued, setIssued] = useState<NewAccount>();

  async function reset(a: Account) {
    if (!window.confirm(`Give ${a.displayName} a new one-time password? Their current password will stop working.`)) {
      return;
    }
    try {
      setIssued(await api.resetPassword(a.username));
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  async function toggle(a: Account) {
    if (a.enabled && !window.confirm(`Disable ${a.displayName}'s account? They will not be able to sign in.`)) {
      return;
    }
    try {
      await api.setEnabled(a.username, !a.enabled);
      notify(`${a.displayName}'s account is now ${a.enabled ? "disabled" : "active"}.`, true);
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  async function erase(a: Account) {
    if (
      !window.confirm(
        `Erase ${a.displayName}'s name and mobile number? This cannot be undone. Their username stays on past records.`,
      )
    ) {
      return;
    }
    try {
      await api.anonymise(a.username);
      notify(`${a.username}'s personal details have been erased.`, true);
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  async function decide(a: Account, approve: boolean) {
    if (!approve && !window.confirm(`Reject ${a.displayName}'s request to join? It will be deleted.`)) {
      return;
    }
    try {
      if (approve) await api.approveAccount(a.username);
      else await api.rejectAccount(a.username);
      notify(approve ? `${a.displayName} can now sign in.` : `${a.displayName}'s request was rejected.`, true);
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  const facilityName = (id: string | null) => facilities?.find((f) => f.id === id)?.name ?? id ?? "";

  return (
    <section className="section cream on-light page" aria-labelledby="people-title">
      <div className="wrap">
        <SectionHead
          id="people-title"
          title="People"
          red
          note="Everyone who can sign in. New people get a one-time password from you and choose their own when they first sign in."
        />
        {error && <p className="message error">{error}</p>}
        {issued && <OneTimePassword account={issued} onClose={() => setIssued(undefined)} />}
        {requests.length > 0 && (
          <div className="panel on-light" style={{ marginBottom: 24 }}>
            <h3>Requests to join</h3>
            <p className="login-help">
              People who asked for an account from the sign-in page. Check you know them before approving.
            </p>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Role</th>
                  <th>Works at</th>
                  <th>Asked on</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {requests.map((a) => (
                  <tr key={a.username}>
                    <td>
                      <strong>{a.displayName}</strong>
                    </td>
                    <td>{a.username}</td>
                    <td>{ROLE_LABELS[a.role]}</td>
                    <td>
                      {a.facilityId
                        ? `${a.facilityId} · ${facilityName(a.facilityId)}`
                        : `${a.requestedFacility ?? ""} (new: added to the network when you approve)`}
                    </td>
                    <td>{a.createdOn}</td>
                    <td className="row-actions">
                      <button className="btn dark small" type="button" onClick={() => decide(a, true)}>
                        Approve
                      </button>
                      <button className="btn ghost small" type="button" onClick={() => decide(a, false)}>
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="actions" style={{ marginBottom: 20 }}>
          <button className="btn dark" type="button" onClick={() => setAdding((a) => !a)}>
            {adding ? "Cancel" : "Add a person"}
          </button>
        </div>
        {adding && (
          <AddPerson
            facilities={facilities ?? []}
            onCreated={(a) => {
              setIssued(a);
              setAdding(false);
              changed();
            }}
            notify={notify}
          />
        )}
        {people && (
          <div className="table-scroll panel on-light">
            <table>
              <caption className="visually-hidden">People</caption>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Role</th>
                  <th>Works at</th>
                  <th>Status</th>
                  <th>Urgent alerts</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {people.map((a) => (
                  <tr key={a.username} className={a.enabled ? undefined : "muted-row"}>
                    <td>
                      <strong>{a.displayName}</strong>
                    </td>
                    <td>{a.username}</td>
                    <td>{ROLE_LABELS[a.role]}</td>
                    <td>{a.facilityId ? `${a.facilityId} · ${facilityName(a.facilityId)}` : "Whole network"}</td>
                    <td>
                      {a.anonymisedOn ? (
                        <span className="chip bad">details erased</span>
                      ) : !a.enabled ? (
                        <span className="chip bad">disabled</span>
                      ) : a.mustChangePassword ? (
                        <span className="chip STALE">awaiting first sign-in</span>
                      ) : (
                        <span className="chip ok">active</span>
                      )}
                    </td>
                    <td>{a.alertsEnabled && a.phone ? a.phone : <span className="muted">off</span>}</td>
                    <td className="row-actions">
                      {!a.anonymisedOn && (
                        <button className="btn ghost small" type="button" onClick={() => reset(a)}>
                          Reset password
                        </button>
                      )}
                      {a.username !== me.username && !a.anonymisedOn && (
                        <button className="btn ghost small" type="button" onClick={() => toggle(a)}>
                          {a.enabled ? "Disable" : "Enable"}
                        </button>
                      )}
                      {!a.enabled && !a.anonymisedOn && (
                        <button className="btn ghost small" type="button" onClick={() => erase(a)}>
                          Erase details
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <AlertsPanel rev={rev} />
      </div>
    </section>
  );
}

function AddPerson({
  facilities,
  onCreated,
  notify,
}: {
  facilities: { id: string; name: string; type: string }[];
  onCreated: (a: NewAccount) => void;
  notify: (m: string) => void;
}) {
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [touchedUsername, setTouchedUsername] = useState(false);
  const [role, setRole] = useState<Role>("CENTRE_STAFF");
  const [facilityId, setFacilityId] = useState("");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);

  const choices = facilities.filter((f) =>
    role === "HOSPITAL_STAFF" ? f.type === "HOSPITAL" : role === "CENTRE_STAFF" ? f.type !== "HOSPITAL" : false,
  );

  function suggest(name: string) {
    return name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, ".")
      .replace(/^\.+|\.+$/g, "")
      .slice(0, 30);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      onCreated(
        await api.createUser({
          username,
          displayName,
          role,
          ...(role === "ADMIN" ? {} : { facilityId }),
          ...(phone.trim() ? { phone } : {}),
        }),
      );
    } catch (err) {
      notify(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel on-light" onSubmit={submit} style={{ marginBottom: 24 }}>
      <h3>New person</h3>
      <div className="form">
        <label>
          Name
          <input
            value={displayName}
            onChange={(e) => {
              setDisplayName(e.target.value);
              if (!touchedUsername) setUsername(suggest(e.target.value));
            }}
            required
            autoFocus
          />
        </label>
        <label>
          Username
          <input
            value={username}
            onChange={(e) => {
              setTouchedUsername(true);
              setUsername(e.target.value.toLowerCase());
            }}
            required
          />
        </label>
        <label>
          Role
          <select
            value={role}
            onChange={(e) => {
              setRole(e.target.value as Role);
              setFacilityId("");
            }}
          >
            {(Object.keys(ROLE_LABELS) as Role[]).map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </select>
        </label>
        {role !== "ADMIN" && (
          <label>
            Works at
            <select value={facilityId} onChange={(e) => setFacilityId(e.target.value)} required>
              <option value="">Choose…</option>
              {choices.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.id} · {f.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Mobile for urgent alerts (optional)
          <input
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="98123 45678"
            autoComplete="off"
          />
        </label>
        {role !== "ADMIN" && choices.length === 0 && (
          <p className="message error full">
            Add a {role === "HOSPITAL_STAFF" ? "hospital" : "blood centre"} on the Facilities page first.
          </p>
        )}
        <div className="full">
          <button className="btn dark" type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create account"}
          </button>
        </div>
      </div>
    </form>
  );
}

function OneTimePassword({ account, onClose }: { account: NewAccount; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const text = `RaktSetu sign-in for ${account.user.displayName}\nUsername: ${account.user.username}\nOne-time password: ${account.oneTimePassword}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="panel dark one-time" role="status">
      <h3 className="red-text">Sign-in details for {account.user.displayName}</h3>
      <dl>
        <dt>Username</dt>
        <dd>{account.user.username}</dd>
        <dt>One-time password</dt>
        <dd>
          <code>{account.oneTimePassword}</code>
        </dd>
      </dl>
      <p>
        Give these to {account.user.displayName} privately. They will choose their own password the first time they sign
        in. <strong>This password will not be shown again.</strong>
      </p>
      <div className="actions">
        <button className="btn" type="button" onClick={copy}>
          {copied ? "Copied" : "Copy details"}
        </button>
        <button className="btn ghost" type="button" onClick={onClose}>
          Done
        </button>
      </div>
    </div>
  );
}
