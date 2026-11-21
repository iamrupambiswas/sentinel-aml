import { describe, expect, it } from "vitest";
import { SCENARIOS, demoAccountId, demoCustomer, sampleCsv, scenario, toCsv, type SimTxn } from "./simulation";

// Default rule settings (detection-service V4__rule_params_ctr_and_alert_evidence.sql). Every scenario must
// trip its own rule and none of the others, so the demo shows one clear alert per click.
const CTR = 830_000;
const STRUCT = { min: 751_500, max: 834_999, count: 3 };
const ROUND = { min: 100_000, count: 5, nearEdge: CTR * 0.98 };
const RAPID = { minCredit: 100_000, pct: 80 };
const BEHAV = { factor: 3, minDaily: 50_000 };

const now = new Date("2026-11-21T09:30:00Z");
const ACC = "ACC-DEMO-TEST-1";
const isRound = (t: SimTxn) => t.amount >= ROUND.min && (t.amount % 1000 === 0 || (t.amount >= ROUND.nearEdge && t.amount < CTR));
const isStruct = (t: SimTxn) => t.amount >= STRUCT.min && t.amount <= STRUCT.max;
const all = (key: string) => {
  const { history, live } = scenario(key)!.build(ACC, now);
  return [...history, ...live];
};

describe("scenarios trip their own rule", () => {
  it("CTR: one transaction at or above the threshold", () => {
    expect(all("ctr").filter((t) => t.amount >= CTR)).toHaveLength(1);
  });

  it("structuring: 3 amounts in the band, none a CTR, round or near-threshold amount", () => {
    const txns = all("structuring");
    expect(txns.filter(isStruct)).toHaveLength(STRUCT.count);
    expect(txns.some((t) => t.amount >= CTR || isRound(t))).toBe(false);
  });

  it("rapid movement: credits over the minimum, then debits of at least 80% of them", () => {
    const txns = all("rapid");
    const credits = txns.filter((t) => t.destinationAccountId === ACC).reduce((n, t) => n + t.amount, 0);
    const debits = txns.filter((t) => t.sourceAccountId === ACC).reduce((n, t) => n + t.amount, 0);
    expect(credits).toBeGreaterThanOrEqual(RAPID.minCredit);
    expect((debits * 100) / credits).toBeGreaterThanOrEqual(RAPID.pct);
    expect(txns.at(-1)!.sourceAccountId).toBe(ACC); // the debit comes last, as in real life
  });

  it("high-risk jurisdiction: watchlisted country and a near-miss of a listed name", () => {
    const [t] = all("highRisk");
    expect(t.counterpartyCountry).toBe("IRN");
    expect(t.counterpartyName?.toUpperCase()).toContain("ZENITH SHEL");
  });

  it("round amounts: 5 round transfers of at least 1 lakh, and nothing else fires", () => {
    const txns = all("round");
    expect(txns.filter(isRound)).toHaveLength(ROUND.count);
    expect(txns.some((t) => t.amount >= CTR || isStruct(t))).toBe(false);
    expect(txns.every((t) => t.sourceAccountId === ACC)).toBe(true); // no credits: rapid movement can't fire
  });

  it("behavioural: backdated quiet days, then today above 3x the average and the daily minimum", () => {
    const { history, live } = scenario("behavioral")!.build(ACC, now);
    expect(history.length).toBeGreaterThanOrEqual(7);
    for (const t of history) {
      const ts = new Date(t.txnTimestamp!);
      expect(ts.getTime()).toBeLessThan(Date.UTC(2026, 10, 21)); // before today (UTC)
      expect(now.getTime() - ts.getTime()).toBeLessThan(90 * 86_400_000);
      expect(isRound(t) || isStruct(t)).toBe(false);
    }
    const avg = history.reduce((n, t) => n + t.amount, 0) / new Set(history.map((t) => t.txnTimestamp!.slice(0, 10))).size;
    const today = live.reduce((n, t) => n + t.amount, 0);
    expect(today).toBeGreaterThan(BEHAV.factor * avg);
    expect(today).toBeGreaterThanOrEqual(BEHAV.minDaily);
    expect(live.some((t) => t.amount >= CTR || isRound(t) || isStruct(t))).toBe(false);
  });

  it("every scenario only uses EXT- accounts as the other side", () => {
    for (const s of SCENARIOS) {
      for (const t of all(s.key)) {
        const other = t.sourceAccountId === ACC ? t.destinationAccountId : t.sourceAccountId;
        expect(other.startsWith("EXT-")).toBe(true);
      }
    }
  });
});

describe("demo data", () => {
  it("account IDs are valid for customer-service and differ between runs", () => {
    const ids = new Set(Array.from({ length: 50 }, () => demoAccountId("STRUCT", now)));
    for (const id of ids) expect(id).toMatch(/^[A-Za-z0-9-]{1,36}$/);
    expect(ids.size).toBeGreaterThan(40);
  });

  it("customers are valid registrations with a PAN-shaped fake ID", () => {
    const c = demoCustomer(scenario("rapid")!);
    expect(c.type).toBe("CORPORATE");
    expect(c.idNumber).toMatch(/^[A-Z]{5}\d{4}[A-Z]$/);
    expect(c.nationality).toMatch(/^[A-Z]{3}$/);
  });
});

describe("CSV", () => {
  it("writes the header and quotes cells with commas or quotes", () => {
    const csv = toCsv([{ sourceAccountId: "A", destinationAccountId: "B", amount: 1, currency: "INR", type: "WIRE", counterpartyName: 'Acme, "Ltd"' }]);
    const [header, row] = csv.trim().split("\n");
    expect(header).toBe("sourceAccountId,destinationAccountId,amount,currency,type,counterpartyName,counterpartyCountry,txnTimestamp");
    expect(row).toBe('A,B,1,INR,WIRE,"Acme, ""Ltd""",,');
  });

  it("the sample has 5 good rows and 1 invalid one", () => {
    const lines = sampleCsv(now).trim().split("\n");
    expect(lines).toHaveLength(1 + 5 + 1);
    expect(lines.at(-1)).toContain(",-500,");
  });
});
