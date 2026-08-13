# AVAS API

The unified backend serves versioned endpoints at `http://localhost:8080/api/v1`. `GET /api/v1` and `GET /api/v1/status` are public discovery responses describing authentication, database ownership and resource groups. Protected requests use `Authorization: Bearer <access-token>`. `X-Active-Role` may select only an active role assigned to the authenticated user; backend authorities are reduced to that role's configured permissions. Missing authentication returns a JSON `401`, while insufficient active-role authority returns a JSON `403`.

## Authentication and users

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Create a home-owner or builder account; email is optional with mobile |
| POST | `/auth/login` | Public | Username, unique full name, mobile or email plus password |
| POST | `/auth/login/mobile` | Public | Backward-compatible mobile/password login |
| POST | `/auth/refresh` | Refresh cookie | Rotate refresh token and issue access token |
| POST | `/auth/logout` | Signed in | Revoke refresh session and clear cookie |
| GET | `/auth/me` | Signed in | Current user, tenant and assigned roles |
| GET | `/auth/providers` | Public | Local/Google provider availability |
| GET | `/oauth2/authorization/google` | Public | Start Google OAuth when configured |
| GET | `/admin/users` | Admin | List users |
| POST | `/admin/users` | Admin | Create a tenant-scoped user with any platform role |
| PUT | `/admin/users/{id}/roles` | Admin | Assign a controlled role |
| DELETE | `/admin/users/{id}/roles/{role}` | Admin | Remove a role while retaining account and Builder invariants |
| GET | `/admin/roles` | Admin | List all roles, active state and permissions |
| GET | `/admin/roles/permissions` | Admin | List the controlled permission catalog |
| PUT | `/admin/roles/{role}` | Admin | Configure role activation and permissions |

Access tokens are short-lived JWTs. Refresh tokens are random, hashed in storage, HttpOnly, rotated at use and revocable.

Public registration accepts `accountType` as `INDIVIDUAL` or `BUILDER` only. Builder registration assigns both `BUILDER` and `INDIVIDUAL`, allowing one account to switch between professional and customer journeys. `INTERNAL_USER`, `SITE_ENGINEER` and `ADMIN` identities require the admin endpoint. Each account has a unique username. At least one of email or mobile is required, while email itself is optional. Mobile numbers are stored in canonical digits-only form. The unified login endpoint accepts `identifier` plus `password`; legacy `email` and `mobileNumber` properties remain compatible. No sample identities or shared credentials are exposed. A new deployment may create its first administrator once through the validated `AVAS_BOOTSTRAP_ADMIN_*` environment settings.

