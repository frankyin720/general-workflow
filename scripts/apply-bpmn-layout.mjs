#!/usr/bin/env node
/**
 * Generates the BPMNDI section (shapes + edges) for src/main/resources/process/application-workflow.bpmn
 * from an explicit coordinate table, so the diagram opens cleanly in Camunda Modeler / Web Modeler.
 *
 * Layout conventions
 *  - Main happy path on a single horizontal lane (y = 640), phases left → right.
 *  - Parallel branches above / below the main lane; exception & human handling below.
 *  - Shared "decline" lane at y = 1120 and "cancel" lane at y = 1280.
 *
 * Run:  node scripts/apply-bpmn-layout.mjs
 * Never run bpmn-auto-layout on this file again — it rewrites sequence flows.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const file = resolve(here, '../src/main/resources/process/application-workflow.bpmn');

const TASK_W = 100, TASK_H = 80, GW = 50, EV = 36;
const shapes = new Map();

const task = (id, x, cy) => shapes.set(id, { x, y: cy - TASK_H / 2, w: TASK_W, h: TASK_H });
const gw = (id, cx, cy) => shapes.set(id, { x: cx - GW / 2, y: cy - GW / 2, w: GW, h: GW });
const ev = (id, cx, cy) => shapes.set(id, { x: cx - EV / 2, y: cy - EV / 2, w: EV, h: EV });
const sub = (id, x, y, w, h) => shapes.set(id, { x, y, w, h, expanded: true });
/** boundary event centred on a point of the host's edge */
const bnd = (id, cx, cy) => ev(id, cx, cy);

// ------------------------------------------------------------------ Phase 1
ev('StartEvent_applicationSubmitted', 180, 640);
task('Task_createMortgageFile', 260, 640);
bnd('Boundary_validationFailed', 310, 680);
task('Task_notifyAdvisorInvalidApplication', 260, 800);
ev('End_applicationInvalid', 440, 800);
gw('Gateway_preScreenSplit', 430, 640);
task('Task_creditBureauCheck', 520, 540);
bnd('Boundary_creditTimeout', 550, 500);
bnd('Boundary_creditHardDecline', 590, 500);
task('Task_recordCreditTimeout', 680, 400);
task('Task_recordCreditHardDecline', 680, 280);
gw('Gateway_creditMerge', 840, 540);
task('Task_amlKycScreening', 520, 740);
bnd('Boundary_amlTimeout', 570, 780);
task('Task_recordAmlTimeout', 680, 880);
gw('Gateway_amlMerge', 840, 740);
gw('Gateway_preScreenJoin', 940, 640);
task('Task_preScreeningEligibility', 1030, 640);
gw('Gateway_preScreeningRoute', 1210, 640);
task('Task_complianceReview', 1300, 800);
bnd('Boundary_complianceSla', 1350, 840);
task('Task_escalateComplianceReview', 1300, 960);
ev('End_complianceEscalated', 1450, 960);
gw('Gateway_complianceOutcome', 1500, 800);

// ------------------------------------------------------------------ Phase 2
gw('Gateway_phase2Split', 1580, 640);
sub('SubProcess_financialDocuments', 1660, 100, 1200, 460);
ev('Start_docs', 1700, 330);
ev('Event_waitEdoc', 1770, 330);
task('Task_validateDocumentViaEcm', 1840, 330);
bnd('Boundary_ecmUnavailable', 1870, 290);
bnd('Boundary_ecmTimeout', 1910, 290);
task('Task_manualDocumentReview', 1840, 180);
gw('Gateway_documentValid', 2010, 330);
task('Task_updateDocumentChecklist', 2090, 330);
gw('Gateway_allDocsReceived', 2260, 330);
ev('End_docs', 2340, 330);
task('Task_notifyAdvisorDocumentIssue', 1960, 470);
sub('EventSubProcess_docReminder', 2400, 400, 380, 140);
ev('Start_docReminder', 2440, 470);
task('Task_sendDocumentReminder', 2500, 470);
ev('End_docReminder', 2650, 470);
bnd('Boundary_docsExpired', 2500, 560);
task('Task_decideDocumentExpiry', 2450, 660);
gw('Gateway_docExpiryDecision', 2620, 660);

