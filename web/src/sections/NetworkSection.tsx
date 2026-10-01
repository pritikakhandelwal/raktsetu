import { useState } from "react";
import { api } from "../api";
import { badge, componentLabel, daysLeft, place, plural, shortName } from "../format";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";

export function NetworkSection({ rev, changed, notify }: SectionProps) {
  const { data: facilities, error } = useLoad(api.facilities, [rev]);
  const [chosen, setChosen] = useState<string>();
  const centres = facilities?.filter((f) => f.type !== "HOSPITAL") ?? [];
  const selected = chosen ?? centres[0]?.id;
  const setSelected = setChosen;
  const { data: stock } = useLoad(
    () => (selected ? api.facilityStock(selected) : Promise.resolve(undefined)),
    [selected, rev],
  );

  const hospitals = facilities?.filter((f) => f.type === "HOSPITAL") ?? [];

  async function recordReport(id: string) {
    try {
      await api.recordReport(id);
      notify(`${id} reported its stock today; its data is now trusted.`, true);
      changed();
    } catch (e) {
      notify(errorText(e));
    }
  }

  return (
    <section id="network" className="section" aria-labelledby="network-title">
      <div className="glow" style={{ width: 300, height: 300, right: "-80px", top: "40px" }} />
      <div className="wrap">
        <SectionHead
          id="network-title"
          title="The network"
          red
          note="Pick a centre to see its shelves. Centres that have not reported their stock for over three days are treated as unverified."
        />
        {error && <p className="message error">{error}</p>}
        {facilities && centres.length === 0 && (
          <div className="empty">No blood centres yet. Add them on the Facilities page.</div>
        )}
        <div className="people">
          {centres.map((f) => (
            <button
              key={f.id}
              type="button"
              className="person"
              aria-pressed={selected === f.id}
              onClick={() => setSelected(f.id)}
            >
              <span className="avatar" aria-hidden="true">
                <span className="face">{badge(f.id)}</span>
              </span>
              <span className="name">{place(f.name) || f.name}</span>
              <span className="role">
                {f.id} · {f.type === "HUB" ? "Hub" : "Storage"}
              </span>
              <span className={`chip ${f.freshness}`}>{f.freshness.toLowerCase()}</span>
              <span className="meta">
                {plural(f.units, "unit")} · {f.atRiskUnits} at risk
              </span>
            </button>
          ))}
        </div>
        <div className="hospitals">
          <span>Hospitals served:</span>
          {hospitals.map((h) => (
            <span className="chip" key={h.id}>
              {h.id} {place(h.name)}
            </span>
          ))}
        </div>

        {stock && (
          <div className="panel on-light">
            <div className="detail-head">
              <div>
                <h3>
                  {place(stock.facility.name) || stock.facility.name} <span className="red-text">{stock.facility.id}</span>
                </h3>
                <p style={{ color: "var(--muted-light)", fontSize: 13 }}>
                  {shortName(stock.facility.name)} · last stock report {stock.facility.lastReportedOn ?? "never"}{" "}
                  <span className={`chip ${stock.facility.freshness}`}>{stock.facility.freshness.toLowerCase()}</span>
                </p>
              </div>
              {stock.facility.freshness !== "FRESH" && (
                <button className="btn dark small" type="button" onClick={() => recordReport(stock.facility.id)}>
                  Record today's stock report
                </button>
              )}
            </div>
            {stock.lines.length === 0 ? (
              <div className="empty">No stock.</div>
            ) : (
              <div className="table-scroll">
                <table>
                  <caption className="visually-hidden">Stock at {stock.facility.id}</caption>
                  <thead>
                    <tr>
                      <th>Component</th>
                      <th>Group</th>
                      <th className="num">Units</th>
                      <th>Soonest expiry</th>
                      <th />
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
                          {l.soonestExpiry} · {daysLeft(l.daysLeft)}
                        </td>
                        <td>{l.urgent && <span className="flag">URGENT</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
