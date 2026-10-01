import { api } from "../api";
import { Link } from "../router";
import { useLoad, type SectionProps } from "../shared";

/**
 * Administrators' checklist until the network is ready: facilities, hospitals, staff accounts and first stock.
 * Hidden once everything is done.
 */
export function GettingStarted({ rev }: SectionProps) {
  const { data: facilities } = useLoad(api.facilities, [rev]);
  const { data: people } = useLoad(api.users, [rev]);
  if (!facilities || !people) return null;

  const centres = facilities.filter((f) => f.type !== "HOSPITAL");
  const hospitals = facilities.filter((f) => f.type === "HOSPITAL");
  const staff = people.filter((p) => p.role !== "ADMIN");
  const steps = [
    {
      done: centres.length > 0,
      title: "Add your blood centres",
      text: "Your hub blood centre and any storage centres, with their locations.",
      to: "/facilities",
      action: "Go to Facilities",
    },
    {
      done: hospitals.length > 0,
      title: "Add the hospitals you supply",
      text: "Routes to every centre are worked out automatically.",
      to: "/facilities",
      action: "Go to Facilities",
    },
    {
      done: staff.length > 0,
      title: "Create accounts for staff",
      text: "Each person gets a one-time password and chooses their own.",
      to: "/people",
      action: "Go to People",
    },
    {
      done: centres.some((c) => c.units > 0),
      title: "Record stock",
      text: "Centre staff log their units and report stock each day.",
      to: "/people",
      action: "Give centres their accounts",
    },
  ];
  if (steps.every((s) => s.done)) return null;
  const next = steps.findIndex((s) => !s.done);

  return (
    <section className="section cream on-light" aria-labelledby="start-title">
      <div className="wrap">
        <div className="section-head">
          <h2 id="start-title">Getting started</h2>
          <p>
            {steps.filter((s) => s.done).length} of {steps.length} done. RaktSetu becomes useful as soon as centres
            record their stock.
          </p>
        </div>
        <ol className="checklist">
          {steps.map((s, i) => (
            <li key={s.title} className={s.done ? "done" : i === next ? "next" : undefined}>
              <span className="check" aria-hidden="true">
                {s.done ? "✓" : i + 1}
              </span>
              <div>
                <strong>{s.title}</strong>
                <p>{s.text}</p>
              </div>
              {!s.done && i === next && (
                <Link to={s.to} className="btn dark small">
                  {s.action}
                </Link>
              )}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