task('Task_requestPropertyAppraisal', 1660, 800);
gw('Gateway_awaitAppraisal', 1830, 800);
ev('Event_appraisalCompleted', 1920, 800);
task('Task_calculateLoanToValue', 1990, 800);
ev('Event_appraisalOverdue', 1830, 940);
task('Task_followUpAppraisal', 1900, 940);
gw('Gateway_appraisalFollowUp', 2070, 940);
gw('Gateway_phase2Join', 2990, 640);

// ------------------------------------------------------------------ Phase 3
task('Task_calculateDebtServiceRatios', 3080, 640);
task('Task_underwritingDecision', 3240, 640);
gw('Gateway_underwritingRoute', 3420, 640);
task('Task_recordAutoApproval', 3500, 500);
task('Task_manualUnderwritingReview', 3500, 640);
bnd('Boundary_underwritingSla', 3530, 680);
bnd('Boundary_underwritingOverdue', 3570, 680);
task('Task_escalateUnderwriting', 3480, 800);
ev('End_underwritingEscalated', 3620, 800);
task('Task_seniorUnderwriterReview', 3680, 800);
gw('Gateway_underwritingOutcome', 3860, 640);
task('Task_recordManualApproval', 3940, 640);
gw('Gateway_approvedMerge', 4120, 640);
task('Task_recordDecline', 3940, 1120);
task('Task_sendAdverseActionNotice', 4100, 1120);
ev('End_declined', 4270, 1120);

// ------------------------------------------------------------------ Phase 4
task('Task_generateCommitmentLetter', 4200, 640);
bnd('Boundary_letterGenerationFailed', 4250, 680);
task('Task_fixCommitmentLetter', 4200, 800);
task('Task_sendCommitmentForEsign', 4360, 640);
sub('SubProcess_awaitSignature', 4520, 480, 380, 320);
ev('Start_awaitSignature', 4560, 640);
gw('Gateway_awaitEsign', 4640, 640);
ev('Event_esignCompleted', 4740, 640);
ev('End_signed', 4830, 640);
ev('Event_clientDeclined', 4740, 740);
ev('End_clientDeclinedError', 4830, 740);
ev('Event_commitmentExpired', 4740, 540);
ev('End_commitmentExpiredError', 4830, 540);
bnd('Boundary_esignReminder', 4600, 480);
task('Task_sendEsignReminder', 4550, 380);
ev('End_esignReminderSent', 4710, 380);
bnd('Boundary_clientDeclined', 4600, 800);
task('Task_recordClientDeclined', 4550, 900);
ev('End_clientDeclined', 4710, 900);
bnd('Boundary_commitmentExpired', 4800, 800);
task('Task_decideCommitmentExpiry', 4750, 900);
gw('Gateway_commitmentExpiryDecision', 4920, 900);

// ------------------------------------------------------------------ Phase 5
task('Task_sendSolicitorInstructions', 4960, 640);
sub('SubProcess_awaitClosing', 5120, 500, 520, 360);
ev('Start_awaitClosing', 5160, 640);
gw('Gateway_awaitClosing', 5240, 640);
ev('Event_closingConfirmed', 5340, 640);
ev('End_closingConfirmed', 5430, 640);
ev('Event_closingOverdue', 5240, 760);
task('Task_resolveClosingDelay', 5300, 760);
gw('Gateway_closingDelayDecision', 5470, 760);
ev('End_closingCancelledError', 5560, 760);
bnd('Boundary_closingCancelled', 5380, 860);
task('Task_prepareFundingChecklist', 5700, 640);
task('Task_fundingReadiness', 5860, 640);
gw('Gateway_fundingReady', 6040, 640);
task('Task_clearFundingConditions', 5990, 800);
task('Task_disburseMortgage', 6140, 640);
bnd('Boundary_fundingFailed', 6190, 680);
task('Task_reverseFundingAttempt', 6140, 800);
task('Task_manualFundingIntervention', 6300, 800);
gw('Gateway_fundingIntervention', 6450, 800);
task('Task_notifyAdvisorFundingFailed', 6520, 800);
ev('End_fundingFailed', 6700, 800);
task('Task_activateMortgageAccount', 6300, 640);
task('Task_notifyFundedActive', 6460, 640);
ev('End_fundedActive', 6640, 640);

