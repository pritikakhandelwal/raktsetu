import { useState } from "react";
import { api, type SimulationView } from "../api";
import { percent } from "../format";
import { SectionHead, errorText, type SectionProps } from "../shared";

export function SimulationSection({ notify, today }: SectionProps & { today: string }) {
  const [days, setDays] = useState(30);
  const [result, setResult] = useState<SimulationView>();
  const [busy, setBusy] = useState(false);

  async function simulate() {
    setBusy(true);
    try {
      setResult(await api.simulate(days));
    } catch (e) {
      notify(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  const best = result?.results.slice(1).reduce((a, b) => (b.wastageReductionPercent > a.wastageReductionPercent ? b : a));
  const max = Math.max(1, ...(result?.results.map((r) => r.unitsExpired) ?? [1]));

  return (
    <section id="simulation" className="section" aria-labelledby="simulation-title">
      <div className="glow" style={{ width: 420, height: 420, left: "-160px", bottom: "-120px" }} />
      <div className="wrap">
        <SectionHead
          id="simulation-title"
          title="Simulation"
          red
          note="A fresh copy of the sample network, run day by day. Every policy sees exactly the same supply and demand, so the difference comes only from redistribution."
        />
        <div className="sim-grid">
          <div className="sim-copy">
            <div className="big-number" aria-live="polite">
              {best ? percent(best.wastageReductionPercent) : "?"}
            </div>
            <p>
              {best
                ? `less wastage over ${result!.days} days with ${best.policy}, starting ${result!.start} (seed ${result!.seed}).`
                : `How much wastage would redistribution prevent? Simulate from ${today}.`}
            </p>
            <div className="actions">
              <label style={{ flexDirection: "row", alignItems: "center", color: "var(--muted-dark)" }}>
                Days
                <select
                  value={days}
                  onChange={(e) => setDays(Number(e.target.value))}
                  style={{ width: 90, background: "var(--ink)", color: "var(--white)", borderColor: "var(--ink-2)" }}
                >
                  {[7, 30, 90].map((d) => (
                    <option key={d}>{d}</option>
                  ))}
                </select>
              </label>
              <button className="btn" type="button" onClick={simulate} disabled={busy}>
                {busy ? "Simulating…" : "Run simulation"}
              </button>
            </div>
          </div>
          <div>
            {!result && <div className="empty">Results appear here: units expired under each policy.</div>}
            {result && (
              <>
                <div className="bars" role="list" aria-label="Units expired by policy">
                  {result.results.map((r, i) => (
                    <div className="bar-row" role="listitem" key={r.policy}>
                      <div className="label">
                        {r.policy}
                        <small>
                          {r.requestsUnmet} unmet requests · {r.unitsTransferred} moved
                        </small>
                      </div>
                      <div className="bar-track">
                        <div
                          className={`bar-fill${i === 0 ? " base" : ""}`}
                          style={{ width: `${(100 * r.unitsExpired) / max}%` }}
                        >
                          {r.unitsExpired}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
                <p className="sim-note">
                  Bars show units expired. Platelets dominate:{" "}
                  {result.results.map((r) => `${r.expiredByComponent.PLATELETS} (${r.policy})`).join(", ")}. Synthetic
                  network and illustrative demand; this shows the mechanism, not real-world impact.
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
