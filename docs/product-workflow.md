# AVAS product workflow

This sequence consolidates the complete 95-page product and technical specification into one implementation path. Each transition must be persisted, owned, tenant-scoped, auditable and reproducible from versioned inputs.

## End-to-end journey

1. **Identity and active role** — Register or sign in by username, full name, mobile or optional email; select only an assigned active role. Persist the user, tenant, roles, permissions and refresh session in MySQL.
2. **Project draft** — Create an owned project, select plot/drawing/early-planning mode, then autosave and resume plot, location, site, family, budget and preference facts.
3. **Requirement interpretation** — Separate explicit facts from inferred facts; record source, confidence, reason, contradictions, assumptions and targeted follow-up questions.
4. **Alternative recommendations** — Produce cost-efficient, balanced and future-ready options with scores, ranges, provenance and handling level (`AUTOMATIC`, `ASSISTED`, `EXPERT_REVIEW` or `UNSUPPORTED`).
5. **Approved context snapshot** — Accept or override individual facts and create an immutable, versioned `ProjectContextSnapshot`. All downstream engines consume its identifier, never mutable screen state.
6. **Drawing generation job** — Queue a reproducible job with snapshot ID, engine/rule/knowledge versions, configuration hash and seed. Track stages, retryable failures, cancellation, progress and dead-letter state.
7. **Candidate geometry and validation** — Generate three materially different multi-floor candidates. Validate boundaries, setbacks, room dimensions, adjacency, circulation, stairs, wet-area alignment, accessibility, climate and Vastu trade-offs. Preserve structured geometry as authority and render SVG/PDF artifacts.
8. **Customer feedback and concept approval** — Interpret structured feedback into a new requirement/context version, regenerate candidates, compare versions and record irreversible concept approval.
9. **Licensed professional review** — Request and assign architect, structural, quantity-surveyor and optional Vastu reviews. Store findings, corrections and signed approve/reject decisions; block construction output until required gates pass.
10. **BOQ, estimate and market evidence** — Derive quantities from approved drawing geometry. Link every rate to immutable location/specification/freshness evidence; include tax, transport, wastage, fees, escalation, contingency and uncertainty ranges.
11. **Builder marketplace** — Verify builder profiles and service areas, publish eligible projects, receive versioned itemized quotations and timelines, compare offers, select a builder and record award acceptance/contract readiness.
12. **Site execution** — Assign site engineers; persist milestones, attendance, daily evidence, receipts/consumption, inspections, safety/quality observations, issues, delays and change requests.
13. **Invoices and payments** — Connect contract milestones to invoices, provider payment sessions, webhook/signature verification, customer approvals, reconciliation, refunds and immutable payment audit.
14. **Completion and outcome** — Record completion evidence, actual quantities, cost, duration, defects and professional/customer outcomes against the original approved snapshots.
15. **Controlled learning** — With explicit consent, de-identify eligible feedback/outcomes into dataset candidates. Require quality review, offline evaluation, champion/challenger release, monitored deployment and explicit rollback; raw feedback never updates production automatically.

## Data authority

| Store | Authoritative responsibility |
|---|---|
| MySQL `avas-new` | Users, roles, memberships, projects, immutable context references, workflow transitions, approvals, jobs, estimates, quotations, assignments, milestones, invoices, payments, idempotency and outbox events |
| MongoDB `avas-new` | Versioned flexible geometry, analyses, explanations, feedback, daily reports, research/knowledge documents and consented de-identified intelligence snapshots |
| Object storage | Uploaded drawings, photographs and immutable rendered/exported artifacts, addressed by checksum and metadata stored in MySQL |
| Redis/RabbitMQ | Non-authoritative locks, deduplication, progress and asynchronous delivery; durable intent begins in the MySQL outbox |

## Delivery order and current status

| Phase | Deliverable | Status |
|---|---|---|
| P0.1 | One local `avas-new` database target, safe DB status, honest UI errors, atomic project persistence | Complete |
| P0.2 | Project owner/tenant enforcement, owner list, database-backed reopen and restart proof | Complete |
| P0.3 | Versioned schema baseline and MySQL outbox/idempotency tables | Implemented; delivery workers and replay remain next |
| P1.1 | Versioned facts, alternative recommendations and immutable project-context snapshots | Planned |
| P1.2 | Governed building-rule/knowledge admission and location releases | Planned |
| P1.3 | Asynchronous generation jobs, real geometry pipeline, upload/analysis and object storage | Planned |
| P1.4 | Signed professional review and drawing-derived BOQ | Planned |
| P2.1 | Extended local pricing evidence and builder quotation/award workflow | Planned |
| P2.2 | Site execution, documents/messages, milestone invoices and payments | Planned |
| P2.3 | Outcome capture, consented learning, evaluation and model-release governance | Planned |
| P3 | Admin operations, MFA, rate limits, observability, backups, accessibility and release gates | Planned |

## Release rule

A phase is complete only when its API, database records, role/resource authorization, web page, restart/reload behavior, audit history and automated tests all pass. A static screen or in-memory result is not a completed workflow.

Current PDF delivery is an authenticated, on-demand projection of persisted geometry rather than a durable generation job. The concept PDF remains a vector floor-sheet set. The downloadable design report adds a personalised household/area recommendation, selected-versus-represented lift and balcony decisions, a room-by-room schedule, option comparison and governed costing pages. A queue/worker should be introduced only when durable object-storage rendering is implemented; cron is not part of this request path.