## MongoDB knowledge and evidence

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/knowledge/status` | `KNOWLEDGE_MANAGE` | Mongo database, collection, document count and active version |
| GET | `/knowledge/sources` | `KNOWLEDGE_MANAGE` | List versioned knowledge/evidence documents |
| POST | `/admin/knowledge/sources` | `KNOWLEDGE_MANAGE` | Create or update a source by stable `sourceKey` |

MongoDB stores flexible, versioned rule, evidence and de-identified intelligence snapshots. MySQL remains authoritative for users, approvals, configuration, model releases, recommendations, projects, orders, payments and wallets.

## Pricing, budget and controlled learning

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| POST | `/pricing/submissions` | `PRICE_SUBMIT` or `PRICE_MANAGE` | Add immutable provider/user pricing evidence in `PENDING` state |
| GET | `/pricing/submissions/mine` | Signed-in pricing/budget user | List the current user’s contribution history |
| POST | `/pricing/budget-recommendations` | `BUDGET_RECOMMEND` | Create and persist an evidence-backed budget range |
| POST | `/pricing/budget-recommendations/{id}/feedback` | Owner | Store feedback; Mongo learning use requires explicit consent |
| GET | `/admin/pricing/submissions` | `PRICE_MANAGE` | Review all evidence and its decision history |
| PUT | `/admin/pricing/submissions/{id}/decision` | `PRICE_MANAGE` | Approve or reject once; a second decision is rejected |
| GET | `/admin/configuration` | `PLATFORM_CONFIG_MANAGE` | Read versioned rates and recommendation controls |
| PUT | `/admin/configuration` | `PLATFORM_CONFIG_MANAGE` | Publish a new global configuration version |
| GET | `/admin/models` | `MODEL_MANAGE` | List governed model releases |
| POST | `/admin/models` | `MODEL_MANAGE` | Register a draft model release |
| PUT | `/admin/models/{id}/validate` | `MODEL_MANAGE` | Record reviewed offline validation evidence |
| PUT | `/admin/models/{id}/activate` | `MODEL_MANAGE` | Activate a validated model and retire the previous release |

Price evidence separates the item category (`MATERIAL`, `LABOUR`, `SERVICE`, `WORK` or `COST_PER_SQFT`) from the finish tier (`ECONOMY`, `STANDARD`, `PREMIUM`, `LUXURY`). Only approved, unexpired, fresh, city-matched `COST_PER_SQFT` submissions for the requested finish tier can replace administrator base rates in a budget calculation. Other approved rates remain governed evidence for BOQ and future estimate workflows.

Example budget request:

```json
{
  "city": "Jaipur",
  "builtUpAreaSqFt": 1800,
  "category": "STANDARD",
  "totalBudget": 4200000
}
```

The response includes `lowBudget`, `recommendedBudget`, `highBudget`, `budgetFit`, `suggestedCategory`, `confidence`, `confidenceLevel`, `sampleCount`, `priceSource`, `explanations`, `modelVersion` and `configurationVersion`. It is a planning range, never a binding quotation.

Learning is deliberately controlled. MySQL records the recommendation and feedback for ownership/audit. MongoDB receives an area-banded, de-identified feedback snapshot only when both `consentToLearning: true` and platform `learningEnabled: true` are present. There is no API path from raw feedback to production model activation.

## Planning and artifacts

- `POST /projects`
- `GET /projects` (owner-scoped list; tenant-wide only for the active administrator role)
- `GET /projects/{projectId}`
- `PUT /projects/{projectId}/basic-details`
- `POST /projects/{projectId}/recommendations/generate`
- `GET /projects/{projectId}/recommendations`
- `POST /projects/{projectId}/recommendations/{recommendationId}/accept`
- `PUT /projects/{projectId}/preferences`
- `GET /projects/{projectId}/requirement-summary`
- `POST /projects/{projectId}/drawings/generate`
- `GET /projects/{projectId}/drawing-jobs/{jobId}`
- `GET /projects/{projectId}/drawings`
- `GET /drawings/{drawingId}`
- `POST /drawings/{drawingId}/feedback`
- `POST /drawings/{drawingId}/regenerate`
- `POST /drawings/{drawingId}/approve-concept`
- `GET /drawings/{drawingId}/validation`
- `GET /drawings/{drawingId}/pdf` (server-rendered PDF, displayed inline)
- `GET /drawings/{drawingId}/download` and `/download.pdf` (compatibility aliases for the same inline PDF)
- `POST /projects/{projectId}/estimates/generate`
- `GET /projects/{projectId}/estimates`
- `GET /estimates/{estimateId}`
- `GET /estimates/{estimateId}/boq`
- `POST /estimates/{estimateId}/approve`
- `GET /projects/{projectId}/audit`
- `GET /workspace/summary` (active-role permissions, workflow, live metrics and tasks)

Planning APIs require authentication, an assigned active role, the matching permission, and resource ownership within the authenticated tenant. A project created through the API stores its owner and tenant; cross-account reads return `404` so the resource is not disclosed. Tenant-wide project access is available only when the selected active role is `ADMIN`; switching an administrator account to another profile removes that wider scope. Internal professionals and site engineers receive no project data until an explicit assignment source grants it. The dashboard and `/projects/{id}` web page list and reopen database-backed projects. Project, requirement, drawing, estimate and audit records commit atomically with the full rehydration snapshot.

New drawing candidates use `geometrySchemaVersion: multi-floor-1`. The persisted `geometry` remains one backward-compatible flat document: `rooms`, `doors` and `windows` are authoritative lists, and every item carries its canonical `floor` (`GROUND`, `FIRST` or `SECOND`). Opening records reference rooms on the same floor. A candidate contains the exact requested floor set rather than a synthesized copy of its ground floor, and `builtUpArea` is the aggregate of the placed room areas across that set. The candidate provenance freezes `requestedFloors` and `roadFacing`; the geometry itself freezes the plot dimensions. Historical drawing artifacts therefore keep the floor count, orientation and dimensions in force when they were generated even if the project's editable brief later changes.

The PDF renderer runs on demand in the Spring API and draws vector floor-plan geometry directly from the persisted candidate; it never depends on a generated preview image. It emits one vector A4 page for every saved floor, ordered ground to second, with floor-specific rooms, doors, windows, dimensions, orientation and `page n/N` labelling. For `multi-floor-1` candidates the renderer requires the exact frozen floor set and rejects an incomplete artifact instead of inventing missing geometry. A legacy candidate without that schema marker remains readable: only its persisted floors are rendered, with an explicit incomplete/regeneration warning when they do not match the requested set. Regenerating the drawing creates a complete current-schema floor set.

The PDF response uses `application/pdf`, `Content-Disposition: inline` and private no-store caching. It records the selected state, project/drawing versions, cost range, validation status and the exact persisted generation provenance. Current layouts are generated by the AVAS deterministic layout engine, so the PDF explicitly records that no generative AI model was used. Selecting a concept clears any prior project selection before the checked PDF is rendered. Rendering is synchronous and stateless, so it requires neither a queue nor a cron job.

The combined project and estimate reports start with a personalised design brief derived from the saved household, plot, floor and home-system selections. It separates the AVAS recommendation from the customer's selection and the spaces actually represented in the frozen drawing, including bedroom/bathroom counts, lift provision and balcony count. A paginated room schedule then records each persisted space, floor, clear dimensions, area and intended furniture/service contents before the option-comparison, floor-plan and costing sheets. These pages are conceptual guidance only; local rules and qualified architectural, structural, fire, accessibility and MEP review remain mandatory.

`GET /workspace/summary` is the server-authoritative role experience. It returns the active `role`, display name, effective `permissions`, role workflow steps, persisted metrics and actionable tasks. Each workflow step contains its required permission, destination and calculated `AVAILABLE` or `RESTRICTED` status. Workflows are defined for `INDIVIDUAL`, `BUILDER`, `INTERNAL_USER`, `SITE_ENGINEER` and `ADMIN`.

## System database verification

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| GET | `/admin/system/database-status` | `AUDIT_READ` | Safe MySQL/Mongo connectivity, database, endpoint, auth mode and object counts |

The endpoint deliberately excludes secrets. `CREDENTIALS_ACCEPTED` confirms that the configured database credential worked. `NO_PASSWORD_LOCAL_ONLY` means the running local MongoDB has authentication disabled and therefore has no password to validate.

## Commerce, payment, refund and wallet

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/commerce/products` | Public server-owned service catalog |
| POST | `/commerce/checkout` | Price product IDs on the server and create order/payment |
| GET | `/commerce/orders` | Current user’s order and payment history |
| GET | `/commerce/orders/{orderId}` | Ownership-checked receipt |
| POST | `/commerce/payments/verify` | Verify provider order, payment and HMAC signature |
| POST | `/commerce/payments/{paymentId}/simulate` | Complete a local TEST payment only |
| POST | `/commerce/orders/{orderId}/refund` | Full provider refund of a paid owned order |
| GET | `/commerce/wallet` | Current balance |
| GET | `/commerce/wallet/history` | Immutable wallet transaction history |
| POST | `/commerce/wallet/topups` | Create a wallet top-up order/payment |

Example authenticated checkout:

```bash
curl -X POST http://localhost:8080/api/v1/commerce/checkout \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -H 'X-Active-Role: INDIVIDUAL' \
  -d '{
    "items": [{"productId":"architect-review","quantity":1}],
    "buyerName":"AVAS Customer",
    "buyerEmail":"customer@example.com",
    "projectId":"the-buyers-persisted-project-id"
  }'
```

The request deliberately contains no amount. Catalog pricing, totals, ownership, state transitions and wallet credits are backend authority.
