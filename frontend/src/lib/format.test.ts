import { describe, expect, it } from "vitest";
import { formatDateTime, formatThreshold, formatWindow, severity, thresholdLabel } from "./format";

describe("formatThreshold", () => {
  it("shows each rule's threshold in its own unit", () => {
    expect(formatThreshold("CTR_THRESHOLD", 830000)).toBe("₹8,30,000");
    expect(formatThreshold("STRUCTURING", 3)).toBe("3 transactions");
    expect(formatThreshold("RAPID_MOVEMENT", 80)).toBe("80 %");
    expect(formatThreshold("BEHAVIORAL_DEVIATION", 3)).toBe("3× average");
    expect(formatThreshold("HIGH_RISK_JURISDICTION", 0)).toBe("any amount");
    expect(thresholdLabel("ROUND_AMOUNT")).toBe("Threshold (transactions)");
  });
});

describe("formatting", () => {
  it("formats windows and treats service timestamps as UTC", () => {
    expect(formatWindow(1440)).toBe("1 d");
    expect(formatWindow(2880)).toBe("2 d");
    expect(formatWindow(90)).toBe("90 min");
    // 06:10 UTC = 11:40 IST
    expect(formatDateTime("2026-11-21T06:10:15.73")).toContain("11:40");
  });

  it("maps risk scores to severity bands", () => {
    expect(severity(82.5)).toBe("high");
    expect(severity(39)).toBe("low");
    expect(severity(55)).toBe("medium");
  });
});