// ------------------------------------------------------------------ Shared
task('Task_cancelApplication', 5330, 1280);
ev('End_cancelled', 5500, 1280);
sub('EventSubProcess_withdrawal', 160, 1200, 320, 160);
ev('Start_withdrawal', 200, 1280);
task('Task_cancelWithdrawn', 260, 1280);
ev('End_withdrawn', 420, 1280);

// ------------------------------------------------------------------ Edges
const S = (id) => { const s = shapes.get(id); if (!s) throw new Error('no shape ' + id); return s; };
const cx = (id) => S(id).x + S(id).w / 2;
const cy = (id) => S(id).y + S(id).h / 2;
const L = (id, dy = 0) => [S(id).x, cy(id) + dy];
const R = (id, dy = 0) => [S(id).x + S(id).w, cy(id) + dy];
const T = (id, dx = 0) => [cx(id) + dx, S(id).y];
const B = (id, dx = 0) => [cx(id) + dx, S(id).y + S(id).h];
const P = (x, y) => [x, y];

const edges = new Map();
const E = (id, ...pts) => edges.set(id, pts);
/** straight horizontal connection right → left */
const H = (id, from, to) => E(id, R(from), L(to));

// Phase 1
H('Flow_start_createFile', 'StartEvent_applicationSubmitted', 'Task_createMortgageFile');
E('Flow_validationFailed_notify', B('Boundary_validationFailed'), T('Task_notifyAdvisorInvalidApplication'));
H('Flow_notifyInvalid_end', 'Task_notifyAdvisorInvalidApplication', 'End_applicationInvalid');
H('Flow_createFile_preScreenSplit', 'Task_createMortgageFile', 'Gateway_preScreenSplit');
E('Flow_preScreenSplit_credit', T('Gateway_preScreenSplit'), P(430, 540), L('Task_creditBureauCheck'));
E('Flow_preScreenSplit_aml', B('Gateway_preScreenSplit'), P(430, 740), L('Task_amlKycScreening'));
H('Flow_credit_merge', 'Task_creditBureauCheck', 'Gateway_creditMerge');
E('Flow_creditTimeout_record', T('Boundary_creditTimeout'), P(550, 400), L('Task_recordCreditTimeout'));
E('Flow_creditDecline_record', T('Boundary_creditHardDecline'), P(590, 280), L('Task_recordCreditHardDecline'));
E('Flow_creditTimeoutRecorded_merge', R('Task_recordCreditTimeout'), P(840, 400), T('Gateway_creditMerge'));
E('Flow_creditDeclineRecorded_merge', R('Task_recordCreditHardDecline'), P(840, 280), T('Gateway_creditMerge'));
E('Flow_creditMerge_join', R('Gateway_creditMerge'), P(940, 540), T('Gateway_preScreenJoin'));
H('Flow_aml_merge', 'Task_amlKycScreening', 'Gateway_amlMerge');
E('Flow_amlTimeout_record', B('Boundary_amlTimeout'), P(570, 880), L('Task_recordAmlTimeout'));
E('Flow_amlTimeoutRecorded_merge', R('Task_recordAmlTimeout'), P(840, 880), B('Gateway_amlMerge'));
E('Flow_amlMerge_join', R('Gateway_amlMerge'), P(940, 740), B('Gateway_preScreenJoin'));
H('Flow_preScreenJoin_eligibility', 'Gateway_preScreenJoin', 'Task_preScreeningEligibility');
H('Flow_eligibility_route', 'Task_preScreeningEligibility', 'Gateway_preScreeningRoute');
H('Flow_eligibility_proceed', 'Gateway_preScreeningRoute', 'Gateway_phase2Split');
E('Flow_eligibility_decline', B('Gateway_preScreeningRoute'), P(1210, 1120), L('Task_recordDecline'));
E('Flow_eligibility_review', B('Gateway_preScreeningRoute', 0), P(1210, 800), L('Task_complianceReview'));
H('Flow_compliance_outcome', 'Task_complianceReview', 'Gateway_complianceOutcome');
E('Flow_complianceSla_escalate', B('Boundary_complianceSla'), T('Task_escalateComplianceReview'));
H('Flow_complianceEscalated_end', 'Task_escalateComplianceReview', 'End_complianceEscalated');
E('Flow_compliance_cleared', R('Gateway_complianceOutcome'), P(1540, 800), P(1540, 640), L('Gateway_phase2Split'));
E('Flow_compliance_declined', B('Gateway_complianceOutcome'), P(1500, 1120), L('Task_recordDecline'));

