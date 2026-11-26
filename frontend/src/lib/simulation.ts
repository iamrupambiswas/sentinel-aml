/**
 * Demo scenarios for the Simulation page: each one is a short story of transactions built to trip exactly
 * one detection rule with its default settings (detection-service V4__rule_params migration). Pure functions,
 * so they are unit-tested against those rule conditions (simulation.test.ts). If an admin changes a rule's
 * threshold on the Detection rules page, a scenario may no longer fire; the page says so.
 *
 * Accounts starting with EXT- are external (other banks, cash): detection only evaluates our side.
 */

export interface SimTxn {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
  type: string;
  counterpartyName?: string;
  counterpartyCountry?: string;
  /** ISO instant; omitted = "now" (set by ingestion) */
  txnTimestamp?: string;
}

export type ScenarioKey = "ctr" | "structuring" | "rapid" | "highRisk" | "round" | "behavioral";

export interface Scenario {
  key: ScenarioKey;
  ruleCode: string;
  title: string;
  /** one sentence: what a launderer is doing */
  story: string;
  /** what the scenario sends, in plain words */
  steps: string[];
  /** short tag used in generated account IDs */
  tag: string;
  customerType: "RETAIL" | "CORPORATE";
  /**
   * history: backdated transactions sent first (then the baseline is recomputed);
   * live: sent in order, "now".
   */
  build(accountId: string, now: Date): { history: SimTxn[]; live: SimTxn[] };
}

const inr = (sourceAccountId: string, destinationAccountId: string, amount: number, type: string, extra: Partial<SimTxn> = {}): SimTxn => ({
  sourceAccountId,
  destinationAccountId,
  amount,
  currency: "INR",
  type,
  ...extra,
});

export const SCENARIOS: Scenario[] = [
  {
    key: "ctr",
    ruleCode: "CTR_THRESHOLD",
    title: "Large cash deposit",
    story: "A single cash deposit over the ₹8,30,000 reporting threshold.",
    steps: ["Deposit ₹12,00,000 in cash"],
    tag: "CTR",
    customerType: "RETAIL",
    build: (acc) => ({ history: [], live: [inr("EXT-CASH", acc, 1_200_000, "CASH_DEPOSIT")] }),
  },
  {
    key: "structuring",
    ruleCode: "STRUCTURING",
    title: "Structuring (smurfing)",
    story: "Cash split into deposits just under the reporting threshold, so none of them is reported on its own.",
    steps: ["3 cash deposits between ₹7.7 and ₹8.1 lakh, at different branches, within minutes"],
    tag: "STRUCT",
    customerType: "RETAIL",
    // Inside the rule's ₹7,51,500–8,34,999 band, below the round-amount rule's "just under ₹8.3 lakh" edge
    // (₹8,13,400) and not multiples of 1,000, so only STRUCTURING fires.
    build: (acc) => ({
      history: [],
      live: [
        inr("EXT-CASH-BR01", acc, 776_420.5, "CASH_DEPOSIT"),
        inr("EXT-CASH-BR02", acc, 794_350.25, "CASH_DEPOSIT"),
        inr("EXT-CASH-BR03", acc, 805_180.75, "CASH_DEPOSIT"),
      ],
    }),
  },
  {
    key: "rapid",
    ruleCode: "RAPID_MOVEMENT",
    title: "Rapid movement (layering)",
    story: "Money arrives and almost all of it leaves again straight away, to blur where it came from.",
    steps: ["Receive ₹4,87,250 by wire", "Send ₹4,52,600 (93%) abroad minutes later"],
    tag: "RAPID",
    customerType: "CORPORATE",
    build: (acc) => ({
      history: [],
      live: [inr("EXT-TRADE-PARTNER", acc, 487_250, "WIRE"), inr(acc, "EXT-OFFSHORE-77", 452_600, "WIRE")],
    }),
  },
  {
    key: "highRisk",
    ruleCode: "HIGH_RISK_JURISDICTION",
    title: "Sanctioned counterparty",
    story: "A payment to a company on the watchlist, in a high-risk jurisdiction, with a slightly misspelt name.",
    steps: ["Wire ₹2,08,750 to “Zenith Shel Holdings Ltd.” (watchlist: ZENITH SHELL HOLDINGS LTD), country IRN"],
    tag: "HRJ",
    customerType: "CORPORATE",
    build: (acc) => ({
      history: [],
      live: [inr(acc, "EXT-ZENITH-01", 208_750, "WIRE", { counterpartyName: "Zenith Shel Holdings Ltd.", counterpartyCountry: "IRN" })],
    }),
  },
  {
    key: "round",
    ruleCode: "ROUND_AMOUNT",
    title: "Repeated round amounts",
    story: "Five large transfers in round lakhs within a week: typical of invented invoices.",
    steps: ["5 wires of ₹1–3 lakh in exact round amounts to different vendors"],
    tag: "ROUND",
    customerType: "CORPORATE",
    build: (acc) => ({
      history: [],
      live: [100_000, 250_000, 150_000, 300_000, 200_000].map((amount, i) => inr(acc, `EXT-VENDOR-0${i + 1}`, amount, "WIRE")),
    }),
  },
  {
    key: "behavioral",
    ruleCode: "BEHAVIORAL_DEVIATION",
    title: "Sudden change in behaviour",
    story: "A quiet account that suddenly moves many times its normal daily amount.",
    steps: [
      "10 days of history: about ₹20,000 a day (backdated)",
      "Recompute the account's 90-day baseline",
      "Receive ₹3,40,250 today (≈ 17× normal)",
    ],
    tag: "BEHAV",
    customerType: "RETAIL",
    build: (acc, now) => {
      const history: SimTxn[] = [];
      for (let daysAgo = 10; daysAgo >= 1; daysAgo--) {
        const day = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - daysAgo, 10, 0, 0));
        // 18,000–21,999 and never a round 1,000: below every other rule's interest
        const amount = 18_000 + ((daysAgo * 397) % 4000) + 0.5;
        history.push(inr("EXT-SALARY", acc, amount, "NEFT", { txnTimestamp: day.toISOString() }));
      }
      return { history, live: [inr("EXT-PROPERTY-SALE", acc, 340_250, "RTGS")] };
    },
  },
];

