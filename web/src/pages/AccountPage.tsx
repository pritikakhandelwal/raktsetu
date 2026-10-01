import type { User } from "../api";
import { ChangePasswordForm } from "./ChangePassword";
import { SectionHead, type SectionProps } from "../shared";

const ROLE_LABELS: Record<User["role"], string> = {
  ADMIN: "Network administrator",
  CENTRE_STAFF: "Blood centre staff",
  HOSPITAL_STAFF: "Hospital staff",
};

/** Your own account: who you are and changing your password. */
export function AccountPage({ user, notify }: SectionProps & { user: User }) {
  return (
    <section className="section cream on-light page" aria-labelledby="account-title">
      <div className="wrap">
        <SectionHead id="account-title" title="Your account" red />
        <div className="request-grid">
          <div className="panel dark">
            <h3 className="red-text">{user.displayName}</h3>
            <dl className="details">
              <dt>Username</dt>
              <dd>{user.username}</dd>
              <dt>Role</dt>
              <dd>{ROLE_LABELS[user.role]}</dd>
              <dt>Works at</dt>
              <dd>{user.facilityId ?? "The whole network"}</dd>
            </dl>
            <p className="login-help" style={{ color: "var(--muted-dark)" }}>
              To change your name or role, ask a network administrator.
            </p>
          </div>
          <ChangePasswordForm onDone={() => notify("Your password has been changed.", true)} />
        </div>
      </div>
    </section>
  );
}
