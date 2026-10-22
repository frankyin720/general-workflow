# Application Workflow

Repository directory: **`general-workflow`**.

Camunda 8 demo for **General Workflow processing** (eDocs, credit/AML, DMN risk, manual underwriting, eSign, funding).

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
| BPMN process id | `general_workflow` |
| Start API | `POST /api/workflow/processes/general_workflow/instances` |
| Spring app name | `general-workflow` |
| Config prefix | `general-workflow.demo.*`, `general-workflow.integration.*` |
| Main class | `com.application.workflow.ApplicationWorkflowApplication` |

## Docs

- [`docs/demo-portal-walkthrough.md`](docs/demo-portal-walkthrough.md)
- [`docs/kafka-camunda-integration.md`](docs/kafka-camunda-integration.md)
- Postman: `src/main/resources/collection/application-workflow-camunda.postman_collection.json`