export function scenario(key: string): Scenario | undefined {
  return SCENARIOS.find((s) => s.key === key);
}

/** ACC-DEMO-STRUCT-LX4K2P: unique per run, valid for customer-service (letters, digits, '-', max 36). */
export function demoAccountId(tag: string, now: Date, rand: () => number = Math.random): string {
  const stamp = now.getTime().toString(36).toUpperCase().slice(-6);
  const salt = Math.floor(rand() * 36 ** 2).toString(36).toUpperCase().padStart(2, "0");
  return `ACC-DEMO-${tag}-${stamp}${salt}`;
}

const FIRST = ["Aarav", "Diya", "Kabir", "Meera", "Rohan", "Ananya", "Vikram", "Isha", "Arjun", "Nisha"];
const LAST = ["Sharma", "Iyer", "Banerjee", "Kapoor", "Reddy", "Menon", "Gupta", "Das", "Nair", "Joshi"];
const LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

/** A made-up customer for a scenario. The national ID is fake but PAN-shaped (AAAAA9999A). */
export function demoCustomer(s: Scenario, rand: () => number = Math.random) {
  const pick = <T,>(xs: T[]) => xs[Math.floor(rand() * xs.length)];
  const letters = (n: number) => Array.from({ length: n }, () => pick([...LETTERS])).join("");
  const digits = (n: number) => Array.from({ length: n }, () => Math.floor(rand() * 10)).join("");
  const person = `${pick(FIRST)} ${pick(LAST)}`;
  return {
    type: s.customerType,
    fullName: s.customerType === "CORPORATE" ? `${pick(LAST)} ${pick(["Traders", "Exports", "Holdings", "Logistics"])} Pvt Ltd` : person,
    idNumber: `${letters(5)}${digits(4)}${letters(1)}`,
    dob: "1986-04-17",
    nationality: "IND",
  };
}

// ---------------------------------------------------------------- CSV

export const CSV_COLUMNS = [
  "sourceAccountId",
  "destinationAccountId",
  "amount",
  "currency",
  "type",
  "counterpartyName",
  "counterpartyCountry",
  "txnTimestamp",
] as const;

function cell(v: unknown): string {
  const s = v === undefined || v === null ? "" : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
}

export function toCsv(rows: SimTxn[]): string {
  return [CSV_COLUMNS.join(","), ...rows.map((r) => CSV_COLUMNS.map((c) => cell(r[c])).join(","))].join("\n") + "\n";
}

/**
 * A ready-made file for the CSV demo: a structuring pattern, a large deposit and a sanctioned payee on fresh
 * accounts, plus one invalid row (negative amount) so the rejected-rows report has something to show.
 */
export function sampleCsv(now: Date): string {
  const a = demoAccountId("CSVA", now);
  const b = demoAccountId("CSVB", now);
  const struct = scenario("structuring")!.build(a, now).live;
  const ctr = scenario("ctr")!.build(b, now).live;
  const hrj = scenario("highRisk")!.build(b, now).live;
  const rows = toCsv([...struct, ...ctr, ...hrj]);
  return rows + `${b},EXT-BROKEN,-500,INR,WIRE,,,\n`;
}
