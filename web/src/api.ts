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

export interface Allocation {
  sourceId: string;
  sourceName: string;
  distanceKm: number;
  travelMinutes: number;
  donorGroup: string;
  unitIds: string[];
  daysLeft: number[];
  substitute: boolean;
  reason: string;
}

export interface RequestResult {
  unitsIssued: number;
  usedSubstitutes: boolean;
  allocations: Allocation[];
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
  status: "PROPOSED" | "COMPLETED" | "REJECTED";
  reason: string;
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

export interface SimulationRow {
  policy: string;
  unitsExpired: number;
  expiredByComponent: Record<Component, number>;
  requestsFulfilled: number;
  requestsUnmet: number;
  unitsTransferred: number;
  wastageReductionPercent: number;
}

export interface SimulationView {
  days: number;
  seed: number;
  start: string;
  results: SimulationRow[];
}

/** An error returned by the API, with a message written for staff. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
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
}

/** The CSRF token Spring Security puts in the XSRF-TOKEN cookie; it must be echoed on every change. */
function csrfToken(): string {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]!) : "";
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? "GET";
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
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // keep the generic message
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const post = <T>(path: string, body?: unknown) =>
  call<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  me: () => call<User>("/auth/me"),
  login: (username: string, password: string) => post<User>("/auth/login", { username, password }),
  logout: () => post<void>("/auth/logout"),
  summary: () => call<Summary>("/summary"),
  facilities: () => call<Facility[]>("/facilities"),
  facilityStock: (id: string) => call<FacilityStock>(`/facilities/${encodeURIComponent(id)}/stock`),
  recordReport: (id: string) => post<Facility>(`/facilities/${encodeURIComponent(id)}/report`),
  request: (body: { hospitalId: string; component: Component; group: string; quantity: number; urgency: string }) =>
    post<RequestResult>("/requests", body),
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
  simulate: (days: number) => post<SimulationView>("/simulations", { days }),
  advanceDate: (today: string) => post<Summary>("/date", { today }),
};
