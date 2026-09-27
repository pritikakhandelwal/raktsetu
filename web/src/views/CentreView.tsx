import { useState, type FormEvent } from "react";
import { api, COMPONENTS, GROUPS, type Component, type User } from "../api";
import { componentLabel, daysLeft, place, plural } from "../format";
import { RequestBoard } from "../components/RequestBoard";
import { RiskSection } from "../sections/RiskSection";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";

/** What staff at one blood centre see: their stock, what to send, what is coming, and daily tasks. */
export function CentreView(props: SectionProps & { user: User; today: string }) {
  const { user, rev, changed, notify } = props;
  const centre = user.facilityId!;
  const { data: stock } = useLoad(() => api.facilityStock(centre), [centre, rev]);

  async function report() {
    try {
      await api.recordReport(centre);
      notify("Today's stock report recorded. The network can rely on your figures again.", true);
      changed();
    } catch (e) {
      notify(errorText(e));
    }
  }

  const f = stock?.facility;
  return (
    <>
      <section id="top" className="section hero role-hero" aria-labelledby="centre-title">
        <div className="glow" style={{ width: 360, height: 360, left: "-120px", top: "60px" }} />
        <div className="wrap hero-grid">
          <div className="hero-copy">
            <p className="kicker">
              {centre} · {f?.type === "HUB" ? "Hub blood centre" : "Blood storage centre"}
            </p>
            <h1 id="centre-title">
              {f ? place(f.name) || f.name : centre}
              <span>shelves.</span>
            </h1>
            <p className="lede">
              Last stock report: <strong>{f?.lastReportedOn ?? "never"}</strong>{" "}
              {f && <span className={`chip ${f.freshness}`}>{f.freshness.toLowerCase()}</span>}
            </p>
            <div className="actions">
              <button className="btn" type="button" onClick={report}>
                Report today's stock
              </button>
              <a className="btn ghost" href="#dispatch">
                Requests to dispatch
              </a>
            </div>
          </div>
          <div className="hero-panel" aria-label="This centre today">
            <div className="stat">
              <strong>{f?.units ?? "–"}</strong>
              <span>Units on the shelf</span>
            </div>
            <div className="stat">
              <strong>{f?.urgentUnits ?? "–"}</strong>
              <span>Expire within 2 days</span>
            </div>
            <div className="stat">
              <strong>{f?.atRiskUnits ?? "–"}</strong>
              <span>At risk this week</span>
            </div>
            <div className="stat">
              <strong>{f?.daysSinceReport ?? "–"}</strong>
              <span>Days since report</span>
            </div>
          </div>
        </div>
      </section>

      <section id="dispatch" className="section cream on-light" aria-labelledby="dispatch-title">
        <div className="wrap">
          <SectionHead
            id="dispatch-title"
            title="To dispatch"
            red
            note="Requests your centre is supplying. The units are already reserved for the patient; dispatch them and the hospital is told they are on the way."
          />
          <RequestBoard {...props} />
        </div>
      </section>

      <IncomingTransfers {...props} centre={centre} />

      <section id="shelves" className="section" aria-labelledby="shelves-title">
        <div className="wrap">
          <SectionHead
            id="shelves-title"
            title="Shelves"
            red
            note="Everything on your shelves, soonest-expiring first. Log new collections as they arrive, and discard damaged bags with a reason."
          />
          <div className="request-grid">
            <div className="panel on-light">
              {!stock || stock.lines.length === 0 ? (
                <div className="empty">No stock.</div>
              ) : (
                <div className="table-scroll">
                  <table>
                    <caption className="visually-hidden">Stock at {centre}</caption>
                    <thead>
                      <tr>
                        <th>Component</th>
                        <th>Group</th>
                        <th className="num">Units</th>
                        <th>Soonest expiry</th>
                      </tr>
                    </thead>
                    <tbody>
                      {stock.lines.map((l) => (
                        <tr key={`${l.component}-${l.group}`} className={l.urgent ? "urgent" : undefined}>
                          <td>{componentLabel(l.component)}</td>
                          <td>
                            <strong>{l.group}</strong>
                          </td>
                          <td className="num">{l.units}</td>
                          <td>
                            {l.soonestExpiry} · {daysLeft(l.daysLeft)} {l.urgent && <span className="flag">URGENT</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
            <div className="stack">
              <LogCollection {...props} centre={centre} />
              <DiscardUnit {...props} centre={centre} />
            </div>
          </div>
        </div>
      </section>

      <RiskSection {...props} facilityId={centre} />
    </>
  );
}

function IncomingTransfers({ rev, changed, notify, centre }: SectionProps & { centre: string }) {
  const { data: transfers } = useLoad(api.transfers, [rev]);
  const incoming = transfers?.filter((t) => t.destinationId === centre && t.status === "APPROVED") ?? [];
  const outgoing = transfers?.filter((t) => t.sourceId === centre && t.status === "APPROVED") ?? [];

  async function receive(id: string) {
    try {
      await api.receiveTransfer(id);
      notify(`Transfer ${id} received.`, true);
      changed();
    } catch (e) {
      notify(errorText(e));
    }
  }

  return (
    <section id="transfers" className="section red" aria-labelledby="transfers-title">
      <div className="wrap">
        <SectionHead
          id="transfers-title"
          title="Transfers"
          note="Approved by the network team to save units from expiring. Send the outgoing ones; confirm incoming ones when they arrive."
        />
        <div className="request-grid">
          <div className="panel on-light">
            <h3>Incoming</h3>
            {incoming.length === 0 && <div className="empty">Nothing on its way to you.</div>}
            {incoming.map((t) => (
              <div className="transfer compact" key={t.id}>
                <div className="route">
                  {t.sourceId}
                  <span className="arrow">→</span>
                  {t.destinationId}
                </div>
                <div>
                  <div className="units">{t.units}</div>
                  <p>
                    {t.id} · {t.distanceKm} km · approved {t.plannedOn}
                  </p>
                </div>
                <button className="btn small" type="button" onClick={() => receive(t.id)}>
                  Received
                </button>
              </div>
            ))}
          </div>
          <div className="panel dark">
            <h3 className="red-text">Outgoing</h3>
            {outgoing.length === 0 && <div className="empty">Nothing to send.</div>}
            {outgoing.map((t) => (
              <div className="transfer compact" key={t.id}>
                <div className="route">
                  {t.sourceId}
                  <span className="arrow">→</span>
                  {t.destinationId}
                </div>
                <div>
                  <div className="units">{t.units}</div>
                  <p>
                    {t.id} · {t.distanceKm} km · {t.travelMinutes} min
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function LogCollection({ changed, notify, centre, today }: SectionProps & { centre: string; today?: string }) {
  const [component, setComponent] = useState<Component>("RED_CELLS");
  const [group, setGroup] = useState("O+");
  const [quantity, setQuantity] = useState(1);
  const [collectedOn, setCollectedOn] = useState(today ?? "");

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      const r = await api.addUnits(centre, {
        component,
        group,
        quantity,
        ...(collectedOn ? { collectedOn } : {}),
      });
      notify(`Logged ${plural(r.unitIds.length, "unit")} (${r.unitIds[0]}…), expiring ${r.expiresOn}.`, true);
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  return (
    <form className="panel dark" onSubmit={submit}>
      <h3 className="red-text">Log collection</h3>
      <div className="form">
        <label>
          Component
          <select value={component} onChange={(e) => setComponent(e.target.value as Component)}>
            {COMPONENTS.map((c) => (
              <option key={c} value={c}>
                {componentLabel(c)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Group
          <select value={group} onChange={(e) => setGroup(e.target.value)}>
            {GROUPS.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
        <label>
          Units
          <input type="number" min={1} max={500} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
        </label>
        <label>
          Collected on
          <input type="date" value={collectedOn} max={today} onChange={(e) => setCollectedOn(e.target.value)} />
        </label>
        <div className="full">
          <button className="btn" type="submit">
            Add to stock
          </button>
        </div>
      </div>
    </form>
  );
}

function DiscardUnit({ changed, notify, centre }: SectionProps & { centre: string }) {
  const [unitId, setUnitId] = useState("");
  const [reason, setReason] = useState("");

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await api.discard(centre, unitId.trim(), reason);
      notify(`Unit ${unitId} discarded and recorded as wastage.`, true);
      setUnitId("");
      setReason("");
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  return (
    <form className="panel on-light" onSubmit={submit}>
      <h3>Discard a unit</h3>
      <div className="form">
        <label>
          Unit ID
          <input value={unitId} onChange={(e) => setUnitId(e.target.value)} placeholder="RS-000123" required />
        </label>
        <label>
          Reason
          <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Bag leaking" required />
        </label>
        <div className="full">
          <button className="btn dark" type="submit">
            Discard
          </button>
        </div>
      </div>
    </form>
  );
}
