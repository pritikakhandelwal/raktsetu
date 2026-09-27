import { useCallback, useEffect, useState } from "react";
import { ApiError, api, type Summary, type User } from "./api";
import { Login } from "./Login";
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

const ROLE_LABELS: Record<User["role"], string> = {
  ADMIN: "Network admin",
  CENTRE_STAFF: "Centre staff",
  HOSPITAL_STAFF: "Hospital staff",
};

/** Signs the user in first; the dashboard only loads for a signed-in user. */
export function App() {
  const [user, setUser] = useState<User | null>();

  useEffect(() => {
    api
      .me()
      .then(setUser)
      .catch((e: unknown) => setUser(e instanceof ApiError && e.status === 401 ? null : undefined));
  }, []);

  async function signOut() {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  }

  if (user === undefined) return <div className="loading">Loading RaktSetu…</div>;
  if (user === null) return <Login onSignedIn={setUser} />;
  return <Dashboard user={user} onSignOut={signOut} />;
}

function Dashboard({ user, onSignOut }: { user: User; onSignOut: () => void }) {
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
            {user.role === "ADMIN" && (
              <button className="btn ghost small" type="button" onClick={nextDay}>
                Next day
              </button>
            )}
            <span className="who" title={user.displayName}>
              <strong>{user.username}</strong> · {ROLE_LABELS[user.role]}
            </span>
            <button className="btn ghost small" type="button" onClick={onSignOut}>
              Sign out
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
