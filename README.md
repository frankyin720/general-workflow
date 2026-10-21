# Mortgage Workflow

Repository directory: **`mortgage-workflow`**.

Camunda 8 demo for **mortgage origination** (eDocs, credit/AML, DMN risk, manual underwriting, eSign, funding).

## Run locally

1. Start **Camunda 8** (c8run): REST `8080`, gRPC `26500`
2. From this directory:

```bash
./mvnw spring-boot:run
```

3. Demo UI: http://localhost:8081/

## Key IDs

| Item | Value |
|------|--------|
| BPMN process id | `mortgage_workflow` |
| Start API | `POST /api/workflow/processes/mortgage_workflow/instances` |
| Spring app name | `mortgage-workflow` |
| Config prefix | `mortgage-workflow.demo.*`, `mortgage-workflow.integration.*` |
| Main class | `com.mortgage.workflow.MortgageWorkflowApplication` |

## Docs

- [`docs/demo-portal-walkthrough.md`](docs/demo-portal-walkthrough.md)
- [`docs/kafka-camunda-integration.md`](docs/kafka-camunda-integration.md)
- Postman: `src/main/resources/collection/mortgage-workflow-camunda.postman_collection.json`
