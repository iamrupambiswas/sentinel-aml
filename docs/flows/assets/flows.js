/* Shared navigation + Mermaid rendering for docs/flows. Pages only contain their own content. */
(function () {
  const PAGES = [
    { group: "Start here", items: [
      ["index.html", "Overview & map"],
      ["01-system-overview.html", "System architecture"],
      ["02-gateway-routing.html", "Gateway routing"] ] },
    { group: "Customer", items: [
      ["03-customer-onboarding.html", "Customer onboarding & PII"],
      ["04-customer-seeding.html", "Synthetic data seeding"],
      ["15-account-risk-sync.html", "Account risk ratings to Redis"] ] },
    { group: "Ingestion", items: [
      ["05-streaming-ingestion.html", "Streaming ingestion (REST)"],
      ["06-batch-ingestion.html", "Batch CSV ingestion"],
      ["07-outbox-relay.html", "Outbox relay & event contract"] ] },
    { group: "Detection", items: [
      ["08-detection-pipeline.html", "Detection pipeline"],
      ["09-detection-rules.html", "The six AML rules"],
      ["10-rule-config-and-baseline.html", "Rule config, watchlist & baseline"],
      ["13-rule-hot-reload.html", "Live rule tuning & hot reload"] ] },
    { group: "Alerts", items: [
      ["12-alert-dedup.html", "Alert de-duplication & workflow"] ] },
    { group: "Operations", items: [
      ["14-dead-letters.html", "Poison messages & DLQ replay"] ] },
    { group: "Reference", items: [
      ["11-data-stores.html", "Data stores reference"] ] }
  ];
  const flat = PAGES.flatMap(g => g.items);
  const current = location.pathname.split("/").pop() || "index.html";

  // ---- theme ----
  const saved = (() => { try { return localStorage.getItem("flows-theme"); } catch (e) { return null; } })();
  if (saved) document.documentElement.setAttribute("data-theme", saved);
  const isDark = () => {
    const t = document.documentElement.getAttribute("data-theme");
    return t ? t === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
  };

  // ---- sidebar ----
  const side = document.querySelector(".sidebar");
  if (side) {
    let html = '<div class="brand">Sentinel AML</div><div class="brand-sub">Flow documentation &middot; Phases 0&ndash;5</div>';
    for (const g of PAGES) {
      html += "<h4>" + g.group + "</h4>";
      for (const [href, label] of g.items) {
        html += '<a href="' + href + '"' + (href === current ? ' class="active"' : "") + ">" + label + "</a>";
      }
    }
    html += '<h4>Also</h4><a href="../ADR/README.md">Architecture decision records</a>';
    html += '<button class="theme-toggle" type="button">Toggle light / dark</button>';
    side.innerHTML = html;
    side.querySelector(".theme-toggle").addEventListener("click", () => {
      const next = isDark() ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", next);
      try { localStorage.setItem("flows-theme", next); } catch (e) {}
      location.reload();
    });
  }

  // ---- prev / next ----
  const main = document.querySelector("main");
  const i = flat.findIndex(p => p[0] === current);
  if (main && i >= 0) {
    const pager = document.createElement("div");
    pager.className = "pager";
    pager.innerHTML =
      (i > 0 ? '<a href="' + flat[i - 1][0] + '">&larr; ' + flat[i - 1][1] + "</a>" : "<span></span>") +
      (i < flat.length - 1 ? '<a class="next" href="' + flat[i + 1][0] + '">' + flat[i + 1][1] + " &rarr;</a>" : "<span></span>");
    main.appendChild(pager);
  }

  // ---- mermaid ----
  function render() {
    if (!window.mermaid) return;
    window.mermaid.initialize({
      startOnLoad: false, securityLevel: "loose", theme: isDark() ? "dark" : "default",
      flowchart: { htmlLabels: true, curve: "basis", useMaxWidth: false },
      sequence: { mirrorActors: false, showSequenceNumbers: true, useMaxWidth: false, wrap: true, width: 140, actorMargin: 40, messageFontSize: 14, noteFontSize: 13 },
      er: { useMaxWidth: false }, state: { useMaxWidth: false }
    });
    window.mermaid.run({ querySelector: ".mermaid" });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render); else render();
})();
