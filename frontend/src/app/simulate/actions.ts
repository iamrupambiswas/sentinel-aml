"use server";

import { randomUUID } from "node:crypto";
import { api, errorMessage, type BatchStatus } from "@/lib/api";
import { flashResult } from "@/lib/flash-server";
import { demoAccountId, demoCustomer, scenario, type SimTxn } from "@/lib/simulation";

/*
 * The Simulation page stands in for the bank's own systems (onboarding, core banking, file drops). Everything
 * goes through the same public API a real feed would use, with the signed-in admin's token: customer-service
 * for customers/accounts (ADMIN), ingestion-service for transactions (INGEST, part of ADMIN).
 */

const RISKS = ["LOW", "MEDIUM", "HIGH"] as const;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function send(t: SimTxn): Promise<string> {
  return api<string>("/api/v1/transactions", {
    method: "POST",
    headers: { "Idempotency-Key": randomUUID() }, // a real feed reuses the key on retries; each demo txn is new
    body: JSON.stringify(t),
  });
}

export interface ScenarioResult {
  ok: boolean;
  message: string;
  accountId?: string;
  customerName?: string;
  sent?: number;
}

/** Creates a demo customer and account, then plays the scenario's transactions. */
export async function runScenario(_prev: ScenarioResult | null, formData: FormData): Promise<ScenarioResult> {
  const s = scenario(String(formData.get("scenario")));
  if (!s) return { ok: false, message: "Unknown scenario." };
  const riskInput = String(formData.get("risk"));
  const risk = RISKS.find((r) => r === riskInput) ?? "MEDIUM";

  const now = new Date();
  const accountId = demoAccountId(s.tag, now);
  const customer = demoCustomer(s);
  let sent = 0;
  try {
    const created = await api<{ id: string }>("/api/v1/customers", { method: "POST", body: JSON.stringify(customer) });
    await api(`/api/v1/customers/${encodeURIComponent(created.id)}/accounts`, {
      method: "POST",
      body: JSON.stringify({ accountId, type: s.customerType === "CORPORATE" ? "CURRENT" : "SAVINGS", currency: "INR", riskRating: risk }),
    });

    const { history, live } = s.build(accountId, now);
    for (const t of history) {
      await send(t);
      sent++;
    }
    if (history.length) {
      // Let detection record the backdated days, then rebuild baselines now instead of waiting for the nightly job
      await sleep(4000);
      await api("/api/v1/detection/baseline/recompute", { method: "POST" });
    }
    for (const t of live) {
      await send(t);
      sent++;
    }
    return {
      ok: true,
      accountId,
      customerName: customer.fullName,
      sent,
      message: `Sent ${sent} transaction${sent === 1 ? "" : "s"}. The alert should appear within a few seconds.`,
    };
  } catch (e) {
    return { ok: false, accountId: sent ? accountId : undefined, sent, message: errorMessage(e) };
  }
}

/** One hand-made transaction from the form. */
export async function sendTransaction(formData: FormData) {
  const text = (k: string) => String(formData.get(k) ?? "").trim();
  const amount = Number(text("amount"));
  let err: string | null = null;
  let id = "";
  if (!text("sourceAccountId") || !text("destinationAccountId")) err = "Fill in both accounts.";
  else if (!Number.isFinite(amount) || amount <= 0) err = "Enter an amount above zero.";
  else {
    const t: SimTxn = {
      sourceAccountId: text("sourceAccountId"),
      destinationAccountId: text("destinationAccountId"),
      amount,
      currency: text("currency").toUpperCase() || "INR",
      type: text("type") || "WIRE",
      ...(text("counterpartyName") ? { counterpartyName: text("counterpartyName") } : {}),
      ...(text("counterpartyCountry") ? { counterpartyCountry: text("counterpartyCountry").toUpperCase() } : {}),
    };
    try {
      id = await send(t);
    } catch (e) {
      err = errorMessage(e);
    }
  }
  await flashResult(err, `Transaction ${id.slice(0, 8)}… accepted. Detection runs in the background.`);
}

export interface UploadResult {
  ok: boolean;
  message: string;
  batchId?: string;
  fileName?: string;
}

/** Forwards the CSV to ingestion-service, which processes it in the background (Spring Batch). */
export async function uploadCsv(_prev: UploadResult | null, formData: FormData): Promise<UploadResult> {
  const file = formData.get("file");
  if (!(file instanceof File) || file.size === 0) return { ok: false, message: "Choose a CSV file first." };
  if (!file.name.toLowerCase().endsWith(".csv")) return { ok: false, message: "Only .csv files are accepted." };
  const body = new FormData();
  body.append("file", file, file.name);
  try {
    const batchId = await api<string>("/api/v1/ingestion/batches", { method: "POST", body });
    return { ok: true, batchId, fileName: file.name, message: "Uploaded. Processing in the background…" };
  } catch (e) {
    return { ok: false, message: errorMessage(e) };
  }
}

/** Polled by the page while a batch is processing. */
export async function batchStatus(batchId: string): Promise<{ ok: true; status: BatchStatus } | { ok: false; message: string }> {
  if (!/^[A-Za-z0-9-]{1,64}$/.test(batchId)) return { ok: false, message: "Unknown batch." };
  try {
    return { ok: true, status: await api<BatchStatus>(`/api/v1/ingestion/batches/${batchId}?maxErrors=50`) };
  } catch (e) {
    return { ok: false, message: errorMessage(e) };
  }
}

/** customer-service's synthetic data generator (5,000 customers with accounts and risk ratings). */
export async function seedCustomers() {
  let err: string | null = null;
  let result = "";
  try {
    result = await api<string>("/api/v1/admin/seed-data", { method: "POST" });
  } catch (e) {
    err = errorMessage(e);
  }
  await flashResult(err, result || "Seeded.");
}
