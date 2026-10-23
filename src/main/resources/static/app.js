const SESSION_KEY = "general-workflow.demo.session";
const INTEGRATION_CHANNEL_KEY = "general-workflow.demo.integrationChannel";
const DEMO_DOC_TYPES = ["ID", "Income", "PropertyAssessment"];
const DEMO_MAX_DOCS = 3;
const WORKFLOW_STEP_ORDER = ["submit", "edoc", "credit", "uw", "esign", "fund"];
const STEP_JOB_TYPES = {
  submit: ["createApplicationRecord", "initializeDocumentChecklist"],
  edoc: [
    "validateDocumentViaEcm",
    "recordDocumentVerified",
    "markEdocsVerified",
    "notifyAdvisorDocumentIssue",
    "notifyAdvisorDocumentTimeout",
  ],
  credit: ["triggerCreditCheckAndAml", "prepareCreditTimeoutReferral", "recordCreditFailureForManualReview"],
  uw: ["escalateUnderwritingReview", "updateApplicationStatus", "handleUnderwritingRejection"],
  esign: [],
  fund: ["coreBankingFundingOrchestration", "notifyAdvisorFunded", "notifyAdvisorFundingFailed"],
};
const API = {
  conductor: () => fetchJson("/api/demo/auth/conductor", { method: "POST" }),
  login: (body) => fetchJson("/api/demo/auth/login", { method: "POST", body: JSON.stringify(body) }),
  personas: () => fetchJson("/api/demo/auth/personas"),
  applications: () => fetchJson("/api/demo/applications"),
  startApp: (body) => fetchJson("/api/demo/applications", { method: "POST", body: JSON.stringify(body) }),
  status: (id) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/status`),
  edoc: (id, body) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/edoc-upload`, { method: "POST", body: JSON.stringify(body) }),
  esign: (id, body) =>
    fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/esign-complete`, {
      method: "POST",
      body: JSON.stringify(body ?? {}),
    }),
  uwTasks: () => fetchJson("/api/demo/underwriting/tasks"),
  completeUw: (key, body) => fetchJson(`/api/demo/underwriting/tasks/${key}/complete`, { method: "POST", body: JSON.stringify(body) }),
  events: () => fetchJson("/api/demo/integration-events?limit=40"),
  config: () => fetchJson("/api/demo/config"),
};

let state = {
  token: localStorage.getItem(SESSION_KEY),
  user: null,
  applications: [],
  selectedId: null,
  pollTimer: null,
  /** @type {Record<string, { decision: string, reason: string }>} */
  uwDrafts: {},
  uwTasksLoadSeq: 0,
  kafkaIntegrationEnabled: false,
};

const els = {
  personaGrid: document.getElementById("personaGrid"),
  currentRole: document.getElementById("currentRole"),
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
  snackbarHost: document.getElementById("snackbarHost"),
};

init();

async function init() {
  els.refreshAppsBtn.addEventListener("click", loadApplications);
  els.refreshEventsBtn.addEventListener("click", loadEvents);
  bindIntegrationPathPanel();
  renderAllActions();
  try {
    await loadPortalConfig();
    await loadPersonasForOptionalSwitch();
    await ensureConductorSession();
    showWorkspace();
    await refreshAll();
    startPolling();
  } catch (e) {
    flash(e.message, "error");
    els.actionPanel.innerHTML =
      "<p class=\"muted\">Ensure general-workflow is running with <code>general-workflow.demo.enabled=true</code> and restart Spring Boot.</p>";
  }
}

function bindIntegrationPathPanel() {
  document.querySelectorAll('input[name="integrationChannel"]').forEach((input) => {
    input.addEventListener("change", () => {
      if (input.checked) {
        localStorage.setItem(INTEGRATION_CHANNEL_KEY, input.value);
        updateIntegrationPathUi();
      }
    });
  });
}

async function loadPortalConfig() {
  const cfg = await API.config();
  state.kafkaIntegrationEnabled = cfg.kafkaIntegrationEnabled === true;
  applyIntegrationPathDefaults();
}

function applyIntegrationPathDefaults() {
  const kafkaRadio = document.getElementById("integrationKafkaOption");
  const kafkaLabel = document.getElementById("integrationKafkaLabel");
  if (kafkaRadio) {
    kafkaRadio.disabled = !state.kafkaIntegrationEnabled;
  }
  if (kafkaLabel) {
    kafkaLabel.classList.toggle("disabled", !state.kafkaIntegrationEnabled);
    kafkaLabel.title = state.kafkaIntegrationEnabled
      ? ""
      : "Kafka integration is off on the server (KAFKA_INTEGRATION_ENABLED=false)";
  }
  const stored = localStorage.getItem(INTEGRATION_CHANNEL_KEY);
  let channel = "CAMUNDA_CLIENT";
  if (stored === "KAFKA" && state.kafkaIntegrationEnabled) {
    channel = "KAFKA";
  }
  const radio = document.querySelector(`input[name="integrationChannel"][value="${channel}"]`);
  if (radio) {
    radio.checked = true;
  }
  updateIntegrationPathUi();
}

function selectedIntegrationChannel() {
  const checked = document.querySelector('input[name="integrationChannel"]:checked');
  return checked?.value || "CAMUNDA_CLIENT";
}

function updateIntegrationPathUi() {
  const hint = document.getElementById("integrationPathHint");
  if (!hint) return;
  const ch = selectedIntegrationChannel();
  hint.textContent =
    ch === "KAFKA"
      ? "eDoc / eSign → Kafka topics → consumer → Camunda"
      : "eDoc / eSign → Camunda Client (direct correlate)";
  const esignBtn = document.getElementById("esignBtn");
  if (esignBtn) {
    esignBtn.textContent = ch === "KAFKA" ? "Publish eSignCompleted (Kafka)" : "Correlate eSignCompleted";
  }
}

function withIntegrationChannel(body) {
  return { ...body, integrationChannel: selectedIntegrationChannel() };
}

function flashIntegrationAction(label, response) {
  if (!response) {
    flash(label, "ok");
    return;
  }
  const outcome = response.outcome || response.delivery || "OK";
  const detail = response.detail ? ` — ${response.detail}` : "";
  const kind =
    outcome === "BUFFERED" || response.delivery === "CAMUNDA_BUFFERED" ? "warn" : "ok";
  flash(`${label}: ${outcome}${detail}`, kind);
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
  updateCurrentRoleDisplay();
  els.userBadge.textContent = state.user.role === "CONDUCTOR"
    ? "Conductor mode — all steps on this page"
    : `${state.user.displayName} · ${state.user.role} (limited actions)`;
  els.roleHint.textContent =
    "Complete the General Workflow flow top-to-bottom: start → eDoc → (auto credit/ECM) → underwriting if needed → eSign → funding. No sign-out required in conductor mode.";
}

function updateCurrentRoleDisplay() {
  if (!els.currentRole || !state.user) {
    return;
  }
  const roleText =
    state.user.role === "CONDUCTOR"
      ? "Conductor（全局演示）"
      : `${state.user.displayName} · ${state.user.role}`;
  els.currentRole.innerHTML = `<span class="role-label">当前角色</span>${escapeHtml(roleText)}`;
}

function renderAllActions() {
  const limited = state.user && state.user.role !== "CONDUCTOR";
  els.actionPanel.innerHTML = `
    <h2>Workflow steps</h2>
    <p class="muted small">Each block maps to a real Camunda message, user task, or worker. Mock integrations (credit bureau, ECM, core banking) run inside general-workflow workers.</p>

    <div class="step-block" data-step-block="submit">
      <div class="step-head"><span class="step-num">1</span><div><strong>Advisor</strong> — Start application</div></div>
      <div class="form-row">
        <label>Product<select id="appType"><option>General</option><option>LOC</option></select></label>
        <label>Path<select id="flowProfile"><option value="HAPPY_AUTO">Auto-approve (score 720)</option><option value="MANUAL_UNDERWRITING">Manual underwriting (score 640)</option></select></label>
        <label>Customer<input id="customerName" placeholder="Jamie Lee" /></label>
      </div>
      <fieldset class="checklist-builder" id="checklistBuilder">
        <legend class="small">Document checklist <span class="muted">(select 1–${DEMO_MAX_DOCS})</span></legend>
        <div class="checklist-options" id="checklistOptions"></div>
        <p class="muted small" id="checklistHint"></p>
      </fieldset>
      <button class="btn" type="button" id="startAppBtn">Start General Workflow process</button>
    </div>

    <div class="step-block" data-step-block="edoc">
      <div class="step-head"><span class="step-num">2</span><div><strong>Advisor / ECM</strong> — Document uploads</div></div>
      <p class="muted small">Upload each required document once. The process loops on <em>Wait for eDoc Upload</em> until every checklist item is verified.</p>
      <div id="docChecklistHost" class="doc-checklist muted small">Select an application to see its checklist.</div>
      <div class="form-row edoc-advanced">
        <label>Default status<select id="docStatus"><option value="UPLOADED">UPLOADED</option><option value="INVALID">INVALID (retry loop)</option></select></label>
      </div>
      <div class="btn-row">
        <button class="btn" type="button" id="edocUploadAllBtn">Upload all pending</button>
        <button class="btn ghost" type="button" id="edocBtn">Upload next pending only</button>
      </div>
    </div>

    <div class="step-block muted-step" data-step-block="credit">
      <div class="step-head"><span class="step-num">3</span><div><strong>System</strong> — Credit bureau, AML, ECM validation, DMN</div></div>
      <p class="muted small">Fully automatic after step 2. general-workflow <code>@JobWorker</code> must be connected to Zeebe (<code>:26500</code>). Usually finishes in a few seconds; then step 4 (manual path) or step 5 (auto path).</p>
      <p id="systemHintLine" class="muted small"></p>
    </div>

    <div class="step-block" data-step-block="uw">
      <div class="step-head"><span class="step-num">4</span><div><strong>Underwriter</strong> — Manual review (if DMN returns MANUAL_REVIEW)</div></div>
      <div id="uwTasksHost"><p class="muted small">Tasks appear when the process reaches manual underwriting.</p></div>
    </div>

    <div class="step-block" data-step-block="esign">
      <div class="step-head"><span class="step-num">5</span><div><strong>Customer</strong> — eSign completed</div></div>
      <button class="btn ghost" type="button" id="esignBtn">Correlate eSignCompleted</button>
    </div>

    <div class="step-block muted-step" data-step-block="fund">
      <div class="step-head"><span class="step-num">6</span><div><strong>System</strong> — Core banking funding &amp; advisor notification</div></div>
      <p class="muted small">Automatic after eSign. Process completes when workers finish.</p>
    </div>

    <div class="step-block highlight">
      <button class="btn" type="button" id="happyPathBtn">▶ Run happy path (auto)</button>
      <p class="muted small">Starts auto-approve case, uploads every pending eDoc, then eSign when ready. Stops at manual underwriting if that path applies.</p>
    </div>
  `;

  if (limited) {
    els.actionPanel.querySelectorAll(".step-block").forEach((b) => b.classList.add("dimmed"));
  }

  renderChecklistBuilderOptions();
  document.getElementById("appType")?.addEventListener("change", () => applyDefaultChecklistForProduct(false));
  document.getElementById("startAppBtn")?.addEventListener("click", startApplication);
  document.getElementById("edocBtn")?.addEventListener("click", () => sendEdocForNextPending());
  document.getElementById("edocUploadAllBtn")?.addEventListener("click", () => sendEdocUploadAll());
  document.getElementById("esignBtn")?.addEventListener("click", sendEsign);
  document.getElementById("happyPathBtn")?.addEventListener("click", runHappyPath);
  updateIntegrationPathUi();
}

function defaultChecklistForProduct(applicationType) {
  const t = (applicationType || "General").toUpperCase().replace(/\s+/g, "_");
  if (t === "LOC") {
    return ["ID", "Income"];
  }
  return [...DEMO_DOC_TYPES];
}

function renderChecklistBuilderOptions() {
  const host = document.getElementById("checklistOptions");
  if (!host) return;
  host.innerHTML = DEMO_DOC_TYPES.map(
    (type) =>
      `<label class="checklist-option"><input type="checkbox" name="reqDoc" value="${type}" /> ${escapeHtml(type)}</label>`
  ).join("");
  host.querySelectorAll('input[name="reqDoc"]').forEach((input) => {
    input.addEventListener("change", enforceChecklistSelectionLimit);
  });
  applyDefaultChecklistForProduct(true);
  enforceChecklistSelectionLimit();
}

function applyDefaultChecklistForProduct(force) {
  const product = document.getElementById("appType")?.value || "General";
  const defaults = new Set(defaultChecklistForProduct(product));
  document.querySelectorAll('#checklistOptions input[name="reqDoc"]').forEach((input) => {
    if (force || !document.querySelector('#checklistOptions input[name="reqDoc"]:checked')) {
      input.checked = defaults.has(input.value);
    }
  });
  updateChecklistBuilderHint();
}

function selectedChecklistFromForm() {
  const checked = [...document.querySelectorAll('#checklistOptions input[name="reqDoc"]:checked')].map(
    (el) => el.value
  );
  return checked;
}

function enforceChecklistSelectionLimit() {
  const checked = selectedChecklistFromForm();
  const boxes = [...document.querySelectorAll('#checklistOptions input[name="reqDoc"]')];
  if (checked.length >= DEMO_MAX_DOCS) {
    boxes.forEach((box) => {
      if (!box.checked) box.disabled = true;
    });
  } else {
    boxes.forEach((box) => {
      box.disabled = false;
    });
  }
  if (checked.length === 0) {
    const first = boxes[0];
    if (first) first.checked = true;
  }
  updateChecklistBuilderHint();
}

function updateChecklistBuilderHint() {
  const hint = document.getElementById("checklistHint");
  if (!hint) return;
  const n = selectedChecklistFromForm().length;
  hint.textContent = `${n} document${n === 1 ? "" : "s"} selected (max ${DEMO_MAX_DOCS}).`;
}

async function runHappyPath() {
  document.getElementById("flowProfile").value = "HAPPY_AUTO";
  await startApplication();
  const appId = state.selectedId;
  flash("Happy path started for " + appId + " — waiting for eDoc stage…", "ok");

  await waitForStage(appId, (s) => String(s.currentStage).includes("eDoc"), 120);
  await correlateAllPendingEdocs(appId);
  flash("All required eDocs sent — waiting for credit/risk/eSign stage…", "ok");

  await waitForStage(appId, (s) => String(s.currentStage).includes("eSign"), 180);
  if ((await API.status(appId)).currentStage.includes("Manual")) {
    flash("Manual underwriting required — complete step 4, then eSign.", "ok");
    return;
  }
  await API.esign(appId, withIntegrationChannel({}));
  flash("eSign sent — funding should complete shortly.", "ok");
  await refreshAll();
}

async function waitForStage(appId, predicate, maxSeconds) {
  for (let i = 0; i < maxSeconds; i += 2) {
    await sleep(2000);
    const s = await API.status(appId);
    await loadStatus();
    if (predicate(s)) return s;
    if (s.processState === "COMPLETED") return s;
  }
  throw new Error("Timed out waiting for next stage");
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function startApplication() {
  try {
    const requiredDocuments = selectedChecklistFromForm();
    if (!requiredDocuments.length) {
      flash("Select at least one document for the checklist.", "error");
      return;
    }
    if (requiredDocuments.length > DEMO_MAX_DOCS) {
      flash(`Checklist supports at most ${DEMO_MAX_DOCS} documents.`, "error");
      return;
    }
    const body = {
      applicationType: document.getElementById("appType").value,
      flowProfile: document.getElementById("flowProfile").value,
      customerName: document.getElementById("customerName").value || null,
      requiredDocuments,
    };
    const app = await API.startApp(body);
    flash("Started " + app.applicationId, "ok");
    state.selectedId = app.applicationId;
    await refreshAll();
  } catch (e) {
    flash(e.message, "error");
  }
}

function pendingList(vars) {
  const p = vars?.pendingDocuments;
  if (Array.isArray(p)) return p;
  if (typeof p === "string" && p.startsWith("[")) {
    try {
      return JSON.parse(p);
    } catch {
      return [];
    }
  }
  return [];
}

function completedStepsList(vars) {
  const raw = vars?.completedSteps;
  if (Array.isArray(raw)) return raw.map(String);
  if (typeof raw === "string" && raw.startsWith("[")) {
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }
  if (typeof raw === "string" && raw.trim()) {
    return raw.split(",").map((s) => s.trim()).filter(Boolean);
  }
  return [];
}

function verifiedList(vars) {
  const v = vars?.verifiedDocuments;
  if (Array.isArray(v)) return v;
  if (typeof v === "string" && v.startsWith("[")) {
    try {
      return JSON.parse(v);
    } catch {
      return [];
    }
  }
  return [];
}

function requiredList(vars) {
  const raw = vars?.requiredDocuments;
  if (Array.isArray(raw)) return raw.map(String);
  if (typeof raw === "string" && raw.includes(",")) {
    return raw.split(",").map((s) => s.trim()).filter(Boolean);
  }
  if (typeof raw === "string" && raw.trim()) {
    return [raw.trim()];
  }
  const pending = pendingList(vars);
  const verified = verifiedList(vars);
  const merged = [...verified];
  pending.forEach((p) => {
    if (!merged.includes(p)) merged.push(p);
  });
  return merged.length ? merged : [...DEMO_DOC_TYPES];
}

function docUploadStatus(type, vars) {
  const pending = pendingList(vars);
  const verified = verifiedList(vars);
  if (verified.includes(type)) return "verified";
  if (pending.includes(type)) return "pending";
  return "optional";
}

function renderDocChecklist(vars) {
  const host = document.getElementById("docChecklistHost");
  if (!host) return;
  if (!state.selectedId) {
    host.innerHTML = "<span class='muted'>Select an application to see its checklist.</span>";
    return;
  }
  const required = requiredList(vars);
  const statusSelect = document.getElementById("docStatus");
  const defaultStatus = statusSelect?.value || "UPLOADED";
  host.innerHTML = required
    .map((type) => {
      const st = docUploadStatus(type, vars);
      const badge =
        st === "verified"
          ? '<span class="doc-badge verified">Verified</span>'
          : st === "pending"
            ? '<span class="doc-badge pending">Pending</span>'
            : '<span class="doc-badge">—</span>';
      const disabled = st === "verified" ? " disabled" : "";
      return `<div class="doc-checklist-row" data-doc-type="${escapeHtml(type)}">
        <div class="doc-checklist-main"><strong>${escapeHtml(type)}</strong> ${badge}</div>
        <button class="btn ghost small doc-upload-btn" type="button" data-upload-doc="${escapeHtml(type)}"${disabled}>Upload</button>
      </div>`;
    })
    .join("");
  host.querySelectorAll("[data-upload-doc]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const type = btn.getAttribute("data-upload-doc");
      await sendEdocForType(type, defaultStatus);
    });
  });
}

function currentDocStatus() {
  return document.getElementById("docStatus")?.value || "UPLOADED";
}

async function correlateAllPendingEdocs(appId, documentStatus = "UPLOADED") {
  for (let round = 0; round < 12; round++) {
    const s = await API.status(appId);
    if (s.variables?.edocsVerified === true) return;
    const pending = pendingList(s.variables);
    if (!pending.length) {
      await sleep(1500);
      continue;
    }
    for (const documentType of [...pending]) {
      await API.edoc(
        appId,
        withIntegrationChannel({
          documentType,
          documentId: null,
          documentStatus,
        })
      );
      await sleep(1800);
      const after = await API.status(appId);
      if (after.variables?.edocsVerified === true) return;
    }
  }
}

async function sendEdocForType(documentType, documentStatus) {
  requireSelection();
  const edocResponse = await API.edoc(
    state.selectedId,
    withIntegrationChannel({
      documentId: null,
      documentStatus: documentStatus || "UPLOADED",
      documentType,
    })
  );
  flashIntegrationAction(`eDoc ${documentType} (${selectedIntegrationChannel()})`, edocResponse);
  await refreshAll();
}

async function sendEdocForNextPending() {
  requireSelection();
  const s = await API.status(state.selectedId);
  const pending = pendingList(s.variables);
  if (!pending.length) {
    flash("No pending documents — checklist complete or not at eDoc stage.", "ok");
    return;
  }
  await sendEdocForType(pending[0], currentDocStatus());
}

async function sendEdocUploadAll() {
  requireSelection();
  await correlateAllPendingEdocs(state.selectedId, currentDocStatus());
  flash("Uploaded all pending checklist items.", "ok");
  await refreshAll();
}

async function sendEsign() {
  requireSelection();
  const esignResponse = await API.esign(state.selectedId, withIntegrationChannel({}));
  flashIntegrationAction(`eSign (${selectedIntegrationChannel()})`, esignResponse);
  await refreshAll();
}

function taskKeyString(task) {
  const k = task.userTaskKey;
  return typeof k === "string" ? k : String(k);
}

function persistUwDraftFromCard(card) {
  const key = card.getAttribute("data-key");
  if (!key) return;
  state.uwDrafts[key] = {
    decision: card.querySelector(".uwDecision")?.value || "APPROVED",
    reason: card.querySelector(".uwReason")?.value ?? "",
  };
}

function captureUwDrafts(host) {
  if (!host) return;
  host.querySelectorAll(".task-card[data-key]").forEach((card) => persistUwDraftFromCard(card));
}

function applyUwDraftsToDom(host) {
  if (!host) return;
  host.querySelectorAll(".task-card[data-key]").forEach((card) => {
    const key = card.getAttribute("data-key");
    const draft = state.uwDrafts[key];
    if (!draft) return;
    const sel = card.querySelector(".uwDecision");
    const ta = card.querySelector(".uwReason");
    if (sel && document.activeElement !== sel && sel.value !== draft.decision) {
      sel.value = draft.decision;
    }
    if (ta && document.activeElement !== ta && ta.value !== draft.reason) {
      ta.value = draft.reason;
    }
  });
}

function bindUwDraftPersistence(host) {
  host.querySelectorAll(".task-card[data-key]").forEach((card) => {
    const persist = () => persistUwDraftFromCard(card);
    card.querySelector(".uwDecision")?.addEventListener("change", persist);
    card.querySelector(".uwDecision")?.addEventListener("input", persist);
    card.querySelector(".uwReason")?.addEventListener("input", persist);
  });
}

async function loadUwTasks() {
  const host = document.getElementById("uwTasksHost");
  if (!host) return;
  const loadSeq = ++state.uwTasksLoadSeq;
  try {
    const tasks = await API.uwTasks();
    if (loadSeq !== state.uwTasksLoadSeq) {
      return;
    }
    if (!tasks.length) {
      host.innerHTML = "<p class='muted small'>No open underwriting tasks.</p>";
      return;
    }
    const taskKeys = tasks.map((t) => taskKeyString(t)).sort().join(",");
    const existingKeys = [...host.querySelectorAll(".task-card[data-key]")]
      .map((c) => c.getAttribute("data-key"))
      .sort()
      .join(",");
    if (taskKeys === existingKeys && host.querySelector(".task-card")) {
      applyUwDraftsToDom(host);
      return;
    }
    captureUwDrafts(host);
    host.innerHTML = tasks
      .map((t) => {
        const key = taskKeyString(t);
        const draft = state.uwDrafts[key] || { decision: "APPROVED", reason: "" };
        const approveSel = draft.decision === "APPROVED" ? " selected" : "";
        const rejectSel = draft.decision === "REJECTED" ? " selected" : "";
        return `
      <div class="task-card" data-key="${key}">
        <strong>${escapeHtml(t.name)}</strong>
        <div class="muted small">${escapeHtml(t.businessId || "—")} · #${t.userTaskKey}</div>
        <div class="form-row">
          <label>Decision<select class="uwDecision"><option value="APPROVED"${approveSel}>Approve</option><option value="REJECTED"${rejectSel}>Reject</option></select></label>
          <label>Reason<textarea class="uwReason" rows="2">${escapeHtml(draft.reason)}</textarea></label>
        </div>
        <button class="btn" type="button" data-complete="${key}">Submit</button>
      </div>`;
      })
      .join("");
    bindUwDraftPersistence(host);
    host.querySelectorAll("[data-complete]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const card = btn.closest(".task-card");
        const taskKey = btn.getAttribute("data-complete");
        await API.completeUw(taskKey, {
          underwritingDecision: card.querySelector(".uwDecision").value,
          rejectionReason: card.querySelector(".uwReason").value || null,
        });
        delete state.uwDrafts[taskKey];
        flash("Underwriting completed", "ok");
        await refreshAll();
      });
    });
  } catch (e) {
    flash(e.message, "error");
    host.innerHTML = "<p class=\"muted small\">Could not load underwriting tasks.</p>";
  }
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
    });
    els.applicationList.appendChild(btn);
  });
}

async function loadStatus() {
  if (!state.selectedId) {
    els.statusDetail.innerHTML = "<span class='muted'>Start step 1 or select an application.</span>";
    updatePipeline(null);
    applyStepBlockStates(computeStepBlockStates(null));
    return;
  }
  let s;
  try {
    s = await API.status(state.selectedId);
  } catch (e) {
    flash(e.message, "error");
    els.statusDetail.innerHTML = "<span class=\"muted\">Could not load status.</span>";
    if (String(e.message).includes("Unknown demo application")) {
      state.selectedId = null;
    }
    return;
  }
  const vars = s.variables || {};
  const jobs = (s.activeJobs || []).map((j) => j.type + " (" + j.state + ")").join(", ") || "—";
  const failed = (s.failedJobs || []).length ? (s.failedJobs.map((j) => j.type).join(", ")) : "—";
  const inc = (s.incidents || []).length ? s.incidents.map((i) => i.errorMessage).join("; ") : "—";

  els.statusDetail.innerHTML = `
    <p><span class="stage-pill">${escapeHtml(s.currentStage)}</span></p>
    ${s.systemHint ? `<p class="status-hint ${s.processState === "UNAVAILABLE" ? "error" : "ok"}">${escapeHtml(s.systemHint)}</p>` : ""}
    <dl>
      <dt>Process</dt><dd>${escapeHtml(s.processState)}</dd>
      <dt>Credit</dt><dd>${escapeHtml(String(vars.creditScore ?? "—"))}</dd>
      <dt>AML clear</dt><dd>${escapeHtml(String(vars.amlClear ?? "—"))}</dd>
      <dt>Risk (DMN)</dt><dd>${escapeHtml(String(vars.riskDecision ?? "—"))}</dd>
      <dt>Required checklist</dt><dd>${escapeHtml(requiredList(vars).join(", ") || "—")}</dd>
      <dt>Pending docs</dt><dd>${escapeHtml(pendingList(vars).join(", ") || "—")}</dd>
      <dt>Verified docs</dt><dd>${escapeHtml(verifiedList(vars).join(", ") || "—")}</dd>
      <dt>eDocs verified</dt><dd>${escapeHtml(String(vars.edocsVerified ?? "—"))}</dd>
      <dt>Underwriting</dt><dd>${escapeHtml(String(vars.underwritingDecision ?? "—"))}</dd>
      <dt>Active jobs</dt><dd>${escapeHtml(jobs)}</dd>
      <dt>Failed jobs</dt><dd>${escapeHtml(failed)}</dd>
      <dt>Incidents</dt><dd>${escapeHtml(inc)}</dd>
    </dl>`;

  const hintLine = document.getElementById("systemHintLine");
  if (hintLine && s.systemHint) {
    hintLine.textContent = s.systemHint;
  }

  renderDocChecklist(vars);
  updatePipeline(s);
  applyStepBlockStates(computeStepBlockStates(s));
}

function failedJobTypeSet(status) {
  return new Set((status?.failedJobs || []).map((job) => job.type));
}

function stepHasFailedJob(step, status) {
  const types = STEP_JOB_TYPES[step] || [];
  const failed = failedJobTypeSet(status);
  return types.some((type) => failed.has(type));
}

function inferCurrentWorkflowStep(status) {
  if (!status || status.processState === "COMPLETED") {
    return null;
  }
  const stage = status.currentStage || "";
  if (stage.includes("Rejected") || stage.includes("Underwriting rejected")) {
    return "uw";
  }
  if (stage.includes("eDoc")) {
    return "edoc";
  }
  if (stage.includes("Manual") || stage.includes("Underwriting")) {
    return "uw";
  }
  if (stage.includes("eSign")) {
    return "esign";
  }
  if (stage.includes("Funding")) {
    return "fund";
  }
  if (stage.includes("Credit") || stage.includes("AML") || stage.includes("risk") || stage.includes("DMN")) {
    return "credit";
  }
  const v = status.variables || {};
  if (v.edocsVerified !== true) {
    return "edoc";
  }
  if (v.riskDecision == null) {
    return "credit";
  }
  if (v.riskDecision === "MANUAL_REVIEW" && v.underwritingDecision == null) {
    return "uw";
  }
  if (!completedStepsList(v).includes("FUNDING_INITIATED")) {
    return "esign";
  }
  return "fund";
}

function isUnderwritingSkipped(vars) {
  return vars.riskDecision === "AUTO_APPROVE";
}

function isStepMilestoneDone(step, status, vars) {
  switch (step) {
    case "submit":
      return Boolean(status);
    case "edoc":
      return vars.edocsVerified === true;
    case "credit":
      return vars.riskDecision != null && vars.riskDecision !== "";
    case "uw":
      return vars.underwritingDecision != null && vars.underwritingDecision !== "";
    case "esign":
      return (
        completedStepsList(vars).includes("FUNDING_INITIATED") ||
        String(vars.eSignStatus || "").toUpperCase() === "COMPLETED"
      );
    case "fund":
      return status.processState === "COMPLETED";
    default:
      return false;
  }
}

function stepFailed(step, status, vars) {
  if (stepHasFailedJob(step, status)) {
    return true;
  }
  if (step === "uw" && String(vars.underwritingDecision || "").toUpperCase() === "REJECTED") {
    return true;
  }
  if (step === "fund" && String(vars.fundingFailureReason || "").trim()) {
    return true;
  }
  if (step === "fund" && (status.incidents || []).length > 0 && status.processState !== "COMPLETED") {
    return stepHasFailedJob("fund", status);
  }
  return false;
}

function computeStepBlockStates(status) {
  const states = Object.fromEntries(WORKFLOW_STEP_ORDER.map((step) => [step, "pending"]));
  if (!status) {
    states.submit = state.selectedId ? "done" : "current";
    return states;
  }

  const vars = status.variables || {};
  const current = inferCurrentWorkflowStep(status);
  const currentIndex = current ? WORKFLOW_STEP_ORDER.indexOf(current) : WORKFLOW_STEP_ORDER.length;

  for (const step of WORKFLOW_STEP_ORDER) {
    const index = WORKFLOW_STEP_ORDER.indexOf(step);

    if (stepFailed(step, status, vars)) {
      states[step] = "failed";
      continue;
    }

    if (step === "uw" && isUnderwritingSkipped(vars) && isStepMilestoneDone("credit", status, vars)) {
      states[step] = "skipped";
      continue;
    }

    if (isStepMilestoneDone(step, status, vars)) {
      states[step] = "done";
      continue;
    }

    if (current && step === current) {
      states[step] = "current";
      continue;
    }

    if (index < currentIndex) {
      states[step] = step === "uw" && isUnderwritingSkipped(vars) ? "skipped" : "done";
    }
  }

  return states;
}

function applyStepBlockStates(states) {
  document.querySelectorAll("[data-step-block]").forEach((el) => {
    const key = el.getAttribute("data-step-block");
    if (!key || !states[key]) {
      return;
    }
    el.setAttribute("data-step-state", states[key]);
  });
}

function updatePipeline(status) {
  const items = els.pipelineSteps.querySelectorAll("li");
  items.forEach((li) => li.classList.remove("active", "done"));
  if (!status) return;
  const stage = status.currentStage || "";
  const v = status.variables || {};
  const doneFlags = {
    submit: true,
    edoc: v.edocsVerified === true,
    credit: v.riskDecision != null,
    uw: v.underwritingDecision != null || v.riskDecision === "AUTO_APPROVE",
    esign:
      completedStepsList(v).includes("FUNDING_INITIATED") || stage.includes("Funding"),
    fund: status.processState === "COMPLETED",
  };
  Object.entries(doneFlags).forEach(([key, done]) => {
    const li = els.pipelineSteps.querySelector(`[data-step="${key}"]`);
    if (li && done) li.classList.add("done");
  });
  const active = [...items].find((li) => !li.classList.contains("done"));
  if (active) active.classList.add("active");
}

async function loadEvents() {
  const events = await API.events();
  els.integrationFeed.innerHTML = events.map((e) =>
    `<div class="event"><span class="muted">${new Date(e.at).toLocaleTimeString()}</span> · <strong>${escapeHtml(e.source)}</strong> ${escapeHtml(e.action)} <span class="muted">${escapeHtml(e.applicationId)}</span></div>`
  ).join("");
}

async function refreshAll() {
  await loadApplications();
  await loadStatus();
  await loadEvents();
  await loadUwTasks();
}

function startPolling() {
  if (state.pollTimer) clearInterval(state.pollTimer);
  els.pollIndicator.textContent = "Auto-refresh 3s";
  state.pollTimer = setInterval(() => {
    refreshAll().catch((e) => {
      if (String(e.message).includes("Unknown demo application")) {
        state.selectedId = null;
      }
    });
  }, 3000);
}

function requireSelection() {
  if (!state.selectedId) throw new Error("Select an application (or start step 1)");
}

function snackbarHostEl() {
  return document.getElementById("snackbarHost");
}

function flash(message, kind) {
  const host = snackbarHostEl();
  if (!host) {
    return;
  }
  host.replaceChildren();
  const el = document.createElement("div");
  el.className = `snackbar ${kind === "error" ? "error" : "ok"}`;
  el.setAttribute("role", "status");
  el.textContent = message;
  host.appendChild(el);
  const dismissMs = 5000;
  window.setTimeout(() => {
    el.style.opacity = "0";
    el.style.transition = "opacity 0.2s ease";
    window.setTimeout(() => el.remove(), 220);
  }, dismissMs);
}

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
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
    const msg =
      (data && (data.detail || data.message || data.title || data.error)) ||
      res.statusText ||
      "Request failed";
    if (res.status === 400 && String(msg).toLowerCase().includes("header")) {
      throw new Error("Session missing — hard refresh the page (Cmd+Shift+R).");
    }
    if (res.status === 400 || res.status === 404 || res.status === 502) {
      throw new Error(
        msg + " — Is Camunda running on :8080 / :26500 and general-workflow deployed?"
      );
    }
    throw new Error(msg);
  }
  return data;
}
