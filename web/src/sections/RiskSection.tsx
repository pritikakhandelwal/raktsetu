import { api } from "../api";
import { componentLabel, plural, shortName, place } from "../format";
import { SectionHead, useLoad, type SectionProps } from "../shared";

const SHOWN = 8;

/** At-risk stock, soonest first; for centre staff, only their own centre ({@code facilityId}). */
export function RiskSection({ rev, facilityId }: SectionProps & { facilityId?: string }) {
  const { data: all, error } = useLoad(api.risks, [rev]);
  const risks = facilityId ? all?.filter((r) => r.facilityId === facilityId) : all;
  const total = risks?.reduce((n, r) => n + r.atRiskUnits, 0) ?? 0;

  return (
    <section id="risk" className="section cream on-light" aria-labelledby="risk-title">
      <div className="wrap">
        <SectionHead
          id="risk-title"
          title="At risk"
          note={
            risks
              ? `${plural(total, "unit")} will likely expire before the centre holding them can use them. Soonest first.`
              : undefined
          }
        />
        {error && <p className="message error">{error}</p>}
        {risks && risks.length === 0 && <div className="empty">Nothing is at risk this week.</div>}
        <div className="cards">
          {risks?.slice(0, SHOWN).map((r) => (
            <article className="risk-card" key={`${r.facilityId}-${r.component}-${r.group}`}>
              <div className="risk-tile">
                <div className="days">
                  {r.soonestDaysLeft}
                  <small>{r.soonestDaysLeft === 1 ? "day left" : "days left"}</small>
                </div>
                <div className="what">
                  {r.atRiskUnits} × {r.group} {componentLabel(r.component)}
                </div>
              </div>
              <div className="where">
                {r.facilityId} · {place(r.facilityName) || shortName(r.facilityName)}
              </div>
              <p>
                {r.atRiskUnits} of {r.usableStock} at risk; this centre uses about {r.averageDaily.toFixed(1)} a day.
              </p>
            </article>
          ))}
        </div>
        {risks && risks.length > SHOWN && (
          <p className="sim-note" style={{ color: "var(--muted-light)" }}>
            Showing the {SHOWN} most urgent of {risks.length} groups. Generate a transfer plan below to act on them.
          </p>
        )}
      </div>
    </section>
  );
}
