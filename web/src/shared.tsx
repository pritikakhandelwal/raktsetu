import { useEffect, useState } from "react";
import { ApiError } from "./api";

/** Props every section receives: a revision number that changes after any data change, and callbacks. */
export interface SectionProps {
  rev: number;
  changed: () => void;
  notify: (message: string, ok?: boolean) => void;
}

/** Loads data whenever the dependencies change; keeps the previous value while reloading. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): { data: T | undefined; error: string } {
  const [data, setData] = useState<T>();
  const [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    load()
      .then((d) => {
        if (live) {
          setData(d);
          setError("");
        }
      })
      .catch((e: unknown) => live && setError(errorText(e)));
    return () => {
      live = false;
    };
  }, deps);
  return { data, error };
}

export function errorText(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof TypeError) return "Cannot reach the RaktSetu server. Is it running?";
  return e instanceof Error ? e.message : String(e);
}

/** The RaktSetu drop mark. */
export function Drop({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 20 26" aria-hidden="true">
      <path d="M10 0c3.6 5.4 10 10.6 10 16a10 10 0 0 1-20 0C0 10.6 6.4 5.4 10 0z" fill="#ef3e2b" />
    </svg>
  );
}

export function SectionHead({ id, title, note, red }: { id: string; title: string; note?: string; red?: boolean }) {
  return (
    <div className="section-head">
      <h2 id={id} className={red ? "red-text" : undefined}>
        {title}
      </h2>
      {note && <p>{note}</p>}
    </div>
  );
}
