import { useState, type FormEvent } from "react";
import { api, COMPONENTS, GROUPS, type BloodRequest, type Compatibility, type Component } from "../api";
import { componentLabel, daysLeft, plural } from "../format";
import { SectionHead, errorText, useLoad, type SectionProps } from "../shared";

/** The request form. Hospital staff always request for their own hospital ({@code fixedHospital}). */
export function RequestSection({ rev, changed, notify, fixedHospital }: SectionProps & { fixedHospital?: string }) {
  const { data: facilities } = useLoad(api.facilities, [rev]);
  const hospitals = facilities?.filter((f) => f.type === "HOSPITAL") ?? [];

  const [hospitalId, setHospitalId] = useState(fixedHospital ?? "H01");
  const [component, setComponent] = useState<Component>("RED_CELLS");
  const [group, setGroup] = useState("O-");
  const [quantity, setQuantity] = useState(2);
  const [urgency, setUrgency] = useState("EMERGENCY");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<BloodRequest>();
  const [error, setError] = useState("");

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setResult(undefined);
    try {
      const r = await api.request({ hospitalId, component, group, quantity, urgency });
      setResult(r);
      notify(`Request #${r.id}: ${plural(r.quantity, "unit")} reserved; the supplying centre has been told.`, true);
      changed();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section id="request" className="section red" aria-labelledby="request-title">
      <div className="wrap">
        <SectionHead
          id="request-title"
          title="Emergency request"
          note="Searches centres within 90 minutes, nearest first. Units closest to expiry go first; O- is kept for patients who need it."
        />
        <div className="request-grid">
          <form className="panel on-light" onSubmit={submit} aria-describedby="request-help">
            <h3>Blood needed</h3>
            <div className="form">
              <label>
                Hospital
                <select
                  value={hospitalId}
                  onChange={(e) => setHospitalId(e.target.value)}
                  disabled={fixedHospital !== undefined}
                >
                  {hospitals.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.id} · {h.name.replace(" (sample)", "")}
                    </option>
                  ))}
                </select>
              </label>
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
                Patient's blood group
                <select value={group} onChange={(e) => setGroup(e.target.value)}>
                  {GROUPS.map((g) => (
                    <option key={g}>{g}</option>
                  ))}
                </select>
              </label>
              <label>
                Units
                <input
                  type="number"
                  min={1}
                  max={50}
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                  required
                />
              </label>
              <div className="full">
                <span className="visually-hidden" id="urgency-label">
                  Urgency
                </span>
                <div className="segmented" role="group" aria-labelledby="urgency-label">
                  {[
                    ["EMERGENCY", "Emergency: nearest first"],
                    ["ROUTINE", "Routine: exact group first"],
                  ].map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      aria-pressed={urgency === value}
                      onClick={() => setUrgency(value!)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="full">
                <button className="btn dark" type="submit" disabled={busy}>
                  {busy ? "Searching…" : "Find and issue units"}
                </button>
              </div>
            </div>
            <p id="request-help" className="visually-hidden">
              All or nothing: if the network cannot meet the whole request, nothing is issued.
            </p>
            {error && (
              <p className="message error" role="alert">
                {error}
              </p>
            )}
            {result && (
              <div className="result" aria-live="polite">
                {result.parts.map((p) => (
                  <div className="alloc" key={p.sourceId}>
                    <span className="count">{p.unitIds.length}</span>
                    <strong>
                      {p.donorGroups.join(", ")} from {p.sourceId}{" "}
                      <small>
                        · {p.distanceKm} km · {p.travelMinutes} min
                      </small>
                    </strong>
                    <small>
                      {p.unitIds.join(", ")} · {p.daysLeft.map((d) => daysLeft(d)).join(", ")}
                    </small>
                  </div>
                ))}
                {result.note && <p className="sub">{result.note}</p>}
              </div>
            )}
          </form>
          <CompatibilityChecker />
        </div>
      </div>
    </section>
  );
}

function CompatibilityChecker() {
  const [component, setComponent] = useState<Component>("PLASMA");
  const [donor, setDonor] = useState("AB+");
  const [recipient, setRecipient] = useState("O-");
  const [result, setResult] = useState<Compatibility>();
  const [error, setError] = useState("");

  async function check(e: FormEvent) {
    e.preventDefault();
    setError("");
    try {
      setResult(await api.compatibility({ component, donor, recipient }));
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <form className="panel dark" onSubmit={check}>
      <h3 className="red-text">Is it safe?</h3>
      <div className="form">
        <label className="full">
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
          Donor
          <select value={donor} onChange={(e) => setDonor(e.target.value)}>
            {GROUPS.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
        <label>
          Recipient
          <select value={recipient} onChange={(e) => setRecipient(e.target.value)}>
            {GROUPS.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
        <div className="full">
          <button className="btn" type="submit">
            Check
          </button>
        </div>
      </div>
      {error && <p className="message error">{error}</p>}
      {result && (
        <div aria-live="polite">
          <p className={`verdict ${result.allowed ? "yes" : "no"}`}>{result.allowed ? "Allowed" : "Not allowed"}</p>
          <p>{result.reason}</p>
        </div>
      )}
    </form>
  );
}
