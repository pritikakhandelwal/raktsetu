import { useState, type FormEvent } from "react";
import { api, type User } from "../api";
import { ChangePasswordForm } from "./ChangePassword";
import { SectionHead, errorText, type SectionProps } from "../shared";

const ROLE_LABELS: Record<User["role"], string> = {
  ADMIN: "Network administrator",
  CENTRE_STAFF: "Blood centre staff",
  HOSPITAL_STAFF: "Hospital staff",
};

/** Your own account: who you are, urgent alerts to your phone, and changing your password. */
export function AccountPage({ user, notify }: SectionProps & { user: User }) {
  return (
    <section className="section cream on-light page" aria-labelledby="account-title">
      <div className="wrap">
        <SectionHead id="account-title" title="Your account" red />
        <div className="request-grid">
          <div className="panel dark">
            <h3 className="red-text">{user.displayName}</h3>
            <dl className="details">
              <dt>Username</dt>
              <dd>{user.username}</dd>
              <dt>Role</dt>
              <dd>{ROLE_LABELS[user.role]}</dd>
              <dt>Works at</dt>
              <dd>{user.facilityId ?? "The whole network"}</dd>
            </dl>
            <p className="login-help" style={{ color: "var(--muted-dark)" }}>
              To change your name or role, ask a network administrator.
            </p>
          </div>
          <ChangePasswordForm onDone={() => notify("Your password has been changed.", true)} />
        </div>
        <UrgentAlerts user={user} notify={notify} />
      </div>
    </section>
  );
}

function UrgentAlerts({ user, notify }: { user: User; notify: SectionProps["notify"] }) {
  const [phone, setPhone] = useState(user.phone ?? "");
  const [enabled, setEnabled] = useState(user.alertsEnabled);
  const [saved, setSaved] = useState(user.phone ?? "");
  const [busy, setBusy] = useState(false);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const account = await api.updateContact(phone, enabled && phone.trim() !== "");
      setPhone(account.phone ?? "");
      setSaved(account.phone ?? "");
      setEnabled(account.alertsEnabled);
      notify(account.alertsEnabled ? `Urgent alerts will go to ${account.phone}.` : "Urgent alerts are off.", true);
    } catch (err) {
      notify(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  async function test() {
    try {
      const r = await api.testAlert();
      notify(`Test alert on its way to ${r.to} (${r.channels.join(", ")}).`, true);
    } catch (err) {
      notify(errorText(err));
    }
  }

  return (
    <form className="panel on-light alerts-panel" onSubmit={save}>
      <h3>Urgent alerts</h3>
      <p className="login-help">
        {user.role === "HOSPITAL_STAFF"
          ? "Get a text when blood you requested is on its way."
          : user.role === "CENTRE_STAFF"
            ? "Get a text the moment a hospital needs blood from your centre, so an emergency never waits for someone to open RaktSetu."
            : "Get a text when a request cannot be met anywhere in the network."}
      </p>
      <div className="form">
        <label>
          Mobile number
          <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="98123 45678" />
        </label>
        <label className="check-label">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          Send me urgent alerts
        </label>
        <div className="full actions">
          <button className="btn dark" type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </button>
          {saved && (
            <button className="btn ghost small" type="button" onClick={test}>
              Send me a test alert
            </button>
          )}
        </div>
      </div>
    </form>
  );
}
