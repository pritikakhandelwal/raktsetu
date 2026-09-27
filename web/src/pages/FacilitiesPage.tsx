import { useEffect, useState, type FormEvent } from "react";
import { api, type Facility, type FacilityType, type RouteInfo } from "../api";
import { MapPicker, type MapPoint } from "../components/MapPicker";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";

const TYPE_LABELS: Record<FacilityType, string> = {
  HUB: "Hub blood centre",
  STORAGE: "Blood storage centre",
  HOSPITAL: "Hospital",
};

const SOURCE_LABELS: Record<RouteInfo["source"], string> = {
  OSRM: "road route",
  ESTIMATE: "estimate",
  MANUAL: "set by hand",
};

function points(facilities: Facility[] | undefined, except?: string): MapPoint[] {
  return (facilities ?? [])
    .filter((f) => f.latitude != null && f.longitude != null && f.id !== except)
    .map((f) => ({ id: f.id, label: f.name, latitude: f.latitude!, longitude: f.longitude!, kind: f.type }));
}

/** Administrators: the network's facilities on a map, adding and editing them, and correcting routes. */
export function FacilitiesPage(props: SectionProps) {
  const { rev, changed, notify } = props;
  const { data: facilities, error } = useLoad(api.facilities, [rev]);
  const [mode, setMode] = useState<"list" | "add" | string>("list");
  const editing = facilities?.find((f) => f.id === mode);

  return (
    <section className="section cream on-light page" aria-labelledby="facilities-title">
      <div className="wrap">
        <SectionHead
          id="facilities-title"
          title="Facilities"
          red
          note="The blood centres and hospitals in your network. Routes between them are worked out from their locations."
        />
        {error && <p className="message error">{error}</p>}
        <div className="actions" style={{ marginBottom: 20 }}>
          {mode === "list" ? (
            <button className="btn dark" type="button" onClick={() => setMode("add")}>
              Add a facility
            </button>
          ) : (
            <button className="btn ghost" type="button" onClick={() => setMode("list")}>
              Back to all facilities
            </button>
          )}
        </div>

        {mode === "add" && (
          <FacilityForm
            facilities={facilities}
            onSaved={(f, others) => {
              notify(
                `Added ${f.name} (${f.id})` + (others ? `; routes worked out to ${others} other facilities.` : "."),
                true,
              );
              changed();
              setMode(f.id);
            }}
            notify={notify}
          />
        )}

        {editing && (
          <>
            <FacilityForm
              key={editing.id}
              facility={editing}
              facilities={facilities}
              onSaved={(f) => {
                notify(`Saved ${f.name}.`, true);
                changed();
              }}
              notify={notify}
            />
            <Routes facility={editing} rev={rev} changed={changed} notify={notify} />
          </>
        )}

        {mode === "list" && facilities && (
          <>
            {facilities.length === 0 ? (
              <div className="empty">
                No facilities yet. Start with your hub blood centre, then add storage centres and the hospitals you
                supply.
              </div>
            ) : (
              <>
                <MapPicker points={points(facilities)} height={380} />
                <div className="table-scroll panel on-light" style={{ marginTop: 20 }}>
                  <table>
                    <caption className="visually-hidden">Facilities</caption>
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Name</th>
                        <th>Type</th>
                        <th>Address</th>
                        <th className="num">Units</th>
                        <th>Stock report</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {facilities.map((f) => (
                        <tr key={f.id}>
                          <td>
                            <strong>{f.id}</strong>
                          </td>
                          <td>{f.name}</td>
                          <td>{TYPE_LABELS[f.type]}</td>
                          <td>{f.address || "–"}</td>
                          <td className="num">{f.type === "HOSPITAL" ? "–" : f.units}</td>
                          <td>
                            {f.type === "HOSPITAL" ? (
                              "–"
                            ) : (
                              <span className={`chip ${f.freshness}`}>
                                {f.lastReportedOn ? f.freshness.toLowerCase() : "never reported"}
                              </span>
                            )}
                          </td>
                          <td>
                            <button className="btn ghost small" type="button" onClick={() => setMode(f.id)}>
                              Edit
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </>
        )}
      </div>
    </section>
  );
}

function FacilityForm({
  facility,
  facilities,
  onSaved,
  notify,
}: {
  facility?: Facility;
  facilities: Facility[] | undefined;
  onSaved: (f: Facility, otherCount: number) => void;
  notify: (m: string) => void;
}) {
  const [name, setName] = useState(facility?.name ?? "");
  const [type, setType] = useState<FacilityType>(facility?.type ?? "STORAGE");
  const [address, setAddress] = useState(facility?.address ?? "");
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(
    facility?.latitude != null && facility.longitude != null
      ? { latitude: facility.latitude, longitude: facility.longitude }
      : null,
  );
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!location) {
      notify("Click the map to set the location");
      return;
    }
    setBusy(true);
    try {
      const body = { name, type, address, latitude: location.latitude, longitude: location.longitude };
      const saved = facility ? await api.updateFacility(facility.id, body) : await api.addFacility(body);
      onSaved(saved, points(facilities).length);
    } catch (err) {
      notify(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel on-light facility-form" onSubmit={submit}>
      <h3>{facility ? `${facility.id} · ${facility.name}` : "New facility"}</h3>
      <div className="facility-grid">
        <div className="form">
          <label className="full">
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="e.g. City Blood Centre" />
          </label>
          <label className="full">
            Type
            <select value={type} onChange={(e) => setType(e.target.value as FacilityType)} disabled={!!facility}>
              {(Object.keys(TYPE_LABELS) as FacilityType[]).map((t) => (
                <option key={t} value={t}>
                  {TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </label>
          <label className="full">
            Address (optional)
            <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Street, town" />
          </label>
          <label>
            Latitude
            <input
              type="number"
              step="any"
              value={location?.latitude ?? ""}
              onChange={(e) => setLocation({ latitude: Number(e.target.value), longitude: location?.longitude ?? 0 })}
              required
            />
          </label>
          <label>
            Longitude
            <input
              type="number"
              step="any"
              value={location?.longitude ?? ""}
              onChange={(e) => setLocation({ latitude: location?.latitude ?? 0, longitude: Number(e.target.value) })}
              required
            />
          </label>
          <div className="full">
            <button className="btn dark" type="submit" disabled={busy}>
              {busy ? "Saving and working out routes…" : facility ? "Save changes" : "Add facility"}
            </button>
          </div>
          {facility && (
            <p className="login-help full">Moving a facility works out its routes again, replacing hand-set ones.</p>
          )}
        </div>
        <div>
          <p className="login-help">Click the map where the facility is.</p>
          <MapPicker
            points={points(facilities, facility?.id)}
            picked={location}
            onPick={(latitude, longitude) => setLocation({ latitude, longitude })}
          />
        </div>
      </div>
    </form>
  );
}

function Routes({ facility, rev, changed, notify }: SectionProps & { facility: Facility }) {
  const { data: routes } = useLoad(() => api.routes(facility.id), [facility.id, rev]);
  return (
    <div className="panel on-light" style={{ marginTop: 20 }}>
      <h3>Routes from {facility.id}</h3>
      <p className="login-help" style={{ marginBottom: 12 }}>
        Used to find the nearest stock and to plan transfers. Emergency requests only use centres within 90 minutes.
        Correct any route that is wrong; changes apply in both directions.
      </p>
      {!routes || routes.length === 0 ? (
        <div className="empty">No other facilities yet.</div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>To</th>
                <th className="num">Distance (km)</th>
                <th className="num">Travel (min)</th>
                <th>Source</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {routes.map((r) => (
                <RouteRow key={r.toId} from={facility.id} route={r} changed={changed} notify={notify} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function RouteRow({
  from,
  route,
  changed,
  notify,
}: {
  from: string;
  route: RouteInfo;
  changed: () => void;
  notify: (m: string, ok?: boolean) => void;
}) {
  const [km, setKm] = useState(route.distanceKm);
  const [minutes, setMinutes] = useState(route.travelMinutes);
  useEffect(() => {
    setKm(route.distanceKm);
    setMinutes(route.travelMinutes);
  }, [route.distanceKm, route.travelMinutes]);
  const dirty = km !== route.distanceKm || minutes !== route.travelMinutes;

  async function save() {
    try {
      await api.setRoute(from, route.toId, km, minutes);
      notify(`Route ${from} ↔ ${route.toId} saved.`, true);
      changed();
    } catch (err) {
      notify(errorText(err));
    }
  }

  return (
    <tr>
      <td>
        <strong>{route.toId}</strong> {route.toName}
      </td>
      <td className="num">
        <input className="cell-input" type="number" min={1} value={km} onChange={(e) => setKm(Number(e.target.value))} />
      </td>
      <td className="num">
        <input
          className="cell-input"
          type="number"
          min={1}
          value={minutes}
          onChange={(e) => setMinutes(Number(e.target.value))}
        />
      </td>
      <td>
        <span className={`chip ${route.source === "OSRM" ? "ok" : route.source === "MANUAL" ? "STALE" : ""}`}>
          {SOURCE_LABELS[route.source]}
        </span>
      </td>
      <td>
        {dirty && (
          <button className="btn small" type="button" onClick={save}>
            Save
          </button>
        )}
      </td>
    </tr>
  );
}
