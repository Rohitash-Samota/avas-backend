# Consolidated AVAS architecture

AVAS has exactly two deployable application boundaries, matching the requested merge and the specification’s modular-monolith baseline.

```text
Browser
  └─ Next.js web :3000
       ├─ planning and drawing experience
       ├─ authentication and role session
       └─ cart, checkout, orders and wallet
            │ HTTPS/JSON + Bearer JWT
            ▼
     Spring Boot backend :8080
       ├─ auth/users/roles
       ├─ projects/requirements/recommendations
       ├─ native geometry/drawings/validation
       ├─ estimates/BOQ/audit
       ├─ pricing/configuration/model governance/budget intelligence
       ├─ catalog/orders/payments/refunds/wallet
       └─ knowledge/rules/evidence
            ├─ JPA ───────> MySQL database `avas-new`
            └─ Mongo data -> MongoDB database `avas-new`
```

## Backend modules and ownership

- `auth` owns users, unique usernames, optional email, normalized mobile numbers, roles, permissions, password hashes, JWT issue/validation, rotating hashed refresh tokens and optional Google OAuth account creation. Its unified identifier resolver supports username, unambiguous full name, mobile and email while privileged roles remain admin-provisioned.
- `security` owns request authentication and validates that `X-Active-Role` is among the user’s assigned roles.
- `project` owns the planning state machine and native deterministic geometry engine. `SpecificationTier` holds what a finish tier means as a room programme rather than only as a rate, and is read by both the parameter targets a customer is shown and the planner that places the walls. `ApproachParking` resolves where the cars stand once, so the layout and the site plan cannot disagree about it. `LayoutSheetRenderer` draws the customer-facing layout sheet that leads the PDF set. `LayoutClient` asks AVAS AI where the rooms go; `FloorPlanner` places them when it does not answer.
- `project.persistence` owns durable project, requirement snapshot, drawing artifact, estimate artifact and append-only project audit entities.
- `commerce` owns catalog products, server-side price calculation, orders/items, payment sessions/audit, refunds, wallets and wallet transactions.
- `knowledge` owns versioned planning rules, jurisdictional sources and evidence metadata in MongoDB.
- `pricing` owns immutable price contributions and decisions, versioned recommendation configuration, controlled model releases, deterministic budget ranges, feedback consent and governance audit. MySQL is authoritative; MongoDB stores de-identified intelligence snapshots.

The geometry engine remains a strong module boundary inside the backend. It can be extracted later without changing its structured geometry contract, while today’s single process provides the requested one-backend deployment and atomic workflow integration.

## Where a home is decided

Planning is three questions, deliberately asked separately, so no single answer can quietly change the others.

| Question | Answered by | Falls back to |
|---|---|---|
| What is this household owed? | `/api/v1/plan-programme` | `HouseholdProgramme.deterministic` |
| What targets and options? | `/api/v1/plan-parameters` | `PlanningParameterSet` rules |
| Where do the rooms go? | `/api/v1/plan-layout` | `FloorPlanner` |

The programme travels with the layout request, so the remote planner arranges the home the platform already decided on rather than planning one of its own — a service that answered both questions could return a house the customer's estimate was never costed against.

### Corridor and hub

The two layout planners arrange the same programme differently, and the difference is the point.

`FloorPlanner` plans a **double-loaded corridor**: two strips of rooms either side of a 3.75 ft circulation spine. Every room gets a door without walking through another room, and the family spends forty running feet of floor area on somewhere to walk.

AVAS AI plans a **hub**: the entrance opens into the living room, the living runs into the dining, and every other room takes its door off that run — the family lounge doing the same job upstairs. Circulation is still there and is now habitable. It is how the homes this market actually builds are drawn.

The hub guarantee is checked rather than assumed. Every placed room names the space its door opens onto, and a layout is refused — by the AI service and again by `AvasAiLayoutClient` — if that name is not a room sharing at least 2.5 ft of wall with it, if rooms overlap, if any room leaves the buildable envelope, if a room is drawn below the size its type is usable at, or if the reply contains a corridor at all.

A refused layout costs the customer nothing: `FloorPlanner` draws that option instead, and the drawing set is always complete. `avas.ai.layout-enabled` is **false** by default, so a deployment that has not opted in plans every storey locally, with a corridor.

## Layered backend structure

Each domain follows the requested Spring layering inside the one deployable backend:

| Layer | Authentication | Projects | Pricing intelligence | Commerce and payment |
|---|---|---|---|---|
| Controller | `AuthController`, `UserAdminController` | `ProjectController` | `PricingController`, `PricingAdminController` | `CommerceController` |
| Service | `AuthService` | `ProjectService`, `ProjectPersistenceService` | `PricingService`, `MongoPricingIntelligenceStore` | `CommerceService`, `RazorpayGateway` |
| Repository interfaces | User, role and refresh-token repositories | Project/artifact JPA repositories | Price, configuration, model, recommendation, audit and Mongo snapshot repositories | Product, order, payment, refund and wallet repositories |
| Entities | User, role and refresh-token entities | Project, state, drawing, estimate and audit entities | Price submission, platform configuration, model release, budget recommendation and governance audit | Product, order, payment, refund and wallet entities |

Controllers do not access the database directly. Services own workflow and transaction boundaries, while Spring Data JPA repository interfaces own persistence.

MongoDB follows the same separation through `KnowledgeController` / `KnowledgeAdminController`, `KnowledgeService`, `KnowledgeSourceRepository` and `KnowledgeSourceDocument`.

## Persistent entities

Authentication: `users`, `roles`, `role_permissions`, `user_roles`, `refresh_tokens`.

Planning: `projects`, `project_state_snapshots`, `requirement_snapshots`, `drawing_artifacts`, `estimate_artifacts`, `project_audit_logs`. Projects carry `tenantId` and `ownerUserId`; owner-scoped queries and resource checks prevent cross-account access. The full state snapshot rehydrates an active aggregate after restart, while the typed projections and snapshot commit in one MySQL transaction.

Commerce: `catalog_products`, `commerce_orders`, `commerce_order_items`, `payments`, `payment_refunds`, `payment_audit_logs`, `wallets`, `wallet_transactions`.

Pricing governance: `price_submissions`, `platform_configuration`, `model_releases`, `budget_recommendations`, range assumption/explanation tables and `governance_audit`.

MongoDB: `knowledge_sources`, keyed by stable `sourceKey`, stores versioned and jurisdiction-scoped rule/evidence metadata. `pricing_intelligence_snapshots` stores de-identified recommendation and explicitly consented feedback snapshots. The specification migration also creates validated, indexed collections for project-context and requirement snapshots; AI requests, responses and conversations; geometry and floor-plan documents; drawing analyses and validation reports; estimate snapshots; site and inspection reports; recommendation explanations; feedback, diagnostics and learning records. `domain_schema_migrations` records the applied document-schema version.

MySQL applies two independent ledgers. `identity_schema_migrations` creates the role catalogue, first administrator and remaining local role identities in a deterministic order. `database_schema_migrations` records the specification-derived transactional baseline, including role profiles, structured requirements, rule/version tables, drawing metadata and approvals, estimates and BOQ, quotations, assignments, milestones, invoices, professional reviews, jobs, audit, idempotency and outbox tables.

Every commerce query is scoped to the authenticated user. Orders also carry `tenantId` and an optional `projectId`. Monetary values are stored as integer rupees and multiplied into provider paise only at the Razorpay boundary.

## Role model and interface themes

| Role | Primary workspace | Provisioning |
|---|---|---|
| `INDIVIDUAL` | Home planning, budget recommendations, projects, checkout and wallet | Public registration or admin |
| `BUILDER` | Eligible projects, quotations and governed price contributions | Public registration or admin |
| `INTERNAL_USER` | Architect review and estimate validation | Admin only |
| `SITE_ENGINEER` | Site logs, milestones and issues | Admin only |
| `ADMIN` | Governance, users, roles and releases | Admin only |

The browser sends `X-Active-Role`; the backend checks the live MySQL role assignment and active permission set on every protected request. Theme selection is presentation state only and is stored locally as `avas_theme`; it never changes permissions or project data.

## Payment state and safety

```text
PENDING_PAYMENT / CREATED
        │ verified HMAC or explicit TEST simulator
        ▼
      PAID / PAID ── full refund ──> REFUNDED / REFUNDED
```

The browser never marks an order paid. A wallet top-up credits balance only in the same backend transaction that validates payment. Refunding a consumed top-up is rejected for support review instead of allowing a negative wallet.

## Local and production persistence

Local and Docker execution use MySQL 8 and MongoDB 7 with the database name `avas-new`. Automated tests override MySQL with isolated in-memory H2 databases and disable the external Mongo connection. Production must provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MONGODB_URI`, a strong random `JWT_SECRET`, HTTPS cookies, real Razorpay credentials, Google credentials if enabled, managed migrations, backups and secret management.
