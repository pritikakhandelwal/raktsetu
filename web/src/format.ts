// Small, pure formatting helpers (unit tested in format.test.ts).

import type { Component } from "./api";

const COMPONENT_LABELS: Record<Component, string> = {
  WHOLE_BLOOD: "Whole blood",
  RED_CELLS: "Red cells",
  PLATELETS: "Platelets",
  PLASMA: "Plasma",
};

export function componentLabel(c: Component): string {
  return COMPONENT_LABELS[c];
}

export function plural(n: number, singular: string, pluralForm = `${singular}s`): string {
  return `${n} ${n === 1 ? singular : pluralForm}`;
}

/** "expires today", "1 day left", "5 days left". */
export function daysLeft(n: number): string {
  if (n <= 0) return "expires today";
  return `${plural(n, "day")} left`;
}

/** Two letters for a facility badge: "SC04" becomes "04", "HUB01" becomes "HB". */
export function badge(id: string): string {
  if (id.startsWith("HUB")) return "HB";
  const digits = id.replace(/\D/g, "");
  return digits.length >= 2 ? digits.slice(-2) : id.slice(0, 2).toUpperCase();
}

/** The part of a sample facility name before the comma, without "(sample)". */
export function shortName(name: string): string {
  return name.replace(/\s*\(sample\)\s*$/i, "").split(",")[0]!.trim();
}

/** The place in a sample facility name, after the comma. */
export function place(name: string): string {
  const parts = name.replace(/\s*\(sample\)\s*$/i, "").split(",");
  return parts.length > 1 ? parts.slice(1).join(",").trim() : "";
}

/** Adds days to an ISO date without time-zone surprises. */
export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(Date.UTC(y!, m! - 1, d! + days));
  return date.toISOString().slice(0, 10);
}

export function percent(value: number): string {
  return `${Math.round(value)}%`;
}
