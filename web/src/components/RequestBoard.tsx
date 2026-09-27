import { useState } from "react";
import { api, type BloodRequest, type User } from "../api";
import { componentLabel, daysLeft, plural } from "../format";
import { errorText, useLoad, type SectionProps } from "../shared";

const STEPS = ["ALLOCATED", "DISPATCHED", "RECEIVED"] as const;
const STEP_LABELS: Record<(typeof STEPS)[number], string> = {
  ALLOCATED: "Reserved",
  DISPATCHED: "On its way",
  RECEIVED: "Received",
};

/**
 * Requests with the actions the signed-in person may take: centres dispatch their part, hospitals cancel or confirm
 * receipt, administrators can do both.
 */
export function RequestBoard({ user, rev, changed, notify }: SectionProps & { user: User }) {
  const { data: requests, error } = useLoad(api.requests, [rev]);
  const [busy, setBusy] = useState(false);

  async function act(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await action();
      notify(success, true);
    } catch (e) {
      notify(errorText(e));
    } finally {
      setBusy(false);
      changed();
    }
  }

  if (error) return <p className="message error">{error}</p>;
  if (!requests) return null;
  if (requests.length === 0) return <div className="empty">No requests yet.</div>;

  const open = requests.filter((r) => r.status === "ALLOCATED" || r.status === "DISPATCHED");
  const closed = requests.filter((r) => !open.includes(r));
  return (
    <div className="requests">
      {[...open, ...closed].map((r) => (
        <RequestCard key={r.id} request={r} user={user} busy={busy} act={act} />
      ))}
    </div>
  );
}

function RequestCard({
  request: r,
  user,
  busy,
  act,
}: {
  request: BloodRequest;
  user: User;
  busy: boolean;
  act: (action: () => Promise<unknown>, success: string) => void;
}) {
  const mine = user.role === "CENTRE_STAFF" ? r.parts.find((p) => p.sourceId === user.facilityId) : undefined;
  const canDispatch =
    r.status === "ALLOCATED" &&
    ((user.role === "ADMIN" && r.parts.some((p) => !p.dispatched)) || (mine !== undefined && !mine.dispatched));
  const isRequester = user.role === "ADMIN" || (user.role === "HOSPITAL_STAFF" && user.facilityId === r.hospitalId);
  const canCancel = isRequester && r.status === "ALLOCATED" && r.parts.every((p) => !p.dispatched);
  const canReceive = isRequester && r.status === "DISPATCHED";
  const stepIndex = STEPS.indexOf(r.status as (typeof STEPS)[number]);

  return (
    <article className={`request-card status-${r.status}`}>
      <header>
        <div>
          <div className="request-title">
            #{r.id} · {r.quantity} × {r.group} {componentLabel(r.component)}
          </div>
          <div className="request-meta">
            For {r.hospitalId} · {r.urgency === "EMERGENCY" ? "Emergency" : "Routine"} · raised {r.raisedOn} by{" "}
            {r.raisedBy}
          </div>
        </div>
        {stepIndex >= 0 ? (
          <ol className="steps" aria-label="Progress">
            {STEPS.map((s, i) => (
              <li key={s} className={i <= stepIndex ? "done" : undefined} aria-current={i === stepIndex}>
                {STEP_LABELS[s]}
              </li>
            ))}
          </ol>
        ) : (
          <span className={`chip ${r.status === "UNMET" ? "bad" : ""}`}>{r.status.toLowerCase()}</span>
        )}
      </header>
      {r.parts.map((p) => (
        <div className="request-part" key={p.sourceId}>
          <span className={`dot ${p.dispatched ? "sent" : ""}`} aria-hidden="true" />
          <span>
            <strong>
              {plural(p.unitIds.length, "unit")} {p.donorGroups.join("/")} from {p.sourceId}
            </strong>{" "}
            · {p.distanceKm} km · {p.travelMinutes} min · {p.dispatched ? "dispatched" : "waiting to be sent"}
            <small>
              {p.unitIds.join(", ")} · {p.daysLeft.map((d) => daysLeft(d)).join(", ")}
            </small>
          </span>
        </div>
      ))}
      {r.note && <p className="request-note">{r.note}</p>}
      {(canDispatch || canCancel || canReceive) && (
        <div className="actions">
          {canDispatch && (
            <button
              className="btn small"
              type="button"
              disabled={busy}
              onClick={() => act(() => api.dispatch(r.id), `Request #${r.id} dispatched.`)}
            >
              {user.role === "ADMIN" ? "Mark all dispatched" : "Dispatch my units"}
            </button>
          )}
          {canReceive && (
            <button
              className="btn small"
              type="button"
              disabled={busy}
              onClick={() => act(() => api.receive(r.id), `Request #${r.id} marked received.`)}
            >
              Confirm received
            </button>
          )}
          {canCancel && (
            <button
              className="btn ghost small"
              type="button"
              disabled={busy}
              onClick={() => act(() => api.cancel(r.id), `Request #${r.id} cancelled; units are back on the shelf.`)}
            >
              Cancel request
            </button>
          )}
        </div>
      )}
    </article>
  );
}
