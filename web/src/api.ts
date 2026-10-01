// Typed client for the RaktSetu REST API (raktsetu-server). Shapes mirror ApiModels.java.

export type Freshness = "FRESH" | "STALE" | "UNVERIFIED";
export type Component = "WHOLE_BLOOD" | "RED_CELLS" | "PLATELETS" | "PLASMA";

export const COMPONENTS: Component[] = ["WHOLE_BLOOD", "RED_CELLS", "PLATELETS", "PLASMA"];
export const GROUPS = ["O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+"] as const;

export interface Summary {
  today: string;
  storingFacilities: number;
  hospitals: number;
  unitsInStock: number;
  urgentUnits: number;
  atRiskUnits: number;
  unitsWasted: number;
  pendingTransfers: number;
  auditIntact: boolean;
}

export interface Facility {
  id: string;
  name: string;
  type: "HUB" | "STORAGE" | "HOSPITAL";
  lastReportedOn: string | null;
  daysSinceReport: number | null;
  freshness: Freshness;
  units: number;
  urgentUnits: number;
  atRiskUnits: number;
  latitude: number | null;
  longitude: number | null;
  address: string | null;
}

export type FacilityType = Facility["type"];

export interface RouteInfo {
  toId: string;
  toName: string;
  distanceKm: number;
  travelMinutes: number;
  source: "OSRM" | "ESTIMATE" | "MANUAL";
}

export interface StockLine {
  component: Component;
  group: string;
  units: number;
  soonestExpiry: string;
  daysLeft: number;
  urgent: boolean;
}

export interface FacilityStock {
  facility: Facility;
  lines: StockLine[];
}

export interface NetworkStock {
  groups: string[];
  rows: { component: Component; counts: number[]; total: number }[];
  low: string[];
}

/** The units one centre supplies for a request. */
export interface RequestPart {
  sourceId: string;
  sourceName: string;
  distanceKm: number;
  travelMinutes: number;
  donorGroups: string[];
  unitIds: string[];
  daysLeft: number[];
  dispatched: boolean;
}

export type RequestStatus = "ALLOCATED" | "DISPATCHED" | "RECEIVED" | "CANCELLED" | "UNMET";

export interface BloodRequest {
  id: number;
  hospitalId: string;
  component: Component;
  group: string;
  quantity: number;
  urgency: "EMERGENCY" | "ROUTINE";
  status: RequestStatus;
  raisedOn: string;
  raisedBy: string;
  updatedOn: string;
  note: string;
  parts: RequestPart[];
}

export interface Notification {
  id: number;
  createdOn: string;
  kind: string;
  message: string;
  read: boolean;
}

export interface NotificationList {
  unread: number;
  items: Notification[];
}

export interface Compatibility {
  allowed: boolean;
  level: string;
  reason: string;
}

export interface Risk {
  facilityId: string;
  facilityName: string;
  component: Component;
  group: string;
  atRiskUnits: number;
  usableStock: number;
  soonestDaysLeft: number;
  averageDaily: number;
  reason: string;
}

export interface Strategy {
  name: string;
  description: string;
}

export interface Comparison {
  strategy: string;
  transfers: number;
  unitsRescued: number;
  stillAtRisk: number;
  totalKm: number;
}

export interface Transfer {
  id: string;
  sourceId: string;
  destinationId: string;
  units: string;
  unitCount: number;
  distanceKm: number;
  travelMinutes: number;
  status: "PROPOSED" | "APPROVED" | "RECEIVED" | "REJECTED" | "SUPERSEDED";
  reason: string;
  plannedOn: string;
  decidedBy: string | null;
  receivedOn: string | null;
}

export interface AuditEntry {
  sequence: number;
  date: string;
  type: "COLLECTED" | "ISSUED" | "TRANSFERRED" | "DISCARDED";
  facilityId: string;
  counterparty: string;
  component: Component;
  group: string;
  units: number;
  note: string;
  hash: string;
}

export interface AuditView {
  total: number;
  intact: boolean;
  message: string;
  entries: AuditEntry[];
}

/** An error returned by the API, with a message written for staff. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code = "",
  ) {
    super(message);
  }
}

export type Role = "ADMIN" | "CENTRE_STAFF" | "HOSPITAL_STAFF";

export interface User {
  username: string;
  displayName: string;
  role: Role;
  facilityId: string | null;
  mustChangePassword: boolean;
  phone: string | null;
  alertsEnabled: boolean;
}

export interface Account {
  username: string;
  displayName: string;
  role: Role;
  facilityId: string | null;
  enabled: boolean;
  phone: string | null;
  alertsEnabled: boolean;
  mustChangePassword: boolean;
  createdOn: string | null;
  anonymisedOn: string | null;
}

/** The facts in the privacy notice, from the server's settings. */
export interface PrivacyNoticeFacts {
  organisation: string | null;
  contact: string | null;
  noticeVersion: string;
  alertRetentionDays: number;
}

export type AlertStatus = "PENDING" | "SENT" | "FAILED";

export interface AlertsView {
  channels: { name: string; configured: boolean; active: boolean }[];
  counts: Record<AlertStatus, number>;
  recent: {
    id: number;
    username: string;
    phone: string;
    channel: string;
    message: string;
    status: AlertStatus;
    attempts: number;
    lastError: string | null;
    createdAt: string;
    sentAt: string | null;
  }[];
}

export interface NewAccount {
  user: Account;
  oneTimePassword: string;
}