// Phase 2 – documents
E('Flow_phase2Split_docs', T('Gateway_phase2Split'), P(1580, 330), L('SubProcess_financialDocuments', 330 - cy('SubProcess_financialDocuments')));
H('Flow_docs_start_wait', 'Start_docs', 'Event_waitEdoc');
H('Flow_wait_validate', 'Event_waitEdoc', 'Task_validateDocumentViaEcm');
H('Flow_validate_gateway', 'Task_validateDocumentViaEcm', 'Gateway_documentValid');
E('Flow_ecmError_manual', T('Boundary_ecmUnavailable'), P(1870, 220));
E('Flow_ecmTimeout_manual', T('Boundary_ecmTimeout'), P(1910, 220));
E('Flow_manualDoc_gateway', R('Task_manualDocumentReview'), P(2010, 180), T('Gateway_documentValid'));
H('Flow_docValid_checklist', 'Gateway_documentValid', 'Task_updateDocumentChecklist');
E('Flow_docInvalid_notify', B('Gateway_documentValid'), T('Task_notifyAdvisorDocumentIssue'));
E('Flow_docIssue_wait', L('Task_notifyAdvisorDocumentIssue'), P(1778, 470), B('Event_waitEdoc', 8));
H('Flow_checklist_allDocs', 'Task_updateDocumentChecklist', 'Gateway_allDocsReceived');
H('Flow_allDocs_end', 'Gateway_allDocsReceived', 'End_docs');
E('Flow_moreDocs_wait', B('Gateway_allDocsReceived'), P(2260, 540), P(1762, 540), B('Event_waitEdoc', -8));
H('Flow_docReminder_send', 'Start_docReminder', 'Task_sendDocumentReminder');
H('Flow_docReminder_end', 'Task_sendDocumentReminder', 'End_docReminder');
E('Flow_docs_join', R('SubProcess_financialDocuments'), P(2990, 330), T('Gateway_phase2Join'));
E('Flow_docsExpired_decide', B('Boundary_docsExpired'), T('Task_decideDocumentExpiry'));
H('Flow_docExpiry_gateway', 'Task_decideDocumentExpiry', 'Gateway_docExpiryDecision');
E('Flow_docExpiry_extend', T('Gateway_docExpiryDecision'), P(2620, 560));
E('Flow_docExpiry_cancel', B('Gateway_docExpiryDecision'), P(2620, 1280), L('Task_cancelApplication'));

// Phase 2 – appraisal
E('Flow_phase2Split_appraisal', B('Gateway_phase2Split'), P(1580, 800), L('Task_requestPropertyAppraisal'));
H('Flow_appraisalRequested_wait', 'Task_requestPropertyAppraisal', 'Gateway_awaitAppraisal');
H('Flow_await_appraisalCompleted', 'Gateway_awaitAppraisal', 'Event_appraisalCompleted');
E('Flow_await_appraisalOverdue', B('Gateway_awaitAppraisal'), T('Event_appraisalOverdue'));
H('Flow_appraisalOverdue_followUp', 'Event_appraisalOverdue', 'Task_followUpAppraisal');
H('Flow_followUp_gateway', 'Task_followUpAppraisal', 'Gateway_appraisalFollowUp');
E('Flow_appraisalFollowUp_wait', R('Gateway_appraisalFollowUp'), P(2130, 940), P(2130, 740), P(1830, 740), T('Gateway_awaitAppraisal'));
E('Flow_appraisalFollowUp_cancel', B('Gateway_appraisalFollowUp'), P(2070, 1280), L('Task_cancelApplication'));
H('Flow_appraisalCompleted_ltv', 'Event_appraisalCompleted', 'Task_calculateLoanToValue');
E('Flow_ltv_join', R('Task_calculateLoanToValue'), P(2990, 800), B('Gateway_phase2Join'));

