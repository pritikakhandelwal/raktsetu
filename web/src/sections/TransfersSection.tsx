import { useState } from "react";
import { api, type Transfer } from "../api";
import { plural } from "../format";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";

export function TransfersSection({ rev, changed, notify }: SectionProps) {
  const { data: strategies } = useLoad(api.strategies, []);
  const { data: comparison } = useLoad(api.compare, [rev]);
  const { data: transfers } = useLoad(api.transfers, [rev]);
  const [strategy, setStrategy] = useState("expiry-first");
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await action();
      notify(success, true);
      changed();
    } catch (e) {
      notify(errorText(e));
      changed();
    } finally {
      setBusy(false);
    }
  }

  const proposed = transfers?.filter((t) => t.status === "PROPOSED").length ?? 0;

  return (
    <section id="transfers" className="section cream on-light" aria-labelledby="transfers-title">
      <div className="wrap">
        <SectionHead
          id="transfers-title"
          title="Transfers"
          red
          note="Units only go where they are expected to be used before expiry, and never to centres with unverified data. Nothing moves until you approve it."
        />
        <div className="strategy-bar" role="group" aria-label="Strategy">
          {strategies?.map((s) => {
            const c = comparison?.find((x) => x.strategy === s.name);
            return (
              <button
                key={s.name}
                type="button"
                className="strategy"
                aria-pressed={strategy === s.name}
                onClick={() => setStrategy(s.name)}
              >
                <b>{s.name.replace("-", " ")}</b>
                <small>{s.description}</small>
                {c && (
                  <span className="nums">
                    <span>
                      <b>{c.unitsRescued}</b>rescued
                    </span>
                    <span>
                      <b>{c.transfers}</b>trips
                    </span>
                    <span>
                      <b>{c.totalKm}</b>km
                    </span>
                  </span>
                )}
              </button>
            );
          })}
        </div>
        <div className="actions" style={{ marginBottom: 28 }}>
          <button
            className="btn dark"
            type="button"
            disabled={busy}
            onClick={() =>
              run(() => api.plan(strategy), `New ${strategy} plan ready. Approve or reject each transfer below.`)
            }
          >
            Generate {strategy.replace("-", " ")} plan
          </button>
          {transfers && transfers.length > 0 && (
            <span style={{ alignSelf: "center", color: "var(--muted-light)", fontSize: 13 }}>
              {plural(proposed, "transfer")} waiting for approval
            </span>
          )}
        </div>
        {transfers && transfers.length === 0 && (
          <div className="empty">No plan yet. Pick a strategy and generate one.</div>
        )}
        {transfers?.map((t) => (
          <TransferRow key={t.id} transfer={t} busy={busy} run={run} />
        ))}
      </div>
    </section>
  );
}

function TransferRow({
  transfer: t,
  busy,
  run,
}: {
  transfer: Transfer;
  busy: boolean;
  run: (action: () => Promise<unknown>, success: string) => void;
}) {
  return (
    <article className="transfer">
      <div className="route">
        {t.sourceId}
        <span className="arrow" aria-label="to">
          →
        </span>
        {t.destinationId}
      </div>
      <div>
        <div className="units">
          {t.units} · {t.distanceKm} km · {t.travelMinutes} min
        </div>
        <p>{t.reason}</p>
      </div>
      <div className="buttons">
        {t.status === "PROPOSED" ? (
          <>
            <button
              className="btn small"
              type="button"
              disabled={busy}
              onClick={() => run(() => api.approve(t.id), `Approved: ${t.units} moved to ${t.destinationId}.`)}
            >
              Approve
            </button>
            <button
              className="btn ghost small"
              type="button"
              disabled={busy}
              onClick={() => run(() => api.reject(t.id), `Rejected ${t.id}.`)}
            >
              Reject
            </button>
          </>
        ) : (
          <span className={`status ${t.status}`}>{t.status}</span>
        )}
      </div>
    </article>
  );
}
