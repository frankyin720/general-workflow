const SESSION_KEY = "application-workflow.demo.session";
const API = {
  conductor: () => fetchJson("/api/demo/auth/conductor", { method: "POST" }),
  login: (body) => fetchJson("/api/demo/auth/login", { method: "POST", body: JSON.stringify(body) }),
  personas: () => fetchJson("/api/demo/auth/personas"),
  flowProfiles: () => fetchJson("/api/demo/flow-profiles"),
  applications: () => fetchJson("/api/demo/applications"),
  startApp: (body) => fetchJson("/api/demo/applications", { method: "POST", body: JSON.stringify(body) }),
  status: (id) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/status`),
  edoc: (id, body) => post(`/api/demo/applications/${encodeURIComponent(id)}/edoc-upload`, body),
  appraisal: (id, body) => post(`/api/demo/applications/${encodeURIComponent(id)}/appraisal-completed`, body),
  esign: (id) => post(`/api/demo/applications/${encodeURIComponent(id)}/esign-complete`),
  clientDeclined: (id, body) => post(`/api/demo/applications/${encodeURIComponent(id)}/client-declined`, body),
  closing: (id) => post(`/api/demo/applications/${encodeURIComponent(id)}/closing-confirmed`),
  withdraw: (id, body) => post(`/api/demo/applications/${encodeURIComponent(id)}/withdraw`, body),
  humanTasks: (id) => fetchJson(`/api/demo/human-tasks${id ? "?applicationId=" + encodeURIComponent(id) : ""}`),
  completeTask: (key, body) => post(`/api/demo/human-tasks/${key}/complete`, body),
  events: () => fetchJson("/api/demo/integration-events?limit=40"),
};
const post = (url, body) => fetchJson(url, { method: "POST", body: body ? JSON.stringify(body) : undefined });

/**
 * Decision forms per BPMN user task (element id → fields). Each field becomes a process variable
 * that the task's outgoing gateway evaluates. Mirrors the Camunda forms under src/main/resources/process.
 */
const TASK_FORMS = {
  Task_complianceReview: {
    who: "Compliance",
    fields: [
      { key: "complianceDecision", label: "Decision", options: [["CLEARED", "Cleared — proceed"], ["DECLINED", "Declined"]] },
      { key: "complianceNotes", label: "Notes", textarea: true },
    ],
  },
  Task_manualDocumentReview: {
    who: "Operations",
    fields: [
      { key: "documentValid", label: "Document acceptable?", options: [[true, "Yes — accept"], [false, "No — request re-upload"]], boolean: true },
    ],
  },
  Task_decideDocumentExpiry: {
    who: "Advisor",
    fields: [{ key: "documentExpiryDecision", label: "Documents overdue", options: [["EXTEND", "Extend 30 days"], ["CANCEL", "Cancel application"]] }],
  },
  Task_followUpAppraisal: {
    who: "Operations",
    fields: [{ key: "appraisalFollowUpDecision", label: "Appraisal overdue", options: [["WAIT", "Keep waiting"], ["CANCEL", "Cancel application"]] }],
  },
  Task_manualUnderwritingReview: {
    who: "Bank Underwriter",
    fields: [
      { key: "underwritingDecision", label: "Decision", options: [["APPROVED", "Approve"], ["DECLINED", "Decline"]] },
      { key: "approvalConditionsText", label: "Approval conditions (one per line)", textarea: true },
      { key: "declineReason", label: "Decline reason", textarea: true },
    ],
  },
  Task_seniorUnderwriterReview: {
    who: "Senior Underwriter",
    fields: [
      { key: "underwritingDecision", label: "Decision", options: [["APPROVED", "Approve"], ["DECLINED", "Decline"]] },
      { key: "approvalConditionsText", label: "Approval conditions (one per line)", textarea: true },
      { key: "declineReason", label: "Decline reason", textarea: true },
    ],
  },
  Task_fixCommitmentLetter: {
    who: "Operations",
    fields: [{ key: "commitmentFixNotes", label: "Resolution notes", textarea: true, required: false }],
    submitLabel: "Resolved — regenerate letter",
  },
  Task_decideCommitmentExpiry: {
    who: "Advisor",
    fields: [{ key: "commitmentExpiryDecision", label: "Commitment expired", options: [["EXTEND", "Re-issue commitment letter"], ["WITHDRAW", "Withdraw offer"]] }],
  },
  Task_resolveClosingDelay: {
    who: "Operations",
    fields: [{ key: "closingDelayDecision", label: "Closing overdue", options: [["WAIT", "New date agreed — wait"], ["CANCEL", "Deal collapsed — cancel"]] }],
  },
  Task_clearFundingConditions: {
    who: "Operations",
    fields: [
      { key: "outstandingConditions", label: "Outstanding conditions", number: true, default: 0 },
      { key: "insuranceConfirmed", label: "Default insurance confirmed", options: [[true, "Yes"], [false, "No"]], boolean: true },
      { key: "fundingHold", label: "Funding hold", options: [[false, "Released"], [true, "Keep on hold"]], boolean: true },
    ],
    submitLabel: "Re-run funding readiness",
  },
  Task_manualFundingIntervention: {
    who: "Operations",
    fields: [{ key: "fundingInterventionDecision", label: "Disbursement failed", options: [["RETRY", "Issue fixed — retry"], ["ABORT", "Abort funding"]] }],
  },
};

const STAGE_ORDER = ["SUBMITTED", "PRE_SCREENING", "DOCUMENTATION", "APPRAISAL", "UNDERWRITING", "COMMITMENT", "CLOSING", "FUNDING"];

let state = {
  token: localStorage.getItem(SESSION_KEY),
  user: null,
  applications: [],
  selectedId: null,
  pollTimer: null,
  profiles: [],
  happyPathRunning: false,
};

const els = {
  personaGrid: document.getElementById("personaGrid"),
  userBadge: document.getElementById("userBadge"),
  actionPanel: document.getElementById("actionPanel"),
  applicationList: document.getElementById("applicationList"),
  statusDetail: document.getElementById("statusDetail"),
  integrationFeed: document.getElementById("integrationFeed"),
  roleHint: document.getElementById("roleHint"),
  pipelineSteps: document.getElementById("pipelineSteps"),
  pollIndicator: document.getElementById("pollIndicator"),
  refreshAppsBtn: document.getElementById("refreshAppsBtn"),
  refreshEventsBtn: document.getElementById("refreshEventsBtn"),
};

init();

async function init() {
  els.refreshAppsBtn.addEventListener("click", loadApplications);
  els.refreshEventsBtn.addEventListener("click", loadEvents);
  try {
    state.profiles = await API.flowProfiles().catch(() => []);
    renderAllActions();
    await loadPersonasForOptionalSwitch();
    await ensureConductorSession();
    showWorkspace();
    await refreshAll();
    startPolling();
  } catch (e) {
    els.actionPanel.innerHTML = `<div class="flash error">${escapeHtml(e.message)}</div><p class="muted">Ensure application-workflow is running with <code>application-workflow.demo.enabled=true</code> and restart Spring Boot.</p>`;
  }
}

async function ensureConductorSession() {
  const res = await API.conductor();
  state.token = res.token;
  state.user = res;
  localStorage.setItem(SESSION_KEY, state.token);
}

async function loadPersonasForOptionalSwitch() {
  const personas = await API.personas();
  els.personaGrid.innerHTML = "";
  personas.filter((p) => p.role !== "CONDUCTOR").forEach((p) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "persona-card";
    card.innerHTML = `<h3>${escapeHtml(p.displayName)}</h3><p class="muted small">${escapeHtml(p.role)}</p>`;
    card.addEventListener("click", () => loginAs(p.username));
    els.personaGrid.appendChild(card);
  });
}

async function loginAs(username) {
  const res = await API.login({ username, password: "demo" });
  state.token = res.token;
  state.user = res;
  localStorage.setItem(SESSION_KEY, state.token);
  showWorkspace();
  renderAllActions();
  await refreshAll();
}

function showWorkspace() {
  els.userBadge.textContent = state.user.role === "CONDUCTOR"
    ? "Conductor mode — all steps on this page"
    : `${state.user.displayName} · ${state.user.role} (limited actions)`;
  els.roleHint.textContent =
    "Five-phase mortgage origination: pre-screening (credit + AML, DMN) → documentation & appraisal (parallel) → GDS/TDS underwriting (DMN, manual review with SLA escalation) → commitment letter & eSign (reminders, 14-day expiry) → closing, funding readiness (DMN) and disbursement. Timeouts and failures create human tasks instead of stalling.";
}

function renderAllActions() {
  const limited = state.user && state.user.role !== "CONDUCTOR";
  const profileOptions = (state.profiles.length
    ? state.profiles
    : [{ name: "HAPPY_AUTO", description: "Auto-approve" }, { name: "MANUAL_UNDERWRITING", description: "Manual underwriting" }])
    .map((p) => `<option value="${escapeHtml(p.name)}">${escapeHtml(p.description)}</option>`)
    .join("");

  els.actionPanel.innerHTML = `
    <h2>Workflow steps</h2>
    <p class="muted small">Each block maps to a Camunda message, user task or worker. External systems (credit bureau, AML, ECM, appraiser, eSign, solicitor, core banking) are mocked inside the workers; failures are simulated via the scenario selected in step 1.</p>

    <div class="step-block" data-step-block="SUBMITTED">
      <div class="step-head"><span class="step-num">1</span><div><strong>Advisor</strong> — Start application</div></div>
      <div class="form-row">
        <label>Product<select id="appType"><option>Mortgage</option><option>Refinance</option><option>HELOC</option></select></label>
        <label>Scenario<select id="flowProfile">${profileOptions}</select></label>
        <label>Customer<input id="customerName" placeholder="Jamie Lee" /></label>
      </div>
      <div class="form-row">
        <label>Loan amount<input id="loanAmount" type="number" placeholder="520000" /></label>
        <label>Purchase price<input id="purchasePrice" type="number" placeholder="650000" /></label>
        <label>Annual income<input id="annualIncome" type="number" placeholder="145000" /></label>
        <label>Monthly debts<input id="monthlyDebtPayments" type="number" placeholder="600" /></label>
      </div>
      <button class="btn" type="button" id="startAppBtn">Start mortgage application process</button>
    </div>

    <div class="step-block muted-step" data-step-block="PRE_SCREENING">
      <div class="step-head"><span class="step-num">2</span><div><strong>System / Compliance</strong> — Credit bureau + AML/KYC (parallel, 15m timeouts) → eligibility DMN</div></div>
      <p class="muted small">PROCEED continues automatically. MANUAL_REVIEW creates a Compliance task (24h SLA → escalation). DECLINE sends an adverse action notice.</p>
    </div>

    <div class="step-block" data-step-block="DOCUMENTATION">
      <div class="step-head"><span class="step-num">3</span><div><strong>Advisor / ECM</strong> — Income &amp; financial eDoc uploaded</div></div>
      <div class="form-row">
        <label>Document type<select id="docType"><option value="">All required docs (shortcut)</option><option value="NOA">NOA</option><option value="T4">T4</option><option value="EMPLOYMENT_LETTER">Employment letter</option><option value="BANK_STATEMENT">Bank statement</option></select></label>
        <label>Status<select id="docStatus"><option value="UPLOADED">UPLOADED</option><option value="INVALID">INVALID (rejected → re-upload)</option></select></label>
      </div>
      <button class="btn ghost" type="button" id="edocBtn">Correlate eDocUploadedEvent</button>
      <p class="muted small">Reminders every 7 days (×3); after 30 days the advisor must extend or cancel. ECM failure/timeout → manual document review.</p>
    </div>

    <div class="step-block" data-step-block="APPRAISAL">
      <div class="step-head"><span class="step-num">4</span><div><strong>Appraiser</strong> — Appraisal report received (parallel with step 3)</div></div>
      <div class="form-row">
        <label>Appraised value<input id="appraisedValue" type="number" placeholder="= purchase price" /></label>
      </div>
      <button class="btn ghost" type="button" id="appraisalBtn">Correlate appraisalCompletedEvent</button>
      <p class="muted small">No report after 10 days → Operations follow-up task (wait / cancel).</p>
    </div>

    <div class="step-block muted-step" data-step-block="UNDERWRITING">
      <div class="step-head"><span class="step-num">5</span><div><strong>System / Underwriter</strong> — GDS/TDS (stress-tested) → underwriting DMN</div></div>
      <p class="muted small">AUTO_APPROVE: GDS ≤ 39, TDS ≤ 44, LTV ≤ 80 (or ≤ 95 insured), score ≥ 680/720. Otherwise manual review (48h SLA escalation, reassigned to senior underwriter after 5 days) or decline.</p>
    </div>

    <div class="step-block" data-step-block="COMMITMENT">
      <div class="step-head"><span class="step-num">6</span><div><strong>Customer</strong> — Commitment letter eSign</div></div>
      <div class="form-row inline">
        <button class="btn ghost" type="button" id="esignBtn">Correlate eSignCompletedEvent</button>
        <button class="btn ghost danger" type="button" id="clientDeclinedBtn">Client declines offer</button>
      </div>
      <p class="muted small">Reminders every 3 days; unsigned after 14 days → advisor extends (re-issue) or withdraws.</p>
    </div>

    <div class="step-block" data-step-block="CLOSING">
      <div class="step-head"><span class="step-num">7</span><div><strong>Solicitor / notary</strong> — Closing day confirmed</div></div>
      <button class="btn ghost" type="button" id="closingBtn">Correlate closingDayConfirmedEvent</button>
      <p class="muted small">No confirmation after 45 days → Operations resolves (wait / cancel).</p>
    </div>

    <div class="step-block muted-step" data-step-block="FUNDING">
      <div class="step-head"><span class="step-num">8</span><div><strong>System / Operations</strong> — Funding readiness DMN → core banking disbursement → account activation</div></div>
      <p class="muted small">HOLD creates a task to clear conditions; disbursement failure → reversal + manual intervention (retry / abort).</p>
    </div>

    <div class="step-block" data-step-block="HUMAN">
      <div class="step-head"><span class="step-num">★</span><div><strong>Human tasks</strong> — open reviews &amp; decisions for the selected application</div></div>
      <div id="humanTasksHost"><p class="muted small">No open human tasks.</p></div>
    </div>

    <div class="step-block highlight">
      <div class="form-row inline">
        <button class="btn" type="button" id="happyPathBtn">▶ Run happy path (auto)</button>
        <button class="btn ghost danger" type="button" id="withdrawBtn">Withdraw application (any phase)</button>
      </div>
      <p class="muted small">Happy path: start → eDocs → appraisal → eSign → closing confirmation → funding (polls every 2s). Withdrawal fires the global <code>applicationWithdrawnEvent</code> and cancels the instance.</p>
    </div>
  `;

  if (limited) {
    els.actionPanel.querySelectorAll(".step-block").forEach((b) => b.classList.add("dimmed"));
  }

  on("startAppBtn", startApplication);
  on("edocBtn", sendEdoc);
  on("appraisalBtn", sendAppraisal);
  on("esignBtn", sendEsign);
  on("clientDeclinedBtn", sendClientDeclined);
  on("closingBtn", sendClosing);
  on("withdrawBtn", sendWithdraw);
  on("happyPathBtn", runHappyPath);
}

function on(id, handler) {
  document.getElementById(id)?.addEventListener("click", () => handler().catch((e) => flash(els.actionPanel, e.message, "error")));
}

async function runHappyPath() {
  if (state.happyPathRunning) return;
  state.happyPathRunning = true;
  try {
    document.getElementById("flowProfile").value = "HAPPY_AUTO";
    await startApplication();
    const appId = state.selectedId;
    flash(els.actionPanel, "Happy path started for " + appId + " — waiting for documentation stage…", "ok");

    await waitFor(appId, (s) => ["DOCUMENTATION", "APPRAISAL"].includes(s.stageKey), 120);
    await API.edoc(appId, { documentType: null, documentStatus: "UPLOADED" });
    await API.appraisal(appId, {});
    flash(els.actionPanel, "eDocs + appraisal sent — waiting for underwriting/commitment…", "ok");

    const s = await waitFor(appId, (st) => st.stageKey === "COMMITMENT" || (st.openHumanTasks || []).length > 0 || st.stageKey === "DECLINED", 180);
    if (s.stageKey !== "COMMITMENT") {
      flash(els.actionPanel, "Process needs a human decision (" + s.currentStage + ") — complete it in the Human tasks block.", "ok");
      return;
    }
    await API.esign(appId);
    flash(els.actionPanel, "eSign sent — waiting for closing stage…", "ok");
    await waitFor(appId, (st) => st.stageKey === "CLOSING", 120);
    await API.closing(appId);
    flash(els.actionPanel, "Closing confirmed — funding readiness + disbursement should complete shortly.", "ok");
    await refreshAll();
  } finally {
    state.happyPathRunning = false;
  }
}

async function waitFor(appId, predicate, maxSeconds) {
  for (let i = 0; i < maxSeconds; i += 2) {
    await sleep(2000);
    const s = await API.status(appId);
    await loadStatus();
    if (predicate(s)) return s;
    if (s.processState !== "ACTIVE") return s;
  }
  throw new Error("Timed out waiting for next stage");
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function startApplication() {
  const num = (id) => {
    const v = document.getElementById(id)?.value;
    return v ? Number(v) : null;
  };
  const financials = {
    loanAmount: num("loanAmount"),
    purchasePrice: num("purchasePrice"),
    annualIncome: num("annualIncome"),
    monthlyDebtPayments: num("monthlyDebtPayments"),
  };
  Object.keys(financials).forEach((k) => financials[k] == null && delete financials[k]);
  const body = {
    applicationType: document.getElementById("appType").value,
    flowProfile: document.getElementById("flowProfile").value,
    customerName: document.getElementById("customerName").value || null,
    financials,
  };
  const app = await API.startApp(body);
  flash(els.actionPanel, "Started " + app.applicationId, "ok");
  state.selectedId = app.applicationId;
  await refreshAll();
}

async function sendEdoc() {
  requireSelection();
  await API.edoc(state.selectedId, {
    documentType: document.getElementById("docType")?.value || null,
    documentStatus: document.getElementById("docStatus")?.value || "UPLOADED",
  });
  flash(els.actionPanel, "eDocUploadedEvent correlated", "ok");
  await refreshAll();
}

async function sendAppraisal() {
  requireSelection();
  const v = document.getElementById("appraisedValue")?.value;
  await API.appraisal(state.selectedId, { appraisedValue: v ? Number(v) : null });
  flash(els.actionPanel, "appraisalCompletedEvent correlated", "ok");
  await refreshAll();
}

async function sendEsign() {
  requireSelection();
  await API.esign(state.selectedId);
  flash(els.actionPanel, "eSignCompletedEvent correlated", "ok");
  await refreshAll();
}

async function sendClientDeclined() {
  requireSelection();
  await API.clientDeclined(state.selectedId, { reason: "Client chose a competing offer" });
  flash(els.actionPanel, "clientDeclinedCommitmentEvent correlated", "ok");
  await refreshAll();
}

async function sendClosing() {
  requireSelection();
  await API.closing(state.selectedId);
  flash(els.actionPanel, "closingDayConfirmedEvent correlated", "ok");
  await refreshAll();
}

async function sendWithdraw() {
  requireSelection();
  if (!confirm("Withdraw " + state.selectedId + "? The process instance will be cancelled.")) return;
  await API.withdraw(state.selectedId, { reason: "CLIENT_WITHDRAWN" });
  flash(els.actionPanel, "applicationWithdrawnEvent correlated", "ok");
  await refreshAll();
}

async function loadHumanTasks() {
  const host = document.getElementById("humanTasksHost");
  if (!host) return;
  try {
    const tasks = await API.humanTasks(state.selectedId);
    if (!tasks.length) {
      host.innerHTML = "<p class='muted small'>No open human tasks" + (state.selectedId ? " for " + escapeHtml(state.selectedId) : "") + ".</p>";
      return;
    }
    host.innerHTML = tasks.map(renderTaskCard).join("");
    host.querySelectorAll("[data-complete]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const card = btn.closest(".task-card");
        const form = TASK_FORMS[card.dataset.element];
        const variables = {};
        (form?.fields || []).forEach((f) => {
          const input = card.querySelector(`[data-field="${f.key}"]`);
          if (!input) return;
          let value = input.value;
          if (value === "" || value == null) return;
          if (f.boolean) value = value === "true";
          else if (f.number) value = Number(value);
          variables[f.key] = value;
        });
        if (!form) variables.completed = true;
        try {
          await API.completeTask(btn.getAttribute("data-complete"), { variables, comment: null });
          flash(host, "Task completed", "ok");
          await refreshAll();
        } catch (e) {
          flash(host, e.message, "error");
        }
      });
    });
  } catch (e) {
    host.innerHTML = `<div class="flash error">${escapeHtml(e.message)}</div>`;
  }
}

function renderTaskCard(t) {
  const form = TASK_FORMS[t.elementId];
  const fields = (form?.fields || []).map((f) => {
    if (f.options) {
      const opts = f.options.map(([v, l]) => `<option value="${escapeHtml(String(v))}">${escapeHtml(l)}</option>`).join("");
      return `<label>${escapeHtml(f.label)}<select data-field="${f.key}">${opts}</select></label>`;
    }
    if (f.textarea) {
      return `<label>${escapeHtml(f.label)}<textarea data-field="${f.key}" rows="2"></textarea></label>`;
    }
    return `<label>${escapeHtml(f.label)}<input data-field="${f.key}" type="${f.number ? "number" : "text"}" value="${f.default ?? ""}" /></label>`;
  }).join("");
  const groups = (t.candidateGroups || []).join(", ");
  return `
    <div class="task-card" data-key="${t.userTaskKey}" data-element="${escapeHtml(t.elementId)}">
      <strong>${escapeHtml(t.name)}</strong>
      <div class="muted small">${escapeHtml(t.businessId || "—")} · ${escapeHtml(groups || form?.who || "—")} · #${t.userTaskKey}${t.creationDate ? " · " + new Date(t.creationDate).toLocaleString() : ""}</div>
      ${fields ? `<div class="form-row">${fields}</div>` : ""}
      <button class="btn" type="button" data-complete="${t.userTaskKey}">${escapeHtml(form?.submitLabel || "Submit decision")}</button>
    </div>`;
}

async function loadApplications() {
  state.applications = await API.applications();
  if (!state.selectedId && state.applications.length) {
    state.selectedId = state.applications[0].applicationId;
  }
  renderApplicationList();
}

function renderApplicationList() {
  els.applicationList.innerHTML = "";
  if (!state.applications.length) {
    els.applicationList.innerHTML = "<p class='muted'>No applications yet — use step 1.</p>";
    return;
  }
  state.applications.forEach((app) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "app-item" + (app.applicationId === state.selectedId ? " selected" : "");
    btn.innerHTML = `<strong>${escapeHtml(app.applicationId)}</strong><span class="muted small">${escapeHtml(app.applicationType)} · ${escapeHtml(app.flowProfile)}</span>`;
    btn.addEventListener("click", () => {
      state.selectedId = app.applicationId;
      renderApplicationList();
      loadStatus();
      loadHumanTasks();
    });
    els.applicationList.appendChild(btn);
  });
}

async function loadStatus() {
  if (!state.selectedId) {
    els.statusDetail.innerHTML = "<span class='muted'>Start step 1 or select an application.</span>";
    updatePipeline(null);
    return;
  }
  const s = await API.status(state.selectedId);
  const v = s.variables || {};
  const jobs = (s.activeJobs || []).map((j) => j.type + " (" + j.state + ")").join(", ") || "—";
  const failed = (s.failedJobs || []).length ? s.failedJobs.map((j) => j.type + " retries=" + j.retries).join(", ") : "—";
  const inc = (s.incidents || []).length ? s.incidents.map((i) => i.elementId + ": " + i.errorMessage).join("; ") : "—";
  const active = (s.activeElements || []).map((e) => e.elementName || e.elementId).filter(Boolean).join(", ") || "—";
  const pre = v.preScreening || {};
  const uw = v.underwriting || {};
  const fr = v.fundingReadiness || {};
  const fmt = (x) => (x == null ? "—" : Array.isArray(x) ? (x.length ? x.join(", ") : "none") : String(x));

  els.statusDetail.innerHTML = `
    <p><span class="stage-pill">${escapeHtml(s.currentStage)}</span> <span class="muted small">next: ${escapeHtml(s.suggestedRole || "—")}</span></p>
    ${s.systemHint ? `<p class="flash ok" style="margin:0.5rem 0">${escapeHtml(s.systemHint)}</p>` : ""}
    <dl>
      <dt>Process</dt><dd>${escapeHtml(s.processState)} · status ${escapeHtml(fmt(v.applicationStatus))}</dd>
      <dt>Active elements</dt><dd>${escapeHtml(active)}</dd>
      <dt>Credit</dt><dd>score ${escapeHtml(fmt(v.creditScore))} · ${escapeHtml(fmt(v.creditCheckOutcome))}</dd>
      <dt>AML / KYC</dt><dd>${escapeHtml(fmt(v.amlOutcome))}</dd>
      <dt>Pre-screening (DMN)</dt><dd>${escapeHtml(fmt(pre.eligibility))}${pre.reason ? " — " + escapeHtml(pre.reason) : ""}${v.complianceDecision ? " · compliance " + escapeHtml(v.complianceDecision) : ""}</dd>
      <dt>Documents</dt><dd>verified ${escapeHtml(fmt(v.verifiedDocumentTypes))} · outstanding ${escapeHtml(fmt(v.outstandingDocuments))} · reminders ${escapeHtml(fmt(v.documentReminderCount ?? 0))}</dd>
      <dt>Appraisal / LTV</dt><dd>${escapeHtml(fmt(v.appraisedValue))} · LTV ${escapeHtml(fmt(v.ltvRatio))}%</dd>
      <dt>GDS / TDS</dt><dd>${escapeHtml(fmt(v.gdsRatio))}% / ${escapeHtml(fmt(v.tdsRatio))}% @ ${escapeHtml(fmt(v.qualifyingRate))}%</dd>
      <dt>Underwriting (DMN)</dt><dd>${escapeHtml(fmt(uw.route))}${uw.reason ? " — " + escapeHtml(uw.reason) : ""}${v.underwritingDecision ? " · underwriter " + escapeHtml(v.underwritingDecision) : ""}${v.approvalPath ? " · " + escapeHtml(v.approvalPath) : ""}</dd>
      <dt>Escalations</dt><dd>${escapeHtml(fmt(v.escalationCount ?? 0))}${v.lastEscalation ? " (" + escapeHtml(v.lastEscalation) + ")" : ""}</dd>
      <dt>Commitment / eSign</dt><dd>${escapeHtml(fmt(v.commitmentLetterId))} v${escapeHtml(fmt(v.commitmentVersion))} · ${escapeHtml(fmt(v.eSignStatus))}</dd>
      <dt>Closing / funding</dt><dd>closing ${escapeHtml(fmt(v.closingConfirmed))} · readiness ${escapeHtml(fmt(fr.decision))}${fr.reason ? " — " + escapeHtml(fr.reason) : ""} · ref ${escapeHtml(fmt(v.disbursementReference))}</dd>
      <dt>Outcome</dt><dd>${escapeHtml(fmt(v.declineReason || v.cancelReason || v.mortgageAccountNumber))}</dd>
      <dt>Active jobs</dt><dd>${escapeHtml(jobs)}</dd>
      <dt>Failed jobs</dt><dd>${escapeHtml(failed)}</dd>
      <dt>Incidents</dt><dd>${escapeHtml(inc)}</dd>
    </dl>`;

  updatePipeline(s);
  highlightNextStep(s);
}

function highlightNextStep(status) {
  document.querySelectorAll("[data-step-block]").forEach((el) => el.classList.remove("next-step"));
  if (!status || status.processState !== "ACTIVE") return;
  const hasTasks = (status.openHumanTasks || []).length > 0;
  const block = hasTasks ? "HUMAN" : status.stageKey;
  const el = document.querySelector(`[data-step-block="${block}"]`);
  if (el) el.classList.add("next-step");
  if (status.stageKey === "DOCUMENTATION" && !hasTasks) {
    document.querySelector('[data-step-block="APPRAISAL"]')?.classList.add("next-step");
  }
}

function updatePipeline(status) {
  const items = els.pipelineSteps.querySelectorAll("li");
  items.forEach((li) => li.classList.remove("active", "done", "failed"));
  if (!status) return;
  const v = status.variables || {};
  const steps = String(v.completedSteps || "");
  const done = {
    SUBMITTED: steps.includes("MORTGAGE_FILE_CREATED"),
    PRE_SCREENING: steps.includes("CREDIT_BUREAU") && steps.includes("AML_KYC") && v.preScreening != null && !["PRE_SCREENING"].includes(status.stageKey),
    DOCUMENTATION: steps.includes("FINANCIAL_DOCS_VERIFIED"),
    APPRAISAL: steps.includes("LTV_CALCULATED"),
    UNDERWRITING: steps.includes("APPROVED"),
    COMMITMENT: steps.includes("SOLICITOR_INSTRUCTIONS"),
    CLOSING: steps.includes("FUNDING_CHECKLIST"),
    FUNDING: steps.includes("MORTGAGE_ACTIVE"),
  };
  Object.entries(done).forEach(([key, isDone]) => {
    const li = els.pipelineSteps.querySelector(`[data-step="${key}"]`);
    if (li && isDone) li.classList.add("done");
  });
  if (status.processState === "ACTIVE") {
    const li = els.pipelineSteps.querySelector(`[data-step="${status.stageKey}"]`);
    if (li) li.classList.add("active");
  } else if (["DECLINED", "CANCELLED"].includes(status.stageKey)) {
    const idx = STAGE_ORDER.findIndex((k) => !done[k]);
    const li = idx >= 0 ? items[idx] : null;
    if (li) li.classList.add("failed");
  }
}

async function loadEvents() {
  const events = await API.events();
  els.integrationFeed.innerHTML = events.map((e) =>
    `<div class="event"><span class="muted">${new Date(e.at).toLocaleTimeString()}</span> · <strong>${escapeHtml(e.source)}</strong> ${escapeHtml(e.action)} <span class="muted">${escapeHtml(e.applicationId)}</span><div class="muted small">${escapeHtml(e.detail || "")}</div></div>`
  ).join("");
}

async function refreshAll() {
  await loadApplications();
  await loadStatus();
  await loadEvents();
  await loadHumanTasks();
}

function startPolling() {
  if (state.pollTimer) clearInterval(state.pollTimer);
  els.pollIndicator.textContent = "Auto-refresh 3s";
  state.pollTimer = setInterval(() => refreshAll().catch(() => {}), 3000);
}

function requireSelection() {
  if (!state.selectedId) throw new Error("Select an application (or start step 1)");
}

function flash(container, message, kind) {
  const el = document.createElement("div");
  el.className = `flash ${kind}`;
  el.textContent = message;
  container.prepend(el);
  setTimeout(() => el.remove(), 6000);
}

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

async function fetchJson(url, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.token) headers["X-Demo-Session"] = state.token;
  const res = await fetch(url, { ...options, headers });
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { detail: text };
    }
  }
  if (!res.ok) {
    const msg = (data && (data.detail || data.message || data.title)) || res.statusText || "Request failed";
    if (res.status === 400 && String(msg).toLowerCase().includes("header")) {
      throw new Error("Session missing — hard refresh the page (Cmd+Shift+R).");
    }
    if (res.status === 502) {
      throw new Error(msg + " — Is Camunda running on :8080 / :26500 and application-workflow deployed?");
    }
    throw new Error(msg);
  }
  return data;
}