// Phase 3
H('Flow_phase2Join_ratios', 'Gateway_phase2Join', 'Task_calculateDebtServiceRatios');
H('Flow_ratios_dmn', 'Task_calculateDebtServiceRatios', 'Task_underwritingDecision');
H('Flow_dmn_route', 'Task_underwritingDecision', 'Gateway_underwritingRoute');
E('Flow_route_auto', T('Gateway_underwritingRoute'), P(3420, 500), L('Task_recordAutoApproval'));
H('Flow_route_manual', 'Gateway_underwritingRoute', 'Task_manualUnderwritingReview');
E('Flow_route_decline', B('Gateway_underwritingRoute'), P(3420, 1120), L('Task_recordDecline'));
E('Flow_autoApproved_merge', R('Task_recordAutoApproval'), P(4120, 500), T('Gateway_approvedMerge'));
H('Flow_manualUw_outcome', 'Task_manualUnderwritingReview', 'Gateway_underwritingOutcome');
E('Flow_uwSla_escalate', B('Boundary_underwritingSla'), T('Task_escalateUnderwriting'));
H('Flow_uwEscalated_end', 'Task_escalateUnderwriting', 'End_underwritingEscalated');
E('Flow_uwOverdue_senior', B('Boundary_underwritingOverdue'), P(3570, 730), P(3730, 730), T('Task_seniorUnderwriterReview'));
E('Flow_seniorUw_outcome', R('Task_seniorUnderwriterReview'), P(3810, 800), P(3810, 640), L('Gateway_underwritingOutcome'));
H('Flow_uw_approved', 'Gateway_underwritingOutcome', 'Task_recordManualApproval');
E('Flow_uw_declined', B('Gateway_underwritingOutcome'), P(3860, 1120), L('Task_recordDecline'));
H('Flow_manualApproved_merge', 'Task_recordManualApproval', 'Gateway_approvedMerge');
H('Flow_decline_notice', 'Task_recordDecline', 'Task_sendAdverseActionNotice');
H('Flow_notice_end', 'Task_sendAdverseActionNotice', 'End_declined');

// Phase 4
H('Flow_approved_commitment', 'Gateway_approvedMerge', 'Task_generateCommitmentLetter');
E('Flow_letterFailed_fix', B('Boundary_letterGenerationFailed'), T('Task_fixCommitmentLetter'));
E('Flow_fixLetter_retry', L('Task_fixCommitmentLetter'), P(4175, 800), P(4175, 655), L('Task_generateCommitmentLetter', 15));
H('Flow_commitment_send', 'Task_generateCommitmentLetter', 'Task_sendCommitmentForEsign');
H('Flow_send_awaitSignature', 'Task_sendCommitmentForEsign', 'SubProcess_awaitSignature');
H('Flow_sig_start_gateway', 'Start_awaitSignature', 'Gateway_awaitEsign');
H('Flow_sig_signed', 'Gateway_awaitEsign', 'Event_esignCompleted');
E('Flow_sig_declined', B('Gateway_awaitEsign'), P(4640, 740), L('Event_clientDeclined'));
E('Flow_sig_expired', T('Gateway_awaitEsign'), P(4640, 540), L('Event_commitmentExpired'));
H('Flow_signed_end', 'Event_esignCompleted', 'End_signed');
H('Flow_declined_errorEnd', 'Event_clientDeclined', 'End_clientDeclinedError');
H('Flow_expired_errorEnd', 'Event_commitmentExpired', 'End_commitmentExpiredError');
E('Flow_esignReminder_send', T('Boundary_esignReminder'), B('Task_sendEsignReminder'));
H('Flow_esignReminder_end', 'Task_sendEsignReminder', 'End_esignReminderSent');
E('Flow_clientDeclined_record', B('Boundary_clientDeclined'), T('Task_recordClientDeclined'));
H('Flow_clientDeclined_end', 'Task_recordClientDeclined', 'End_clientDeclined');
E('Flow_commitmentExpired_decide', B('Boundary_commitmentExpired'), T('Task_decideCommitmentExpiry'));
H('Flow_commitmentExpiry_gateway', 'Task_decideCommitmentExpiry', 'Gateway_commitmentExpiryDecision');
E('Flow_commitmentExpiry_extend', B('Gateway_commitmentExpiryDecision'), P(4920, 1000), P(4185, 1000), P(4185, 670), L('Task_generateCommitmentLetter', 30));
E('Flow_commitmentExpiry_withdraw', R('Gateway_commitmentExpiryDecision'), P(5000, 900), P(5000, 1280), L('Task_cancelApplication'));
H('Flow_signed_solicitor', 'SubProcess_awaitSignature', 'Task_sendSolicitorInstructions');

