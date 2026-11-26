# Sentinel console — user guide

How to use the Sentinel AML analyst console day to day. It's written for analysts, senior analysts and administrators, not developers. For how the console is built, see [frontend-technical-guide.md](frontend-technical-guide.md). To get the system running on your machine, see [GETTING-STARTED.md](GETTING-STARTED.md).

---

## 1. The two ideas you need: alerts and cases

**An alert is raised by the system. A case is opened by a person.** An alert says "this pattern looks suspicious." A case is the investigation that pulls several alerts together and ends in a decision.

| | Alert | Case |
|---|---|---|
| **Who creates it** | The system. Detection flags transactions and turns the hits into an alert | An analyst, by hand |
| **What it covers** | One suspicious pattern: one **rule**, on one **account**, in one **time window** | Any number of alerts you want to investigate together, such as everything on one customer's accounts |
| **Repeats** | If the same pattern keeps happening in the same window, the new hits are **added to the same alert** (its hit count goes up). A new window creates a new alert | An alert can belong to **only one** case |
| **Statuses** | New → Under review → Escalated → Closed (false positive / confirmed) | Open → Investigating → Closed (no action / reported to FIU) |
| **Who decides** | Any analyst | Analysts open and work cases. **Only senior analysts close them**, and a written resolution is required |
| **What it records** | Rule, account, evidence (hits), risk score, an optional note on each decision | Title, priority, owner, investigation notes, resolution, full history |

**Example.** One customer has three accounts. Over a week the system raises five alerts: two for *structuring* (many deposits just under the reporting limit), two for *rapid movement* (money in and straight out), and one for a *high-risk jurisdiction*. You see they are one story. You open "CASE-000042 — possible layering by customer X", add the five alerts, take ownership and investigate. A senior analyst then closes the case as **reported to FIU** (the Financial Intelligence Unit) or **no action needed**.

