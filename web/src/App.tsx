import { useCallback, useEffect, useState } from "react";
import { api, type Summary } from "./api";
import { addDays } from "./format";
import { Drop, errorText, useLoad } from "./shared";
import { Hero } from "./sections/Hero";
import { RiskSection } from "./sections/RiskSection";
import { RequestSection } from "./sections/RequestSection";
import { NetworkSection } from "./sections/NetworkSection";
import { TransfersSection } from "./sections/TransfersSection";
import { SimulationSection } from "./sections/SimulationSection";
import { AuditSection } from "./sections/AuditSection";

const LINKS = [
  ["#risk", "At risk"],
  ["#request", "Request"],
  ["#network", "Network"],
  ["#transfers", "Transfers"],
  ["#simulation", "Simulation"],
  ["#audit", "Audit"],
] as const;

export function App() {
  const [rev, setRev] = useState(0);
  const [toast, setToast] = useState<{ message: string; ok: boolean }>();
  const changed = useCallback(() => setRev((r) => r + 1), []);
  const notify = useCallback((message: string, ok = false) => setToast({ message, ok }), []);
  const { data: summary, error } = useLoad<Summary>(api.summary, [rev]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(undefined), 5000);
    return () => clearTimeout(t);
  }, [toast]);

  async function nextDay() {
    if (!summary) return;
    try {
      const s = await api.advanceDate(addDays(summary.today, 1));
      notify(`Today is now ${s.today}. Expired units were moved to wastage.`, true);
      changed();
    } catch (e) {
      notify(errorText(e));
    }
  }

  if (!summary) {
    return <div className="loading">{error || "Loading RaktSetu…"}</div>;
  }
  const props = { rev, changed, notify };

  return (
    <>
      <a className="visually-hidden" href="#main">
        Skip to content
      </a>
      <header className="nav">
        <div className="wrap">
          <a className="brand" href="#top">
            <Drop />
            RaktSetu
          </a>
          <nav aria-label="Sections">
            <ul className="nav-links">
              {LINKS.map(([href, label]) => (
                <li key={href}>
                  <a href={href}>{label}</a>
                </li>
              ))}
            </ul>
          </nav>
          <div className="day">
            <span>
              Today <strong>{summary.today}</strong>
            </span>
            <button className="btn ghost small" type="button" onClick={nextDay}>
              Next day
            </button>
          </div>
        </div>
      </header>

      <main id="main">
        <Hero summary={summary} />
        <RiskSection {...props} />
        <RequestSection {...props} />
        <NetworkSection {...props} />
        <TransfersSection {...props} />
        <SimulationSection {...props} today={summary.today} />
        <AuditSection {...props} />
      </main>

      <footer className="footer">
        <div className="wrap">
          <div>
            <div className="brand">
              <Drop />
              RaktSetu
            </div>
            <p>Expiry-aware blood redistribution network. Decision support: every transfer needs staff approval.</p>
          </div>
          <p>
            Sample network and simulated data for demonstration.
            <br />
            Shelf lives and rules to be confirmed against NACO/NBTC standards.
          </p>
        </div>
      </footer>

      {toast && (
        <div className={`toast${toast.ok ? " ok" : ""}`} role="status">
          {toast.message}
        </div>
      )}
    </>
  );
}
