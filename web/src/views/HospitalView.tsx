import { api, type User } from "../api";
import { place } from "../format";
import { RequestBoard } from "../components/RequestBoard";
import { RequestSection } from "../sections/RequestSection";
import { SectionHead, useLoad, type SectionProps } from "../shared";

/** What hospital staff see: request blood for their hospital and follow each request until it arrives. */
export function HospitalView(props: SectionProps & { user: User }) {
  const { user, rev } = props;
  const hospital = user.facilityId!;
  const { data: facilities } = useLoad(api.facilities, [rev]);
  const { data: requests } = useLoad(api.requests, [rev]);
  const { data: stock } = useLoad(api.networkStock, [rev]);
  const me = facilities?.find((f) => f.id === hospital);
  const open = requests?.filter((r) => r.status === "ALLOCATED" || r.status === "DISPATCHED").length ?? 0;
  const onTheWay = requests?.filter((r) => r.status === "DISPATCHED").length ?? 0;

  return (
    <>
      <section id="top" className="section hero role-hero" aria-labelledby="hospital-title">
        <div className="glow" style={{ width: 360, height: 360, left: "-120px", top: "60px" }} />
        <div className="wrap hero-grid">
          <div className="hero-copy">
            <p className="kicker">{hospital} · Hospital</p>
            <h1 id="hospital-title">
              {me ? place(me.name) || me.name : hospital}
              <span>requests.</span>
            </h1>
            <p className="lede">
              Ask for blood and RaktSetu reserves the <strong>closest compatible units</strong>, soonest-expiring first,
              and tells the centre to send them.
            </p>
            <div className="actions">
              <a className="btn" href="#request">
                Request blood
              </a>
              <a className="btn ghost" href="#my-requests">
                My requests
              </a>
            </div>
          </div>
          <div className="hero-panel" aria-label="Your requests">
            <div className="stat">
              <strong>{open}</strong>
              <span>Open requests</span>
            </div>
            <div className="stat">
              <strong>{onTheWay}</strong>
              <span>On their way</span>
            </div>
          </div>
        </div>
      </section>

      <RequestSection {...props} fixedHospital={hospital} />

      <section id="my-requests" className="section cream on-light" aria-labelledby="mine-title">
        <div className="wrap">
          <SectionHead
            id="mine-title"
            title="My requests"
            red
            note="Newest first. Cancel before dispatch and the units go back on the shelf; confirm receipt when they arrive."
          />
          <RequestBoard {...props} />
        </div>
      </section>

      {stock && (
        <section id="availability" className="section" aria-labelledby="availability-title">
          <div className="wrap">
            <SectionHead
              id="availability-title"
              title="Availability"
              red
              note="Usable units across the whole network today. Your request is served from centres within 90 minutes."
            />
            <div className="panel on-light table-scroll">
              <table>
                <caption className="visually-hidden">Network stock by component and blood group</caption>
                <thead>
                  <tr>
                    <th>Component</th>
                    {stock.groups.map((g) => (
                      <th key={g} className="num">
                        {g}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {stock.rows.map((r) => (
                    <tr key={r.component}>
                      <td>{r.component.replace("_", " ").toLowerCase()}</td>
                      {r.counts.map((n, i) => (
                        <td key={i} className="num">
                          {n}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      )}
    </>
  );
}
