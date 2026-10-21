const SESSION_KEY = "application-workflow.demo.session";
const API = {
  conductor: () => fetchJson("/api/demo/auth/conductor", { method: "POST" }),
  login: (body) => fetchJson("/api/demo/auth/login", { method: "POST", body: JSON.stringify(body) }),
  personas: () => fetchJson("/api/demo/auth/personas"),
  applications: () => fetchJson("/api/demo/applications"),
  startApp: (body) => fetchJson("/api/demo/applications", { method: "POST", body: JSON.stringify(body) }),
  status: (id) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/status`),
  edoc: (id, body) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/edoc-upload`, { method: "POST", body: JSON.stringify(body) }),
  esign: (id) => fetchJson(`/api/demo/applications/${encodeURIComponent(id)}/esign-complete`, { method: "POST" }),
  uwTasks: () => fetchJson("/api/demo/underwriting/tasks"),
  completeUw: (key, body) => fetchJson(`/api/demo/underwriting/tasks/${key}/complete`, { method: "POST", body: JSON.stringify(body) }),
  events: () => fetchJson("/api/demo/integration-events?limit=40"),
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
};

init();

async function init() {
  els.refreshAppsBtn.addEventListener("click", loadApplications);
  els.refreshEventsBtn.addEventListener("click", loadEvents);
  renderAllActions();
  try {
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
  updateCurrentRoleDisplay();
  els.userBadge.textContent = state.user.role === "CONDUCTOR"
    ? "Conductor mode — all steps on this page"
    : `${state.user.displayName} · ${state.user.role} (limited actions)`;
  els.roleHint.textContent =
    "Complete the mortgage flow top-to-bottom: start → eDoc → (auto credit/ECM) → underwriting if needed → eSign → funding. No sign-out required in conductor mode.";
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
    <p class="muted small">Each block maps to a real Camunda message, user task, or worker. Mock integrations (credit bureau, ECM, core banking) run inside application-workflow workers.</p>

    <div class="step-block" data-step-block="submit">
      <div class="step-head"><span class="step-num">1</span><div><strong>Advisor</strong> — Start application</div></div>
      <div class="form-row">
        <label>Product<select id="appType"><option>Mortgage</option><option>LOC</option></select></label>
        <label>Path<select id="flowProfile"><option value="HAPPY_AUTO">Auto-approve (score 720)</option><option value="MANUAL_UNDERWRITING">Manual underwriting (score 640)</option></select></label>
        <label>Customer<input id="customerName" placeholder="Jamie Lee" /></label>
      </div>
      <button class="btn" type="button" id="startAppBtn">Start mortgage application process</button>
    </div>

    <div class="step-block" data-step-block="edoc">
      <div class="step-head"><span class="step-num">2</span><div><strong>Advisor / ECM</strong> — Document upload event</div></div>
      <div class="form-row">
        <label>Document ID<input id="docId" placeholder="auto if empty" /></label>
        <label>Status<select id="docStatus"><option value="UPLOADED">UPLOADED</option><option value="INVALID">INVALID (retry loop)</option></select></label>
      </div>
      <button class="btn ghost" type="button" id="edocBtn">Correlate eDocUploaded</button>
    </div>

    <div class="step-block muted-step" data-step-block="credit">
      <div class="step-head"><span class="step-num">3</span><div><strong>System</strong> — Credit bureau, AML, ECM validation, DMN</div></div>
      <p class="muted small">Fully automatic after step 2. application-workflow <code>@JobWorker</code> must be connected to Zeebe (<code>:26500</code>). Usually finishes in a few seconds; then step 4 (manual path) or step 5 (auto path).</p>
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

    <div class="step-block muted-step">
      <div class="step-head"><span class="step-num">6</span><div><strong>System</strong> — Core banking funding &amp; advisor notification</div></div>
      <p class="muted small">Automatic after eSign. Process completes when workers finish.</p>
    </div>

    <div class="step-block highlight">
      <button class="btn" type="button" id="happyPathBtn">▶ Run happy path (auto)</button>
      <p class="muted small">Starts auto-approve case, sends eDoc + eSign when ready (polls every 2s). Stops at manual underwriting if that path applies.</p>
    </div>
  `;

  if (limited) {
    els.actionPanel.querySelectorAll(".step-block").forEach((b) => b.classList.add("dimmed"));
  }

  document.getElementById("startAppBtn")?.addEventListener("click", startApplication);
  document.getElementById("edocBtn")?.addEventListener("click", sendEdoc);
  document.getElementById("esignBtn")?.addEventListener("click", sendEsign);
  document.getElementById("happyPathBtn")?.addEventListener("click", runHappyPath);
}

async function runHappyPath() {
  document.getElementById("flowProfile").value = "HAPPY_AUTO";
  await startApplication();
  const appId = state.selectedId;
  flash(els.actionPanel, "Happy path started for " + appId + " — waiting for eDoc stage…", "ok");

  await waitForStage(appId, (s) => !String(s.currentStage).includes("eDoc") ? false : true, 120);
  await API.edoc(appId, { documentId: null, documentStatus: "UPLOADED" });
  flash(els.actionPanel, "eDoc sent — waiting for credit/risk/eSign stage…", "ok");

  await waitForStage(appId, (s) => String(s.currentStage).includes("eSign"), 180);
  if ((await API.status(appId)).currentStage.includes("Manual")) {
    flash(els.actionPanel, "Manual underwriting required — complete step 4, then eSign.", "ok");
    return;
  }
  await API.esign(appId);
  flash(els.actionPanel, "eSign sent — funding should complete shortly.", "ok");
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
    const body = {
      applicationType: document.getElementById("appType").value,
      flowProfile: document.getElementById("flowProfile").value,
      customerName: document.getElementById("customerName").value || null,
    };
    const app = await API.startApp(body);
    flash(els.actionPanel, "Started " + app.applicationId, "ok");
    state.selectedId = app.applicationId;
    await refreshAll();
  } catch (e) {
    flash(els.actionPanel, e.message, "error");
  }
}

async function sendEdoc() {
  requireSelection();
  await API.edoc(state.selectedId, {
    documentId: document.getElementById("docId")?.value || null,
    documentStatus: document.getElementById("docStatus")?.value || "UPLOADED",
  });
  flash(els.actionPanel, "eDoc correlated", "ok");
  await refreshAll();
}

async function sendEsign() {
  requireSelection();
  await API.esign(state.selectedId);
  flash(els.actionPanel, "eSign correlated", "ok");
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
        flash(host, "Underwriting completed", "ok");
        await refreshAll();
      });
    });
  } catch (e) {
    host.innerHTML = `<div class="flash error">${escapeHtml(e.message)}</div>`;
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
    return;
  }
  let s;
  try {
    s = await API.status(state.selectedId);
  } catch (e) {
    els.statusDetail.innerHTML = `<div class="flash error">${escapeHtml(e.message)}</div>`;
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
    ${s.systemHint ? `<p class="flash ${s.processState === "UNAVAILABLE" ? "error" : "ok"}" style="margin:0.5rem 0">${escapeHtml(s.systemHint)}</p>` : ""}
    <dl>
      <dt>Process</dt><dd>${escapeHtml(s.processState)}</dd>
      <dt>Credit</dt><dd>${escapeHtml(String(vars.creditScore ?? "—"))}</dd>
      <dt>AML clear</dt><dd>${escapeHtml(String(vars.amlClear ?? "—"))}</dd>
      <dt>Risk (DMN)</dt><dd>${escapeHtml(String(vars.riskDecision ?? "—"))}</dd>
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

  updatePipeline(s);
  highlightNextStep(s);
}

function highlightNextStep(status) {
  document.querySelectorAll("[data-step-block]").forEach((el) => el.classList.remove("next-step"));
  if (!status || status.processState === "COMPLETED") return;
  const stage = status.currentStage || "";
  let block = "submit";
  if (stage.includes("eDoc")) block = "edoc";
  else if (stage.includes("Credit") || stage.includes("AML") || stage.includes("risk") || stage.includes("DMN")) block = "credit";
  else if (stage.includes("Manual")) block = "uw";
  else if (stage.includes("eSign")) block = "esign";
  const el = document.querySelector(`[data-step-block="${block}"]`);
  if (el) el.classList.add("next-step");
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
    esign: String(v.completedSteps || "").includes("FUNDING") || stage.includes("Funding"),
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

function flash(container, message, kind) {
  const el = document.createElement("div");
  el.className = `flash ${kind}`;
  el.textContent = message;
  container.prepend(el);
  setTimeout(() => el.remove(), 5000);
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
        msg + " — Is Camunda running on :8080 / :26500 and application-workflow deployed?"
      );
    }
    throw new Error(msg);
  }
  return data;
}
