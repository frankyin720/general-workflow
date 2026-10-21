# application-workflow Demo Portal — walkthrough

Open **http://localhost:8081/** (Camunda c8run on 8080/26500 and `./mvnw spring-boot:run`).

**Default: conductor mode** — one screen shows all eight workflow steps plus a *Human tasks* block for every open review. Optional **Role simulation** (header menu) signs in as a persona; password **`demo`**.

## Personas

| User | Role | Camunda groups | Responsibility |
|------|------|----------------|----------------|
| `advisor.smith` | Financial Advisor | `Advisor` | Start applications, upload eDocs, decide on document / commitment expiry |
| `customer.lee` | Customer | — | eSign, decline the offer, withdraw |
| `underwriter.jones` | Bank Underwriter | `Bank Underwriter`, `Senior Underwriter` | Manual underwriting decisions |
| `compliance.khan` | Compliance Officer | `Compliance` | Pre-screening exception review (AML hit, bureau timeout) |
| `ecm.bot` | ECM System | — | Document upload / appraisal events (Kafka substitute) |
| `ops.admin` | Operations | `Operations` | Manual document review, appraisal follow-up, closing delay, funding conditions / intervention |

## Scenarios (step 1 → *Scenario*)

| Profile | What it demonstrates |
|---------|----------------------|
| `HAPPY_AUTO` | Score 740, clean AML, LTV 80 % → straight-through auto approval |
| `MANUAL_UNDERWRITING` | Score 650 → DMN `MANUAL_REVIEW` → underwriter task (48 h SLA escalation, senior reassignment after 5 days) |
| `AML_HIT_COMPLIANCE` | AML hit → DMN `MANUAL_REVIEW` → Compliance task (24 h SLA) before documentation |
| `CREDIT_HARD_DECLINE` | Bureau hard decline (BPMN error) → `DECLINE` → adverse action notice |
| `ECM_FALLBACK` | ECM hard failure → Operations manual document review |
| `COMMITMENT_LETTER_FAILURE` | Letter generation fails → Operations resolves → regenerated |
| `FUNDING_FAILURE` | First disbursement fails → reversal → manual intervention → retry succeeds |
| `TECHNICAL_FAILURE` | Every integration fails with retries/backoff → incident visible in Operate |

## Happy path

1. **Start** (`HAPPY_AUTO`). Phase 1 runs automatically: file creation, credit + AML in parallel, eligibility DMN → `PROCEED`.
2. Stage shows *Waiting for financial eDocs and property appraisal (parallel)*.
3. **eDoc** — leave *Document type* on *All required docs* (or send `NOA`, `T4`, `EMPLOYMENT_LETTER` one by one) → ECM validation → checklist.
4. **Appraisal** — *Correlate appraisalCompletedEvent* (optionally with an appraised value) → LTV.
5. GDS/TDS are stress-tested at max(contract + 2 %, 5.25 %); underwriting DMN → `AUTO_APPROVE`; status `APPROVED`.
6. Commitment letter generated and sent; **Customer** — *Correlate eSignCompletedEvent*.
7. Solicitor instructions sent; **Solicitor** — *Correlate closingDayConfirmedEvent*.
8. Funding readiness DMN → `FUND` → disbursement → account activation → *Mortgage funded & active*.

Or press **▶ Run happy path (auto)**, which drives steps 1–8 automatically and stops if a human decision is required.

## Human tasks

Every timeout / exception creates a task instead of stalling the instance. Open tasks for the selected application appear in the **Human tasks** block with the matching decision form:

| Task | Group | Decision variable |
|------|-------|-------------------|
| Pre-screening exception review | Compliance | `complianceDecision` = CLEARED / DECLINED |
| Manual document review (ECM fallback) | Operations | `documentValid` = true / false |
| Documents overdue 30 d | Advisor | `documentExpiryDecision` = EXTEND / CANCEL |
| Appraisal overdue 10 d | Operations | `appraisalFollowUpDecision` = WAIT / CANCEL |
| Manual / senior underwriting review | Bank Underwriter / Senior Underwriter | `underwritingDecision` = APPROVED / DECLINED, `approvalConditionsText` |
| Resolve commitment letter issue | Operations | (complete to regenerate) |
| Commitment expired 14 d | Advisor | `commitmentExpiryDecision` = EXTEND / WITHDRAW |
| Resolve closing delay 45 d | Operations | `closingDelayDecision` = WAIT / CANCEL |
| Clear funding conditions | Operations | `outstandingConditions`, `insuranceConfirmed`, `fundingHold` |
| Manual funding intervention | Operations | `fundingInterventionDecision` = RETRY / ABORT |

Timers are production-like (hours/days). To demo escalation quickly, shorten the ISO durations in the BPMN (e.g. `PT24H` → `PT1M`) and redeploy.

## Other paths

- **Invalid document** — eDoc with status `INVALID` → advisor notified → back to waiting for upload.
- **Client declines offer** — *Client declines offer* in step 6 → `CLIENT_DECLINED`, process ends.
- **Withdrawal** — *Withdraw application* at any time → global event sub-process cancels the instance (`WITHDRAWN`).
- **Decline** — every decline path (pre-screening, compliance, DMN, underwriter) records `declineStage` / `declineReason` and sends an adverse action notice.

## Configuration

- `application-workflow.demo.enabled` / `DEMO_PORTAL_ENABLED` (default `true`) — disables `/api/demo/*` and the static portal when `false`.
- The portal uses the same Camunda messages as `POST /api/workflow/messages/*` (see `docs/kafka-camunda-integration.md`) through `CamundaMessageBridge` (buffered publish when the catch is not yet active).
