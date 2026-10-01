import { useEffect, useRef, useState } from "react";
import { api, type NotificationList } from "../api";
import { errorText } from "../shared";

const POLL_MS = 30_000;

const KIND_LABELS: Record<string, string> = {
  REQUEST_TO_DISPATCH: "To dispatch",
  REQUEST_DISPATCHED: "On its way",
  REQUEST_CANCELLED: "Cancelled",
  REQUEST_UNMET: "Unmet request",
  TRANSFER_OUTGOING: "Send transfer",
  TRANSFER_INCOMING: "Incoming transfer",
  EXPIRING_SOON: "Expiring soon",
  REPORT_OVERDUE: "Report overdue",
  ACCOUNT_REQUESTED: "Request to join",
};

/** The bell in the header: unread count, and a panel of the latest notifications. Refreshes every 30 seconds. */
export function NotificationsBell({ rev, notify }: { rev: number; notify: (message: string) => void }) {
  const [data, setData] = useState<NotificationList>();
  const [open, setOpen] = useState(false);
  const panel = useRef<HTMLDivElement>(null);

  async function load() {
    try {
      setData(await api.notifications());
    } catch {
      // the next poll will try again
    }
  }

  useEffect(() => {
    void load();
    const t = setInterval(load, POLL_MS);
    return () => clearInterval(t);
  }, [rev]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (panel.current && !panel.current.contains(e.target as Node)) setOpen(false);
    };
    const escape = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  async function markRead(id: number) {
    try {
      await api.markRead(id);
      await load();
    } catch (e) {
      notify(errorText(e));
    }
  }

  async function markAll() {
    try {
      await api.markAllRead();
      await load();
    } catch (e) {
      notify(errorText(e));
    }
  }

  const unread = data?.unread ?? 0;
  return (
    <div className="bell" ref={panel}>
      <button
        type="button"
        className="bell-button"
        aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm7-6V11a7 7 0 0 0-5.5-6.84V3.5a1.5 1.5 0 0 0-3 0v.66A7 7 0 0 0 5 11v5l-2 2v1h18v-1l-2-2z"
            fill="currentColor"
          />
        </svg>
        {unread > 0 && <span className="badge">{unread > 99 ? "99+" : unread}</span>}
      </button>
      {open && (
        <div className="bell-panel" role="dialog" aria-label="Notifications">
          <div className="bell-head">
            <strong>Notifications</strong>
            {unread > 0 && (
              <button type="button" className="link" onClick={markAll}>
                Mark all read
              </button>
            )}
          </div>
          {!data || data.items.length === 0 ? (
            <p className="bell-empty">Nothing yet.</p>
          ) : (
            <ul>
              {data.items.map((n) => (
                <li key={n.id} className={n.read ? "read" : undefined}>
                  <button type="button" onClick={() => !n.read && markRead(n.id)} disabled={n.read}>
                    <span className="bell-kind">{KIND_LABELS[n.kind] ?? n.kind}</span>
                    <span className="bell-message">{n.message}</span>
                    <span className="bell-date">{n.createdOn}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