// Phase 5
H('Flow_solicitor_awaitClosing', 'Task_sendSolicitorInstructions', 'SubProcess_awaitClosing');
H('Flow_closing_start_gateway', 'Start_awaitClosing', 'Gateway_awaitClosing');
H('Flow_closing_confirmed', 'Gateway_awaitClosing', 'Event_closingConfirmed');
E('Flow_closing_overdue', B('Gateway_awaitClosing'), T('Event_closingOverdue'));
H('Flow_closingConfirmed_end', 'Event_closingConfirmed', 'End_closingConfirmed');
H('Flow_closingOverdue_resolve', 'Event_closingOverdue', 'Task_resolveClosingDelay');
H('Flow_resolveClosing_gateway', 'Task_resolveClosingDelay', 'Gateway_closingDelayDecision');
E('Flow_closingDelay_wait', T('Gateway_closingDelayDecision'), P(5470, 560), P(5240, 560), T('Gateway_awaitClosing'));
H('Flow_closingDelay_cancel', 'Gateway_closingDelayDecision', 'End_closingCancelledError');
E('Flow_closingCancelled_cancel', B('Boundary_closingCancelled'), T('Task_cancelApplication'));
H('Flow_closingConfirmed_checklist', 'SubProcess_awaitClosing', 'Task_prepareFundingChecklist');
H('Flow_checklist_readiness', 'Task_prepareFundingChecklist', 'Task_fundingReadiness');
H('Flow_readiness_gateway', 'Task_fundingReadiness', 'Gateway_fundingReady');
E('Flow_readiness_hold', B('Gateway_fundingReady'), P(6040, 800), L('Task_clearFundingConditions'));
E('Flow_conditionsCleared_checklist', L('Task_clearFundingConditions'), P(5750, 800), B('Task_prepareFundingChecklist'));
H('Flow_readiness_fund', 'Gateway_fundingReady', 'Task_disburseMortgage');
E('Flow_fundingFailed_reverse', B('Boundary_fundingFailed'), T('Task_reverseFundingAttempt'));
H('Flow_reversed_intervention', 'Task_reverseFundingAttempt', 'Task_manualFundingIntervention');
H('Flow_intervention_gateway', 'Task_manualFundingIntervention', 'Gateway_fundingIntervention');
E('Flow_fundingIntervention_retry', B('Gateway_fundingIntervention'), P(6450, 900), P(6110, 900), P(6110, 655), L('Task_disburseMortgage', 15));
H('Flow_fundingIntervention_fail', 'Gateway_fundingIntervention', 'Task_notifyAdvisorFundingFailed');
H('Flow_fundingFailed_end', 'Task_notifyAdvisorFundingFailed', 'End_fundingFailed');
H('Flow_disbursed_activate', 'Task_disburseMortgage', 'Task_activateMortgageAccount');
H('Flow_activated_notify', 'Task_activateMortgageAccount', 'Task_notifyFundedActive');
H('Flow_notify_end', 'Task_notifyFundedActive', 'End_fundedActive');

// Shared
H('Flow_cancel_end', 'Task_cancelApplication', 'End_cancelled');
H('Flow_withdrawal_cancel', 'Start_withdrawal', 'Task_cancelWithdrawn');
H('Flow_withdrawal_end', 'Task_cancelWithdrawn', 'End_withdrawn');

