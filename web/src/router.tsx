import { useEffect, useState, type ReactNode } from "react";

/** The page named by a hash like "#/people", or null for an in-page anchor like "#risk". */
function read(): string | null {
  const hash = window.location.hash.replace(/^#/, "");
  if (hash === "") return "/";
  return hash.startsWith("/") ? hash : null;
}

/** The current page, from the address bar's hash: "#/people" gives "/people". In-page anchors keep the page. */
export function useRoute(): string {
  const [route, setRoute] = useState(() => read() ?? "/");
  useEffect(() => {
    const update = () => {
      const next = read();
      if (next === null) return;
      setRoute(next);
      window.scrollTo({ top: 0 });
    };
    window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, []);
  return route;
}

export function navigate(to: string) {
  window.location.hash = to;
}

export function Link({ to, children, className }: { to: string; children: ReactNode; className?: string }) {
  return (
    <a href={`#${to}`} className={className}>
      {children}
    </a>
  );
}
