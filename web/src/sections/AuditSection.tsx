import { api, type AuditEntry } from "../api";
import { componentLabel, plural } from "../format";
import { SectionHead, useLoad, type SectionProps } from "../shared";

const VERBS: Record<AuditEntry["type"], string> = {
  COLLECTED: "Collected",
  ISSUED: "Issued",
  TRANSFERRED: "Transferred",
  DISCARDED: "Discarded",
};

export function AuditSection({ rev }: SectionProps) {
  const { data: audit, error } = useLoad(() => api.audit(9), [rev]);

  return (
    <section id="audit" className="section cream on-light" aria-labelledby="audit-title">
      <div className="wrap">
        <SectionHead
          id="audit-title"
          title="Audit log"
          red
          note="Every collection, issue, transfer and discard, newest first. Each entry carries the hash of the one before it, so editing any past line breaks the chain."
        />
        {error && <p className="message error">{error}</p>}
        {audit && (
          <p style={{ marginBottom: 40 }}>
            <span className={`seal ${audit.intact ? "ok" : "bad"}`} role="status">
              {audit.intact ? "● Chain intact" : "● Tampering detected"}
            </span>{" "}
            <span style={{ marginLeft: 10, fontSize: 13, color: "var(--muted-light)" }}>{audit.message}</span>
          </p>
        )}
        {audit && audit.entries.length === 0 && <div className="empty">Nothing recorded yet.</div>}
        <div className="quotes">
          {audit?.entries.map((e) => (
            <blockquote className="quote" key={e.sequence} style={{ margin: 0 }}>
              <div className="event">
                {VERBS[e.type]} {plural(e.units, "unit")}
              </div>
              <p>
                {e.group} {componentLabel(e.component).toLowerCase()} at {e.facilityId}
                {e.counterparty && ` → ${e.counterparty}`}
                {e.note && e.note !== "exact match" && `: ${e.note}`}
              </p>
              <code>
                #{e.sequence} · {e.date} · {e.hash.slice(0, 12)}…
              </code>
            </blockquote>
          ))}
        </div>
      </div>
    </section>
  );
}