Every case action is written to the tamper-evident [audit trail](#9-audit-trail-senior-analysts-and-admins).

---

## 2. Signing in and roles

1. Open the console (locally: <http://localhost:3000>). Click **Continue with Keycloak** and sign in with your organisation account.
2. You return to the **Overview**. Your name and role appear at the bottom of the left sidebar. Sign out with the arrow button next to them.

Your role decides what you see:

| Feature | Analyst | Senior analyst | Admin |
|---|:-:|:-:|:-:|
| Overview, Alerts, Cases, Customers, Detection rules (view) | ✓ | ✓ | ✓ |
| Change alert status, open and work cases, add notes | ✓ | ✓ | ✓ |
| **Close** a case | | ✓ | ✓ |
| Reveal a full national ID (with a reason) | | ✓ | ✓ |
| Change a customer's or an account's risk rating | | ✓ | ✓ |
| Audit trail | | ✓ | ✓ |
| Verify the audit chain | | | ✓ |
| Edit detection rules | | | ✓ |
| Dead letters (replay or delete failed messages) | | | ✓ |
| Simulation: create demo customers, send transactions, upload CSVs | | | ✓ |

Local test accounts: `analyst1`, `senior1`, `admin1`. The password is the same as the username.

---

## 3. Finding your way around

- **Left sidebar.** It groups pages into *Monitoring* (Overview, Alerts, Cases), *Investigation* (Customers, Audit trail) and *Configuration* (Detection rules, Dead letters) and, for admins, *Demo* (Simulation). **API reference** opens the gateway's Swagger page. On a phone the sidebar is behind the ☰ button.
- **Theme.** The three buttons above your name switch between system, light and dark. Your choice is remembered.
- **Top bar.** Breadcrumbs show where you are. The pill on the right shows the **live connection**:
  - **Live** (green, pulsing): changes arrive as they happen.
  - **Connecting** (grey): the console is (re)connecting. It retries by itself. If it stays grey, alert-case-service is probably not running.
- **Notifications.** Every message appears as a notification at the top right (at the bottom on a phone):
  - green: your action worked ("Moved to Under review.")
  - red: it didn't, and why
  - red, amber or teal, with an account and rule: a live alert change (new alert, new evidence, status changed) by anyone. Click it to open the alert.

  Each one closes by itself, and the thin bar at its bottom shows how long is left (errors stay longest). **Hover over it (or tab to it) to pause**, or close it with ×. The page you're on also refreshes its numbers when alerts change.
- **While something loads**, the page shows a grey outline of its layout. After you click an action button, that button shows a spinner and the form's other buttons wait, so nothing is sent twice.
- **If a page can't load its data** (a service is down, for example), a red box says why. Click **Try again** once the problem is fixed.
- **Wrong address or missing record.** A "No record of this page", "Alert not found" or "Case not found" page offers a way back. If something unexpected breaks a page, you get an error page with **Try again** and a reference code to give your administrator.

---

## 4. Overview

What needs a decision now:

- **Tiles.** *Open alerts* (new, under review or escalated), *Open cases*, and *Noise removed*: how many raw rule hits were folded into each alert. For example, 3.2× means 100 alerts instead of 320 notifications. The last tile is *Escalated* for analysts, or *Dead letters* for admins. Click a tile to go to its list.
- **Needs attention.** The six newest **New** alerts, highest risk first. **Work the queue** opens the full list.
- **Alert pipeline.** How many alerts are in each status. Click a row to filter.
- **Open cases** and, for senior analysts, **Recent activity** from the audit trail.

---

## 5. Working alerts

### The queue (**Alerts**)

One row per alert, highest risk first. Filter by **Status**, **Rule** or **Account** and click **Apply**. **Reset** clears the filters. The columns:

- **Risk.** A score from 0 to 100: **70 or more is high** (red), **40 to 69 is medium** (amber), **below 40 is low**.
- **Hits.** How many rule hits this alert has collected.
- **Latest explanation.** The rule's plain-language reason for the most recent hit.

### One alert

Click an account ID to open the alert.

- **Latest explanation** and **Evidence.** Each hit shows the transaction, its weight and risk score, when the transaction happened and when it was detected. Expand **"N transactions in the pattern"** to see every transaction the rule looked at.
- **Details panel.** Shows the account, the time window, the hit count and the first and last transaction. Click the account to look up the customer.
- **Decision.** Write an optional **Note** (what you checked and why), then click a **Move to …** button. Only the moves allowed from the current status are shown:

  | From | You can move to |
  |---|---|
  | New | Under review, Closed – false positive, Closed – confirmed |
  | Under review | Escalated, Closed – false positive, Closed – confirmed |
  | Escalated | Closed – false positive, Closed – confirmed |
  | Closed (either) | Nothing. Closed alerts are final |

  A closed alert still collects new hits from the same window as evidence, but it does not reopen.
- **If someone else changed the alert while you were reading**, your move is refused with a message instead of overwriting their decision. The page reloads with the current state, so check it and decide again.

### From alert to case

In the **Case** panel of an alert:

- **Open a new case.** Edit the suggested title and click **Open case**. You land on the new case.
- **Or add to an existing case.** Type `CASE-000012` (or just `12`) and click **Add**.
- If the alert is already in a case, the panel links to that case instead.

---

## 6. Working cases

### The list (**Cases**)

The tabs are **Open** (open or investigating), **Assigned to me** and **All**. On **All** you can also filter by status. Each row shows priority, status, owner, how many alerts it holds, the highest risk among them and when it was last updated.

### One case

- **Alerts in this case.** Click any alert to open it. To add another alert, paste its ID (the long ID in the alert's web address) and click **Add**.
- **Investigation notes.** Write what you found, who you contacted and what comes next, then click **Add note**. Notes are permanent. They can't be edited or deleted.
- **Manage panel** (while the case is open):
  - **Assign to me** makes you the owner.
  - **Start investigating** moves the case from Open to Investigating.
  - **Owner / Priority** reassigns the case (leave the owner empty to unassign) or changes its priority. Click **Save**.
  - **Close with a resolution** is for senior analysts only. Write what was decided and why, then choose **Close · no action needed** or **Close · reported to FIU**. Analysts see this box greyed out.
- **History.** Every step, with who did it and when. The *Audited* mark means each entry is also in the hash-chained audit trail.
- A closed case shows its **Resolution** at the top and can no longer be changed.

As with alerts, if two people change a case at the same time, the second change is refused rather than silently overwriting the first.

---

## 7. Customers

Look up who is behind an account. Enter an **account ID** (such as `ACC-RAPID-01`) or a **customer ID**, then click **Look up**. Clicking an account on an alert's page brings you here directly.

- **Profile.** Name, risk rating and other details. The **national ID is always masked**.
- **Reveal the national ID** (senior analysts). Click reveal, give a reason (for example "KYC refresh for CASE-000012") and click **Reveal**. The full ID shows for 60 seconds and then hides again. The reveal and your reason are recorded in the audit trail. Analysts see a lock icon instead.
- **Accounts.** Each account and its rating. Click an account to see its alerts.
- **Change a risk rating** (senior analysts). Pick a **Rating** for the customer or an account, give a **Reason** and click **Update**. Detection uses the new rating within seconds, and the change is audited.

---

## 8. Detection rules

The list shows each rule's threshold, window, weight, state and version. The **State** column means:

- **Live · vN**: detection already runs version N.
- **Reloading**: a change was just saved and the detection pods are picking it up (a few seconds).
- **Disabled**: the rule is switched off.

Open a rule to see its description and **Version history** (who changed what, when and why).

**Admins** can change a rule's threshold, window (in minutes), weight (0 to 100) and whether it is enabled. A **Reason** is required. Saving creates a new version, and old versions are kept. What the threshold means depends on the rule: an amount in ₹, a count, a percentage or a multiplier. The form labels it for each rule.

---

## 9. Audit trail (senior analysts and admins)

**Audit trail** shows one append-only log of every decision from every service: alert moves, case steps, notes, ID reveals, rating and rule changes. Filter by **Record type**, **Record ID** (such as `CASE-000001`, a customer ID or a rule code) or **User**.

Each entry carries a hash of the entry before it, so changing, deleting or reordering anything breaks the chain. **Admins** can click **Verify chain** to recompute every hash. The console then confirms the chain is intact or names the first broken entry.

---

## 10. Dead letters (admins)

A message that failed five delivery attempts is parked here so the rest of its queue keeps flowing. Pick a queue to see its parked messages and the error for each.

1. Fix the cause first, for example by starting the service that was down.
2. Click **Replay all N**, or replay single messages.
3. **Delete** only messages that can never be processed.

The Overview tile turns red while anything is parked.

---

## 11. Simulation (admins, for demos and testing)

In a real bank, transactions arrive from the bank's own systems: core banking, payment rails and end-of-day files. **Simulation** stands in for those systems, so you can show or test the whole flow from the console. It uses the same API a real feed uses; nothing here is a shortcut around detection.

**Scenarios.** Six cards, one per detection rule. **Run scenario** creates a made-up customer and account (named `ACC-DEMO-…`), then sends the transactions that make that rule fire:

| Card | What it sends | Alert you get |
|---|---|---|
| Large cash deposit | ₹12,00,000 cash deposit | CTR threshold |
| Structuring (smurfing) | 3 cash deposits just under ₹8.3 lakh | Structuring |
| Rapid movement (layering) | ₹4.87 lakh in, ₹4.53 lakh straight out | Rapid movement |
| Sanctioned counterparty | A wire to a near-match of a watchlisted company, country IRN | High-risk jurisdiction |
| Repeated round amounts | 5 wires in round lakhs | Round amounts |
| Sudden change in behaviour | 10 backdated days of about ₹20,000, then ₹3.4 lakh today (takes about 10 seconds) | Behavioral deviation |

- **Customer risk** (Low, Medium, High) above the cards sets the new demo customer's rating. A higher rating gives a higher alert score: × 1.0, × 1.15 and × 1.3.
- After a run, the card shows the new account with links to its **Alerts** and its **Customer**, and the alert's notification pops up within seconds.
- If an admin has changed a rule's thresholds on **Detection rules**, its scenario may no longer fire.

**Upload a CSV.** Pick a file and click **Upload**. It's processed in the background like a bank's end-of-day file. The card follows it live and shows the rows, how many were accepted and rejected, and the reason for each rejected line. **Sample file** downloads a ready-made CSV with a structuring pattern, a large deposit, a sanctioned payee and one deliberately invalid row. Columns: `sourceAccountId, destinationAccountId, amount, currency, type`, and optionally `counterpartyName, counterpartyCountry, txnTimestamp`.

**Send one transaction.** A form for a single transaction. Accounts starting with `EXT-` are outside the bank. Use an account from a scenario to add to its story: for example, another deposit into a scenario account adds evidence to its existing alert instead of raising a new one.

**Bulk customers.** Adds 5,000 synthetic customers with accounts and risk ratings, so customer lookups and lists look like a real bank.

**A demo in two minutes:** open **Overview** in one window and **Simulation** in another. Run *Structuring* and *Sanctioned counterparty*, watch the notifications and numbers update, open an alert, then open a case from it (§5).

---

## 12. Common questions

| Question | Answer |
|---|---|
| Why didn't I get a new alert, only more hits on an old one? | Same rule, same account, same time window means one alert. Only a new window creates a new alert |
| I can't close a case | Closing needs a senior analyst and a written resolution |
| "Changed by someone else" error | Another person updated the record while you had it open. The page now shows their change, so decide again |
| Pill stuck on **Connecting** | The live stream can't reach alert-case-service. Pages still work. Ask your administrator |
| I was sent back to the sign-in page | Your Keycloak session ended. Sign in again and you return to the page you were on |
| Where's the full national ID? | Masked for everyone. Senior analysts can reveal it with a reason, and every reveal is audited |
