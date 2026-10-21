# Mortgage Workflow Demo Portal — role walkthrough

Open **http://localhost:8081/** (with Camunda c8run on 8080 and `./mvnw spring-boot:run`).

**Default: conductor mode** — one screen lists all workflow steps (1–6). No sign-out / persona switching required.

Optional **Role simulation** (header menu) still available for training; password **`demo`**.

## Personas

| User | Role | Responsibility |
|------|------|----------------|
| `advisor.smith` | Financial Advisor | Start applications, upload eDocs |
| `customer.lee` | Customer | Complete eSign |
| `underwriter.jones` | Bank Underwriter | Manual underwriting (Camunda group `Bank Underwriter`) |
| `ecm.bot` | ECM System | Emit document upload events (Kafka substitute) |
| `ops.admin` | Operations | Monitor all cases + integration feed |

## Happy path (auto approve)

1. **Advisor** — Start application, profile **Happy path — auto approve** (credit score 720).
2. Wait until status shows **Waiting for eDoc upload** (a few seconds).
3. **Advisor** or **ECM** — **Send eDoc uploaded** for the selected `applicationId`.
4. System runs mock **credit bureau** (worker), **ECM validation**, **DMN** → auto approve.
5. **Customer** — **Mark eSign completed**.
6. Mock **core banking funding** runs; status becomes **Funded / completed**.

## Manual underwriting path

1. **Advisor** — Start with profile **Manual underwriting** (credit score 640 → DMN `MANUAL_REVIEW`).
2. Upload eDoc as above.
3. **Underwriter** — Open task, **Approve** or **Reject**.
4. If approved, **Customer** eSign → funding as above.

## Invalid document loop

On eDoc upload, set status **INVALID**. ECM worker sets `documentValid=false`; flow notifies advisor and returns to **Wait for eDoc Upload**. Re-upload with **UPLOADED**.

## Configuration

- `application-workflow.demo.enabled` / `DEMO_PORTAL_ENABLED` (default `true`) — disables `/api/demo/*` and static portal when `false`.
- Real Kafka path unchanged; portal calls the same Camunda messages as `POST /api/workflow/messages/*` with buffer support via `CamundaMessageBridge`.
