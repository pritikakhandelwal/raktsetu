import { api, type AlertStatus } from "../api";
import { useLoad } from "../shared";

const STATUS_CHIP: Record<AlertStatus, string> = { SENT: "ok", PENDING: "STALE", FAILED: "bad" };

/** Administrators: which alert channels are working, and the latest SMS or WhatsApp deliveries. */
export function AlertsPanel({ rev }: { rev: number }) {
  const { data, error } = useLoad(api.alerts, [rev]);
  if (error) return <p className="message error">{error}</p>;
  if (!data) return null;
  const active = data.channels.filter((c) => c.active).map((c) => c.name);
  const logOnly = active.length === 1 && active[0] === "LOG";

  return (
    <div className="panel on-light alerts-panel">
      <h3>Urgent alerts</h3>
      <p className="login-help">
        Requests to dispatch, blood on its way and requests that could not be met are also sent by{" "}
        {logOnly ? "SMS or WhatsApp" : active.join(" and ")} to people who added a mobile number
        {logOnly ? ", once a provider is set up" : ""}. {data.counts.SENT} sent, {data.counts.PENDING} waiting,{" "}
        {data.counts.FAILED} failed.
      </p>
      {logOnly && (
        <p className="message">
          No SMS or WhatsApp provider is configured yet, so alerts are only written to the server log. See
          "Urgent alerts" in the README to connect Twilio SMS or WhatsApp.
        </p>
      )}
      {data.recent.length === 0 ? (
        <div className="empty">No alerts yet.</div>
      ) : (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">Latest alerts</caption>
            <thead>
              <tr>
                <th>To</th>
                <th>By</th>
                <th>Message</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {data.recent.map((a) => (
                <tr key={a.id}>
                  <td>
                    {a.username}
                    <br />
                    <span className="muted">{a.phone}</span>
                  </td>
                  <td>{a.channel}</td>
                  <td>{a.message}</td>
                  <td>
                    <span className={`chip ${STATUS_CHIP[a.status]}`}>{a.status.toLowerCase()}</span>
                    {a.attempts > 1 && <span className="muted"> · {a.attempts} tries</span>}
                    {a.lastError && <div className="muted small-text">{a.lastError}</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