// ------------------------------------------------------------------ Labels
// Boundary-event labels are moved beside the event so they never overlap the host task name.
const labels = new Map();
const label = (id, x, y, w = 70, h = 27) => labels.set(id, { x, y, w, h });
label('Boundary_validationFailed', 232, 690);
label('Boundary_creditTimeout', 462, 462);
label('Boundary_creditHardDecline', 612, 462, 60);
label('Boundary_amlTimeout', 592, 790);
label('Boundary_complianceSla', 1372, 850, 50);
label('Boundary_ecmUnavailable', 1792, 250);
label('Boundary_ecmTimeout', 1926, 250);
label('Boundary_docsExpired', 2522, 570, 80);
label('Boundary_underwritingSla', 3452, 700, 50);
label('Boundary_underwritingOverdue', 3592, 745, 60);
label('Boundary_letterGenerationFailed', 4272, 690);
label('Boundary_esignReminder', 4622, 445);
label('Boundary_clientDeclined', 4622, 810);
label('Boundary_commitmentExpired', 4822, 810);
label('Boundary_closingCancelled', 5402, 870);
label('Boundary_fundingFailed', 6212, 690);

// ------------------------------------------------------------------ Emit
const xml = readFileSync(file, 'utf8');
const semantic = xml.replace(/\s*<bpmndi:BPMNDiagram[\s\S]*?<\/bpmndi:BPMNDiagram>/, '');

// Every flow node / sequence flow in the model must have DI, and vice versa.
const nodeIds = [...semantic.matchAll(/<bpmn:(?!sequenceFlow|process|definitions|message|error|incoming|outgoing|documentation|extensionElements|conditionExpression|timeDuration|timeCycle|[a-zA-Z]*EventDefinition)([a-zA-Z]+) id="([^"]+)"/g)]
	.filter((m) => !['signal', 'escalation', 'collaboration'].includes(m[1]))
	.map((m) => m[2]);
const flowIds = [...semantic.matchAll(/<bpmn:sequenceFlow id="([^"]+)"/g)].map((m) => m[1]);
const missingShapes = nodeIds.filter((id) => !shapes.has(id));
const missingEdges = flowIds.filter((id) => !edges.has(id));
const extraShapes = [...shapes.keys()].filter((id) => !nodeIds.includes(id));
const extraEdges = [...edges.keys()].filter((id) => !flowIds.includes(id));
if (missingShapes.length || missingEdges.length || extraShapes.length || extraEdges.length) {
	console.error({ missingShapes, missingEdges, extraShapes, extraEdges });
	process.exit(1);
}

const nodeOrder = nodeIds; // keep document order for stable diffs
let di = '  <bpmndi:BPMNDiagram id="BPMNDiagram_mortgage_workflow">\n';
di += '    <bpmndi:BPMNPlane id="BPMNPlane_mortgage_workflow" bpmnElement="mortgage_workflow">\n';
for (const id of nodeOrder) {
	const s = shapes.get(id);
	const expanded = s.expanded ? ' isExpanded="true"' : '';
	di += `      <bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}"${expanded}>\n`;
	di += `        <dc:Bounds x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" />\n`;
	const lb = labels.get(id);
	if (lb) {
		di += `        <bpmndi:BPMNLabel>\n          <dc:Bounds x="${lb.x}" y="${lb.y}" width="${lb.w}" height="${lb.h}" />\n        </bpmndi:BPMNLabel>\n`;
	}
	di += '      </bpmndi:BPMNShape>\n';
}
for (const id of flowIds) {
	di += `      <bpmndi:BPMNEdge id="${id}_di" bpmnElement="${id}">\n`;
	for (const [x, y] of edges.get(id)) {
		di += `        <di:waypoint x="${x}" y="${y}" />\n`;
	}
	di += '      </bpmndi:BPMNEdge>\n';
}
di += '    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n';

const out = semantic.replace(/\n?<\/bpmn:definitions>\s*$/, '\n' + di + '</bpmn:definitions>\n');
writeFileSync(file, out);
console.log(`BPMNDI written: ${shapes.size} shapes, ${edges.size} edges`);
