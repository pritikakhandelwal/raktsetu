import { api, type PrivacyNoticeFacts } from "../api";
import { SectionHead, useLoad } from "../shared";
import { Link } from "../router";

/**
 * The privacy notice required by India's Digital Personal Data Protection Act, 2023: what RaktSetu holds, why, who
 * else receives it, and how people use their rights. Readable before signing in.
 */
export function PrivacyPage({ signedIn }: { signedIn: boolean }) {
  const { data: facts, error } = useLoad<PrivacyNoticeFacts>(api.privacy, []);

  return (
    <section className="section cream on-light page privacy" aria-labelledby="privacy-title">
      <div className="wrap narrow">
        <SectionHead
          id="privacy-title"
          title="Privacy notice"
          note={facts ? `Version ${facts.noticeVersion}` : error || "Loading…"}
          red
        />
        {facts && <Notice facts={facts} signedIn={signedIn} />}
        {!signedIn && (
          <p>
            <Link to="/" className="btn dark small">
              Back to sign in
            </Link>
          </p>
        )}
      </div>
    </section>
  );
}

function Notice({ facts, signedIn }: { facts: PrivacyNoticeFacts; signedIn: boolean }) {
  const contact = facts.contact ?? "your network administrator";
  return (
    <div className="panel on-light prose">
      <h3>Who is responsible</h3>
      <p>
        {facts.organisation ?? "The organisation that runs this RaktSetu network"} decides how its data is used. For
        any question or complaint about your data, contact {contact}.
      </p>

      <h3>What RaktSetu holds about you</h3>
      <ul>
        <li>Your name, username, role and the facility you work at.</li>
        <li>Your password, stored only as a one-way hash that nobody can read.</li>
        <li>If you choose, a mobile number for urgent alerts.</li>
        <li>
          Your username on the requests you raise and the transfers you approve or reject, as the network's record of
          blood movements.
        </li>
        <li>
          A record of each alert sent to you, deleted after {facts.alertRetentionDays} days.
        </li>
      </ul>

      <h3>About patients</h3>
      <p>
        RaktSetu never identifies patients. A blood request holds only the component, the blood group and the number
        of units. Do not type patient names, hospital numbers or other details into any field.
      </p>

      <h3>Why it is used</h3>
      <ul>
        <li>Your account details: to let you sign in and act only for your own facility.</li>
        <li>
          Your mobile number: only to send urgent alerts. You agree to this when you turn alerts on, or your employer
          adds it for work alerts when creating your account.
        </li>
      </ul>

      <h3>Who else receives it</h3>
      <p>
        When alerts are on, your number and the alert text go to the SMS or WhatsApp provider that delivers them. No
        other data leaves RaktSetu, and nothing is sold or used for advertising.
      </p>

      <h3>Your rights</h3>
      <ul>
        <li>
          <strong>Get a copy</strong> of everything held about you:{" "}
          {signedIn ? <Link to="/account">Your account → Download my data</Link> : "Your account → Download my data"}.
        </li>
        <li>
          <strong>Correct it:</strong> change your mobile number yourself; ask an administrator to correct your name.
        </li>
        <li>
          <strong>Withdraw consent:</strong> turn urgent alerts off and your number is deleted, including from the record
          of past alerts.
        </li>
        <li>
          <strong>Erasure when you leave:</strong> an administrator erases your name and number. Your username stays on
          past records, because the tamper-evident log of blood movements cannot be rewritten.
        </li>
        <li>
          <strong>Complain:</strong> contact {contact}. If you are not satisfied with the answer, you may complain to the
          Data Protection Board of India.
        </li>
      </ul>
    </div>
  );
}
