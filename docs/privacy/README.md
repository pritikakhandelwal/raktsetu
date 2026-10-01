# Data protection: the DPDP Act, 2023

How RaktSetu meets India's Digital Personal Data Protection Act, 2023 (the Act) and the DPDP Rules, 2025. The
software does its part; the organisation that deploys it (the **Data Fiduciary**) must do the rest, listed in
section 5.

This is an engineering record, not legal advice. Have it reviewed by a lawyer before a real deployment, and check the
dates from which each duty applies, because the Rules bring them into force in phases.

## 1. What personal data is held

| Data | About | Where | Why | Lawful basis | Kept for |
|---|---|---|---|---|---|
| Name, username, role, facility | Staff | `app_user` | Sign-in; staff act only for their own facility | Employment (legitimate use, s. 7) | While the account is in use; erased on request when the person leaves |
| Requests to join (name, username, role, facility, password hash) | People asking for an account | `app_user` (`pending`) | So an administrator can approve or reject the request | Steps at the person's request (s. 7) | Until approved; deleted when rejected |
| Password hash (BCrypt) | Staff | `app_user` | Sign-in | Employment | As above |
| Mobile number | Staff, optional | `app_user` | Urgent SMS/WhatsApp alerts only | Consent (s. 6), or employment if an administrator adds it | Until alerts are turned off, which deletes it |
| Alert records (number, text, status) | Staff | `alert_outbox` | Delivery and retries; showing administrators failures | As for the number | 90 days (`raktsetu.privacy.alert-retention-days`), then deleted daily |
| Username on requests and transfers | Staff | `blood_request.raised_by`, `transfer.decided_by` | Accountability for blood movements | Employment; legal record-keeping | Permanently, as the network's record |
| Sign-in failures (username, address) | Anyone | Memory only | Lockout after repeated failures | Security (s. 8(5)) | 15 minutes; lost on restart |

**The facility directory holds no personal data.** The 8,912 blood centres and hospitals offered on the request form
come from the National Health Portal's open data (built by `tools/directory/build_directory.py`). The source lists
blood bank nodal officers' names, phone numbers and e-mail addresses; those are deliberately not copied.

**Patients are not identified.** A request holds only component, ABO/RhD group and quantity. The privacy notice tells
staff not to type patient details into any field. **Donors are not identified**: a unit carries only the donation
number printed on the bag, which only the collecting centre can link to a person.

## 2. What the software does

| Duty in the Act | How RaktSetu meets it | Code |
|---|---|---|
| Notice before or with consent (s. 5) | Privacy notice page, readable before sign-in and linked from the sign-in page, the footer and the alerts form | `PrivacyPage.tsx`, `GET /api/privacy` |
| Consent: free, specific, recorded, as easy to withdraw as to give (s. 6) | Alerts are off until the person turns them on next to a consent statement; the time and notice version are recorded. Turning alerts off deletes the number, including from past alert records | `AccountService.updateContact`, `V6__privacy.sql` |
| Purpose limitation and data minimisation (s. 4, s. 6) | The number is used only for alerts; no patient or donor identity is collected | Schema |
| Right of access (s. 11) | "Download my data" on the Account page: everything held about the person, as JSON | `GET /api/auth/my-data` |
| Correction (s. 12) | People change their own number; administrators correct names | Account and People pages |
| Erasure (s. 8(7), s. 12) | Administrators erase a former member of staff's name and number. The username stays because the hash-chained audit log refers to it and cannot be rewritten without breaking its integrity | `POST /api/users/{username}/anonymise` |
| Storage limitation (s. 8(7)) | Alert records deleted daily after the retention period | `PrivacyService.purgeOldAlerts` |
| Reasonable security safeguards (s. 8(5)) | See [../security/review.md](../security/review.md) | |
| Contact for questions and grievances (s. 8(9), s. 13) | `raktsetu.privacy.organisation` and `raktsetu.privacy.contact`, shown in the notice | `PrivacyProperties` |

## 3. Who else receives data (Data Processors)

| Processor | Receives | When |
|---|---|---|
| SMS provider (MSG91 or Twilio) | Mobile number and alert text | Only when SMS alerts are configured and the person has alerts on |
| Meta (WhatsApp Cloud API) | Mobile number and alert text | Only when WhatsApp alerts are configured |
| Hosting provider and database host | Everything in the database | Always, as infrastructure |
| OpenStreetMap tile servers | The map area being viewed and the viewer's IP address | When someone opens a map |

The deploying organisation needs a contract with each processor (s. 8(2)). Alerts contain no patient data.

## 4. Personal data breaches

Under s. 8(6) and the Rules, a breach must be reported to the **Data Protection Board of India** and to **each
affected person**. As currently notified, the Rules require a detailed report to the Board within 72 hours of
becoming aware of it.

1. **Contain:** take the server offline or revoke the exposed credentials (database password, provider keys).
2. **Assess:** what data, whose, since when. Check the audit log's integrity (`/actuator/health` reports DOWN if it
   was altered) and the server and database access logs.
3. **Notify** the Board and the affected staff: what happened, the likely consequences, what was done, and what
   they should do (for example, change their password).
4. **Fix and record:** the cause, the fix, and the timeline, kept with the incident report.

## 5. What the deploying organisation must do

- [ ] Set `raktsetu.privacy.organisation` and `raktsetu.privacy.contact` (a grievance contact that is answered).
- [ ] Sign data processing contracts with the hosting, SMS and WhatsApp providers.
- [ ] Have the privacy notice reviewed by a lawyer, and translated if staff need it in another language (s. 5(3)).
- [ ] Decide how long staff accounts are kept after someone leaves, and erase them on schedule.
- [ ] Keep server and database access logs for at least one year, as the Rules require, and back up the database.
- [ ] Name the person responsible for handling breaches, and rehearse section 4.
- [ ] Re-run this review whenever new personal data is collected.