/** The CSRF token Spring Security puts in the XSRF-TOKEN cookie; it must be echoed on every change. */
function csrfToken(): string {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]!) : "";
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? "GET";
  if (method !== "GET" && !csrfToken()) {
    // Signing out clears the token cookie; any request makes the server issue a fresh one.
    await fetch("/api/auth/me", { credentials: "same-origin" });
  }
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(method === "GET" ? {} : { "X-XSRF-TOKEN": csrfToken() }),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    let code = "";
    try {
      const body = (await response.json()) as { message?: string; error?: string };
      if (body.message) message = body.message;
      if (body.error) code = body.error;
    } catch {
      // keep the generic message
    }
    throw new ApiError(response.status, message, code);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const post = <T>(path: string, body?: unknown) =>
  call<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export interface FacilityInput {
  name: string;
  type: FacilityType;
  latitude: number;
  longitude: number;
  address?: string;
}

export const api = {
  me: () => call<User>("/auth/me"),
  login: (username: string, password: string) => post<User>("/auth/login", { username, password }),
  logout: () => post<void>("/auth/logout"),
  summary: () => call<Summary>("/summary"),
  facilities: () => call<Facility[]>("/facilities"),
  networkStock: () => call<NetworkStock>("/stock"),
  facilityStock: (id: string) => call<FacilityStock>(`/facilities/${encodeURIComponent(id)}/stock`),
  recordReport: (id: string) => post<Facility>(`/facilities/${encodeURIComponent(id)}/report`),
  request: (body: { hospitalId: string; component: Component; group: string; quantity: number; urgency: string }) =>
    post<BloodRequest>("/requests", body),
  requests: () => call<BloodRequest[]>("/requests"),
  dispatch: (id: number) => post<BloodRequest>(`/requests/${id}/dispatch`),
  receive: (id: number) => post<BloodRequest>(`/requests/${id}/receive`),
  cancel: (id: number) => post<BloodRequest>(`/requests/${id}/cancel`),
  receiveTransfer: (id: string) => post<Transfer>(`/transfers/${encodeURIComponent(id)}/receive`),
  addUnits: (
    facilityId: string,
    body: { component: Component; group: string; quantity: number; collectedOn?: string; unitId?: string },
  ) =>
    post<{ unitIds: string[]; expiresOn: string }>(`/facilities/${encodeURIComponent(facilityId)}/units`, body),
  discard: (facilityId: string, unitId: string, reason: string) =>
    post<void>(`/facilities/${encodeURIComponent(facilityId)}/units/${encodeURIComponent(unitId)}/discard`, { reason }),
  notifications: () => call<NotificationList>("/notifications"),
  markRead: (id: number) => post<void>(`/notifications/${id}/read`),
  markAllRead: () => post<void>("/notifications/read-all"),
  compatibility: (body: { component: Component; donor: string; recipient: string }) =>
    post<Compatibility>("/compatibility", body),
  risks: () => call<Risk[]>("/risks"),
  strategies: () => call<Strategy[]>("/strategies"),
  compare: () => call<Comparison[]>("/strategies/compare"),
  plan: (strategy: string) => post<Transfer[]>("/plans", { strategy }),
  transfers: () => call<Transfer[]>("/transfers"),
  approve: (id: string) => post<Transfer>(`/transfers/${encodeURIComponent(id)}/approve`),
  reject: (id: string) => post<Transfer>(`/transfers/${encodeURIComponent(id)}/reject`),
  audit: (limit = 9) => call<AuditView>(`/audit?limit=${limit}`),
  setupStatus: () => call<{ needsSetup: boolean }>("/setup"),
  setup: (body: { username: string; displayName: string; password: string }) => post<Account>("/setup", body),
  changePassword: (currentPassword: string, newPassword: string) =>
    post<void>("/auth/password", { currentPassword, newPassword }),
  users: () => call<Account[]>("/users"),
  createUser: (body: { username: string; displayName: string; role: Role; facilityId?: string; phone?: string }) =>
    post<NewAccount>("/users", body),
  updateContact: (phone: string, alertsEnabled: boolean) =>
    call<Account>("/auth/contact", { method: "PUT", body: JSON.stringify({ phone, alertsEnabled }) }),
  alerts: () => call<AlertsView>("/alerts"),
  testAlert: () => post<{ queued: number; to: string; channels: string[] }>("/alerts/test"),
  resetPassword: (username: string) => post<NewAccount>(`/users/${encodeURIComponent(username)}/reset-password`),
  privacy: () => call<PrivacyNoticeFacts>("/privacy"),
  myData: () => call<unknown>("/auth/my-data"),
  anonymise: (username: string) => post<Account>(`/users/${encodeURIComponent(username)}/anonymise`),
  setEnabled: (username: string, enabled: boolean) =>
    post<Account>(`/users/${encodeURIComponent(username)}/enabled`, { enabled }),
  addFacility: (body: FacilityInput) => post<Facility>("/facilities", body),
  updateFacility: (id: string, body: FacilityInput) =>
    call<Facility>(`/facilities/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(body) }),
  routes: (id: string) => call<RouteInfo[]>(`/facilities/${encodeURIComponent(id)}/routes`),
  setRoute: (from: string, to: string, distanceKm: number, travelMinutes: number) =>
    call<RouteInfo>(`/routes/${encodeURIComponent(from)}/${encodeURIComponent(to)}`, {
      method: "PUT",
      body: JSON.stringify({ distanceKm, travelMinutes }),
    }),
};
