"use client";

import { useActionState, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner, SubmitButton } from "@/components/SubmitButton";
import type { BatchStatus } from "@/lib/api";
import { CSV_COLUMNS, sampleCsv } from "@/lib/simulation";
import { batchStatus, uploadCsv, type UploadResult } from "./actions";

const POLL_MS = 1500;
const POLL_FOR_MS = 5 * 60_000;

/** Upload a transactions CSV, then follow the batch until it finishes and show the rejected rows. */
export function CsvUpload() {
  const [upload, action] = useActionState<UploadResult | null, FormData>(uploadCsv, null);
  const [status, setStatus] = useState<BatchStatus | null>(null);
  const [pollError, setPollError] = useState<string | null>(null);

  useEffect(() => {
    if (!upload?.ok || !upload.batchId) return;
    const id = upload.batchId;
    const started = Date.now();
    let stop = false;
    setStatus(null);
    setPollError(null);
    (async function poll() {
      while (!stop && Date.now() - started < POLL_FOR_MS) {
        const r = await batchStatus(id);
        if (stop) return;
        if (!r.ok) {
          setPollError(r.message);
          return;
        }
        setStatus(r.status);
        if (r.status.status !== "PROCESSING") return;
        await new Promise((res) => setTimeout(res, POLL_MS));
      }
    })();
    return () => {
      stop = true;
    };
  }, [upload]);

  function downloadSample() {
    const url = URL.createObjectURL(new Blob([sampleCsv(new Date())], { type: "text/csv" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: "sentinel-sample-transactions.csv" });
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <section aria-labelledby="csv-h" className="card flex flex-col p-5">
      <div className="mb-1 flex items-start justify-between gap-3">
        <h2 id="csv-h" className="card-title flex items-center gap-2">
          <Icon name="upload" size={15} className="text-muted" />
          Upload a CSV
        </h2>
        <button type="button" onClick={downloadSample} className="btn btn-ghost h-7 px-2 text-[12px]">
          <Icon name="download" size={13} />
          Sample file
        </button>
      </div>
      <p className="mb-4 text-[13px] text-muted">
        Like a bank's end-of-day file. Processed in the background; bad rows are rejected and reported, the rest go through detection.
      </p>

      <form action={action} className="flex flex-wrap items-center gap-2">
        <label className="sr-only" htmlFor="csv-file">CSV file</label>
        <input
          id="csv-file"
          name="file"
          type="file"
          accept=".csv,text/csv"
          required
          className="min-w-0 flex-1 text-[12.5px] text-ink-2 file:mr-3 file:h-8 file:cursor-pointer file:rounded-lg file:border file:border-line-strong file:bg-surface file:px-3 file:text-[12.5px] file:font-medium file:text-ink hover:file:bg-sunken"
        />
        <SubmitButton className="btn btn-primary" icon={<Icon name="upload" size={13} />}>
          Upload
        </SubmitButton>
      </form>

      <details className="group mt-3 text-[12px] text-muted">
        <summary className="inline-flex cursor-pointer list-none items-center gap-1 hover:text-ink">
          <Icon name="chevron" size={11} className="transition-transform group-open:rotate-90" />
          Columns
        </summary>
        <p className="mt-2 leading-relaxed">
          Header row with <span className="font-mono text-ink-2">{CSV_COLUMNS.slice(0, 5).join(", ")}</span> (required) and optionally{" "}
          <span className="font-mono text-ink-2">{CSV_COLUMNS.slice(5).join(", ")}</span>, in any order. Accounts starting with{" "}
          <span className="font-mono text-ink-2">EXT-</span> are outside the bank. Up to 50 MB.
        </p>
      </details>

      {upload && !upload.ok && (
        <p role="alert" className="mt-3 text-[12.5px] text-danger">{upload.message}</p>
      )}
      {pollError && <p role="alert" className="mt-3 text-[12.5px] text-danger">{pollError}</p>}
      {upload?.ok && <BatchProgress fileName={upload.fileName ?? ""} batchId={upload.batchId!} status={status} />}
    </section>
  );
}

function BatchProgress({ fileName, batchId, status }: { fileName: string; batchId: string; status: BatchStatus | null }) {
  const processing = !status || status.status === "PROCESSING";
  const tone = processing ? "text-warn" : status.status === "COMPLETED" ? "text-ok" : "text-danger";
  return (
    <div className="mt-4 rounded-lg border border-line bg-sunken/40 p-3.5 animate-rise">
      <div className="flex flex-wrap items-center justify-between gap-2 text-[12.5px]">
        <span className="min-w-0 truncate font-medium text-ink">{fileName}</span>
        <span className={`inline-flex items-center gap-1.5 font-medium ${tone}`}>
          {processing && <Spinner size={12} />}
          {processing ? "Processing" : status.status === "COMPLETED" ? "Completed" : "Failed"}
        </span>
      </div>
      <div className="mt-1 font-mono text-[11px] text-muted">batch {batchId}</div>
      {status && (
        <dl className="mt-3 grid grid-cols-3 gap-2 text-center">
          {(
            [
              ["Rows", status.totalRecords, "text-ink"],
              ["Accepted", status.acceptedRecords, "text-ok"],
              ["Rejected", status.failedRecords, status.failedRecords ? "text-danger" : "text-ink"],
            ] as const
          ).map(([label, n, cls]) => (
            <div key={label} className="rounded-md bg-surface px-2 py-2">
              <dt className="eyebrow">{label}</dt>
              <dd className={`mt-0.5 text-[18px] font-semibold tabular-nums ${cls}`}>{n ?? "–"}</dd>
            </div>
          ))}
        </dl>
      )}
      {status && status.errors.length > 0 && (
        <div className="mt-3 max-h-56 overflow-auto rounded-md border border-line bg-surface">
          <table className="table text-[12px]">
            <thead>
              <tr>
                <th className="th w-14">Line</th>
                <th className="th">Why it was rejected</th>
              </tr>
            </thead>
            <tbody>
              {status.errors.map((e, i) => (
                <tr key={i}>
                  <td className="td font-mono text-muted">{e.lineNumber ?? "–"}</td>
                  <td className="td">
                    <div className="text-ink">{e.reason}</div>
                    {e.rawData && <div className="mt-0.5 truncate font-mono text-[11px] text-muted">{e.rawData}</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
