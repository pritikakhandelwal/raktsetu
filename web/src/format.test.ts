import { describe, expect, it } from "vitest";
import { addDays, badge, componentLabel, daysLeft, percent, place, plural, shortName } from "./format";

describe("format", () => {
  it("pluralises", () => {
    expect(plural(1, "unit")).toBe("1 unit");
    expect(plural(3, "unit")).toBe("3 units");
    expect(plural(2, "entry", "entries")).toBe("2 entries");
  });

  it("describes days left", () => {
    expect(daysLeft(0)).toBe("expires today");
    expect(daysLeft(1)).toBe("1 day left");
    expect(daysLeft(5)).toBe("5 days left");
  });

  it("makes facility badges", () => {
    expect(badge("HUB01")).toBe("HB");
    expect(badge("SC04")).toBe("04");
    expect(badge("X")).toBe("X");
  });

  it("splits sample facility names", () => {
    expect(shortName("Storage Centre, Baramati (sample)")).toBe("Storage Centre");
    expect(place("Storage Centre, Baramati (sample)")).toBe("Baramati");
    expect(place("Central")).toBe("");
  });

  it("adds days across month ends", () => {
    expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });

  it("labels components and percentages", () => {
    expect(componentLabel("RED_CELLS")).toBe("Red cells");
    expect(percent(35.24)).toBe("35%");
  });
});
