import { useCallback, useEffect, useState, type ReactNode } from "react";
import { ApiError, api, type Summary, type User } from "./api";
import { Login } from "./Login";
import { Drop, SectionHead, useLoad, type SectionProps } from "./shared";
import { Link, useRoute } from "./router";
import { NotificationsBell } from "./components/NotificationsBell";
import { RequestBoard } from "./components/RequestBoard";
import { Hero } from "./sections/Hero";
import { RiskSection } from "./sections/RiskSection";
import { RequestSection } from "./sections/RequestSection";
import { NetworkSection } from "./sections/NetworkSection";
import { TransfersSection } from "./sections/TransfersSection";
import { AuditSection } from "./sections/AuditSection";
import { CentreView, type CentrePage } from "./views/CentreView";
import { HospitalView, type HospitalPage } from "./views/HospitalView";
import { Setup } from "./pages/Setup";
import { ForcedPasswordChange } from "./pages/ChangePassword";
import { FacilitiesPage } from "./pages/FacilitiesPage";
import { PeoplePage } from "./pages/PeoplePage";
import { GettingStarted } from "./pages/GettingStarted";
import { AccountPage } from "./pages/AccountPage";
import { PrivacyPage } from "./pages/PrivacyPage";

const PAGES: Record<User["role"], [string, string][]> = {
  ADMIN: [
    ["/", "Overview"],
    ["/requests", "Requests"],
    ["/transfers", "Transfers"],
    ["/facilities", "Facilities"],
    ["/people", "People"],
    ["/audit", "Audit"],
  ],
  CENTRE_STAFF: [
    ["/", "Today"],
    ["/stock", "Stock"],
    ["/transfers", "Transfers"],
  ],
  HOSPITAL_STAFF: [
    ["/", "Request blood"],
    ["/requests", "My requests"],
    ["/availability", "Availability"],
  ],
};

const ROLE_LABELS: Record<User["role"], string> = {
  ADMIN: "Network admin",
  CENTRE_STAFF: "Centre staff",
  HOSPITAL_STAFF: "Hospital staff",
};

/**
 * Gates, in order: first-run setup (no administrator yet), sign-in, a forced password change for new or reset
 * accounts, and then the pages for the user's role.
 */
export function App() {
  const [needsSetup, setNeedsSetup] = useState<boolean>();
  const [user, setUser] = useState<User | null>();
  const [failure, setFailure] = useState("");
  const route = useRoute();

  const loadMe = useCallback(() => {
    api
      .me()
      .then(setUser)
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 401) setUser(null);
        else setFailure("RaktSetu's server is not answering. Check that it is running, then reload this page.");
      });
  }, []);

  useEffect(() => {
    api
      .setupStatus()
      .then((s) => {
        setNeedsSetup(s.needsSetup);
        if (!s.needsSetup) loadMe();
      })
      .catch(() => setFailure("RaktSetu's server is not answering. Check that it is running, then reload this page."));
  }, [loadMe]);

  async function signOut() {
    try {
      await api.logout();
    } finally {
      window.location.hash = "";
      setUser(null);
    }
  }

  if (failure) return <div className="loading">{failure}</div>;
  if (needsSetup) {
    return (
      <Setup
        onDone={(u) => {
          setNeedsSetup(false);
          setUser(u);
        }}
      />
    );
  }
  if (user === undefined) return <div className="loading">Loading RaktSetu…</div>;
  if (user === null) {
    return route === "/privacy" ? <PrivacyPage signedIn={false} /> : <Login onSignedIn={setUser} />;
  }
  if (user.mustChangePassword) {
    return <ForcedPasswordChange name={user.displayName} onDone={loadMe} onSignOut={signOut} />;
  }
  return <Shell user={user} onSignOut={signOut} />;
}

function Shell({ user, onSignOut }: { user: User; onSignOut: () => void }) {
  const route = useRoute();
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

  if (!summary) {
    return <div className="loading">{error || "Loading RaktSetu…"}</div>;
  }
  const props: SectionProps = { rev, changed, notify };
  const pages = PAGES[user.role];
  const known = route === "/account" || route === "/privacy" || pages.some(([path]) => path === route);
  const page = known ? route : "/";

  return (
    <>
      <a className="visually-hidden" href="#main">
        Skip to content
      </a>
      <header className="nav">
        <div className="wrap">
          <Link to="/" className="brand">
            <Drop />
            RaktSetu
          </Link>
          <nav aria-label="Pages">
            <ul className="nav-links">
              {pages.map(([path, label]) => (
                <li key={path}>
                  <Link to={path} className={page === path ? "active" : undefined}>
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <div className="day">
            <span>
              Today <strong>{summary.today}</strong>
            </span>
            <NotificationsBell rev={rev} notify={notify} />
            <Link to="/account" className={`who${page === "/account" ? " active" : ""}`}>
              <strong>{user.username}</strong> · {ROLE_LABELS[user.role]}
            </Link>
            <button className="btn ghost small" type="button" onClick={onSignOut}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main id="main" key={page}>
        {page === "/account" ? (
          <AccountPage {...props} user={user} />
        ) : page === "/privacy" ? (
          <PrivacyPage signedIn />
        ) : (
          <>
            {user.role === "ADMIN" && <AdminPage page={page} props={props} user={user} summary={summary} />}
            {user.role === "CENTRE_STAFF" && (
              <CentreView {...props} user={user} today={summary.today} page={centrePage(page)} />
            )}
            {user.role === "HOSPITAL_STAFF" && <HospitalView {...props} user={user} page={hospitalPage(page)} />}
          </>
        )}
      </main>

      <footer className="footer">
        <div className="wrap">
          <div>
            <div className="brand">
              <Drop />
              RaktSetu
            </div>
            <p>Expiry-aware blood redistribution network. Decision support: every transfer needs approval.</p>
          </div>
          <p>
            Signed in as {user.displayName}.
            <br />
            Every change is recorded on a tamper-evident log.
            <br />
            <Link to="/privacy">Privacy notice</Link>
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

function centrePage(route: string): CentrePage {
  return route === "/stock" ? "stock" : route === "/transfers" ? "transfers" : "home";
}

function hospitalPage(route: string): HospitalPage {
  return route === "/requests" ? "requests" : route === "/availability" ? "availability" : "home";
}

function AdminPage({ page, props, user, summary }: { page: string; props: SectionProps; user: User; summary: Summary }) {
  let content: ReactNode;
  switch (page) {
    case "/requests":
      content = (
        <div className="page-wrap">
          <RequestSection {...props} />
          <section id="requests" className="section cream on-light" aria-labelledby="requests-title">
            <div className="wrap">
              <SectionHead
                id="requests-title"
                title="All requests"
                red
                note="Every request in the network: reserved, on its way, received, cancelled or unmet."
              />
              <RequestBoard {...props} user={user} />
            </div>
          </section>
        </div>
      );
      break;
    case "/transfers":
      content = (
        <div className="page-wrap">
          <TransfersSection {...props} />
        </div>
      );
      break;
    case "/facilities":
      content = <FacilitiesPage {...props} />;
      break;
    case "/people":
      content = <PeoplePage {...props} me={user} />;
      break;
    case "/audit":
      content = (
        <div className="page-wrap">
          <AuditSection {...props} />
        </div>
      );
      break;
    default:
      content = (
        <>
          <Hero summary={summary} />
          <GettingStarted {...props} />
          <RiskSection {...props} />
          <NetworkSection {...props} />
        </>
      );
  }
  return content;
}
