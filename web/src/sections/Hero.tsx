import type { Summary } from "../api";

const TICKER = [
  "17.43 lakh units discarded in 2025",
  "46% of discards were expired units",
  "platelets last just 5 days",
  "every transfer needs staff approval",
  "every issue is on a tamper-evident log",
];

export function Hero({ summary }: { summary: Summary }) {
  const ticker = [...TICKER, ...TICKER];
  return (
    <section id="top" className="section hero" aria-labelledby="hero-title">
      <div className="glow" style={{ width: 380, height: 380, left: "-120px", top: "140px" }} />
      <div className="glow" style={{ width: 180, height: 180, left: "46%", top: "60px", opacity: 0.6 }} />
      <div className="wrap hero-grid">
        <div className="hero-copy">
          <p className="kicker">Expiry-aware blood redistribution</p>
          <h1 id="hero-title">
            Every unit
            <span>counts.</span>
          </h1>
          <p className="lede">
            e-Raktkosh shows <strong>where blood is</strong>. RaktSetu shows{" "}
            <strong>which units will expire unused, and where they should go now</strong>, even when stock reports are
            out of date.
          </p>
          <div className="actions">
            <a className="btn" href="#/requests">
              Raise a request
            </a>
            <a className="btn ghost" href="#risk">
              See units at risk
            </a>
          </div>
        </div>
        <div className="hero-panel" aria-label="Network today">
          <svg className="drop" viewBox="0 0 20 26" aria-hidden="true">
            <path d="M10 0c3.6 5.4 10 10.6 10 16a10 10 0 0 1-20 0C0 10.6 6.4 5.4 10 0z" fill="#0a0a0a" />
          </svg>
          <div className="stat">
            <strong>{summary.unitsInStock}</strong>
            <span>Units in stock</span>
          </div>
          <div className="stat">
            <strong>{summary.atRiskUnits}</strong>
            <span>At risk this week</span>
          </div>
          <div className="stat">
            <strong>{summary.urgentUnits}</strong>
            <span>Expire within 2 days</span>
          </div>
          <div className="stat">
            <strong>{summary.unitsWasted}</strong>
            <span>Wasted so far</span>
          </div>
        </div>
      </div>
      <div className="ticker" aria-hidden="true">
        <div>
          {ticker.map((t, i) => (
            <span key={i}>
              {t}
              <em>●</em>
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
