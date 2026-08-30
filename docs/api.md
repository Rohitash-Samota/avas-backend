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
- `GET /drawings/{drawingId}/render?style=ELEVATION|PERSPECTIVE` (a generated impression of the concept, or `204` when no image model is configured)

The render endpoint is the one planning response that carries no geometry. It returns an
`ILLUSTRATION` — pixels a diffusion model produced from a sentence describing the concept — together
with the prompt it was generated from, so nothing downstream can display the picture without the
label saying what it is. Its `kind` is fixed in the payload for that reason. The measured plan, the
layout sheet, the 3D massing and the concept PDF are all drawn from validated project geometry and
are unaffected by whether this endpoint answers.

`204` is the ordinary answer, not an error: it means `avas.ai.render-enabled` is off, the AI service
is unreachable, or it is running without an image model. Enabling it requires `AVAS_AI_RENDER_ENABLED`
here and `AVAS_AI_DRAWING_PROVIDER=flux` on the AI service. The call can take tens of seconds — a
diffusion model on a cold pipeline loads tens of gigabytes of weights before it draws anything — so
it must never be on a path that blocks a plan from appearing.
- `POST /projects/{projectId}/estimates/generate`
- `GET /projects/{projectId}/estimates`
- `GET /estimates/{estimateId}`
- `GET /estimates/{estimateId}/boq`
- `POST /estimates/{estimateId}/approve`
- `GET /projects/{projectId}/audit`
- `GET /workspace/summary` (active-role permissions, workflow, live metrics and tasks)
- `POST /plot-documents/analyse` (multipart plot drawing in, proposed outline out; stores nothing)
- `POST /plot-outlines/describe` (plain-language plot description in, proposed outline out; stores nothing)

Planning APIs require authentication, an assigned active role, the matching permission, and resource ownership within the authenticated tenant. A project created through the API stores its owner and tenant; cross-account reads return `404` so the resource is not disclosed. Tenant-wide project access is available only when the selected active role is `ADMIN`; switching an administrator account to another profile removes that wider scope. Internal professionals and site engineers receive no project data until an explicit assignment source grants it. The dashboard and `/projects/{id}` web page list and reopen database-backed projects. Project, requirement, drawing, estimate and audit records commit atomically with the full rehydration snapshot.

Plot outlines are read, never assumed. `POST /plot-documents/analyse` first parses dimension text out of a vector PDF; when `AVAS_AI_OUTLINE_ENABLED` is set it also asks the AVAS AI reader to read the drawing itself, which is the only path that can measure a scan or a photograph. `POST /plot-outlines/describe` draws the outline a customer's own words imply. Both responses are proposals: the polygon is rebuilt through `PlotBoundary` — at least three corners, no self-intersection, a usable enclosed area — before it is returned, the response records `source`, `model` and `confidence`, and nothing becomes project geometry until the customer confirms it through `PUT /basic-details`. Neither endpoint is project-scoped and neither stores the upload.

The chosen finish tier decides what the plan contains, not only what it costs. `STANDARD` draws the household's own programme; `PREMIUM` adds an entrance foyer, a guest WC off the public half of the ground floor and a dedicated store; `LUXURY` adds a covered verandah in front of the foyer, a separate laundry and a dressing room planned between the master bedroom and its bathroom. The tier also orders what surplus floor area is spent on, sizes rooms slightly more generously, and moves the starting point of the parameters a customer is offered — a duplex costed as luxury defaults to a lift shaft, a terrace and two parking bays. Every one of these spaces is ranked below the core programme, so a plate too small to carry them drops the tier's additions before it drops a bedroom, a kitchen or the utility, and the response says which were left out and why.

The optimized programme decides which rooms a home contains, not only how large they are. Whether it comes from AVAS AI or from the deterministic rules, a variant may name spaces the core programme does not, and the planner places them — restricted to the room types the engine can actually draw, never circulation or the stair core, never a bedroom or an ensuite (the accepted recommendation owns that count), capped at four a storey, and ranked below everything the household needs so a tight plate drops them first. Until this existed the programme was read in one place, an area lookup that resized rooms the planner had already chosen, so plot area, budget and household reached the optimizer, changed its answer, and changed no wall.

Parking is one decision rather than two. The approach is used first, because a bay on the front setback costs a paved slab while the same bay inside the building costs a storey of structure and takes its frontage from the living room. The building carries only the cars the approach cannot, bays are sized by the count of cars standing in them rather than by area, and the programme audit counts indoor and outdoor bays alike.

New drawing candidates use `geometrySchemaVersion: multi-floor-3`. Every storey carries circulation the other rooms open off, rooms are held to the dimensions their type is usable at rather than being sized by area alone, and a bathroom marked `ATTACHED_BATHROOM` shares a wall with the bedroom it serves and opens off it. What that circulation *is* depends on which planner arranged the storey. `FloorPlanner`, which draws every candidate unless `AVAS_AI_LAYOUT_ENABLED` is set, places an explicit `CORRIDOR`: a 3.75 ft spine with a strip of rooms either side. AVAS AI arranges the same programme as a hub instead — the entrance opens into the living room, the living runs into the dining, and every room takes its door off that run, with the family lounge doing the same job upstairs — so those candidates carry no `CORRIDOR` at all and the circulation is habitable floor the family uses. A hub layout is refused, and the local planner draws that option instead, if any room overlaps another, leaves the buildable envelope, is drawn below the size its type is usable at, or names a door onto a room it does not share at least 2.5 ft of wall with. Candidates stamped `multi-floor-2` divide the plate into a fixed grid with no circulation at all, so the project page reports them as needing a regenerated version. `multi-floor-2` and `multi-floor-3` both place openings on the same compass as the plot outline: north is the maximum `y` of the planning grid, matching `PlotBoundary` and the setback envelope. Candidates stamped `multi-floor-1` carry openings mirrored north to south; they remain readable, and both the project page and the PDF renderer say so rather than redrawing them under the new convention. The persisted `geometry` also carries the site context the layout was planned against — `plotOutline`, `buildableOutline`, `setbacks`, `plotArea` and `buildableArea` — so the interactive plan and the PDF draw the rooms on the surveyed plot rather than on its bounding box. The persisted `geometry` remains one backward-compatible flat document: `rooms`, `doors` and `windows` are authoritative lists, and every item carries its canonical `floor` (`GROUND`, `FIRST` or `SECOND`). Opening records reference rooms on the same floor. A candidate contains the exact requested floor set rather than a synthesized copy of its ground floor, and `builtUpArea` is the aggregate of the placed room areas across that set. The candidate provenance freezes `requestedFloors` and `roadFacing`; the geometry itself freezes the plot dimensions. Historical drawing artifacts therefore keep the floor count, orientation and dimensions in force when they were generated even if the project's editable brief later changes.

The PDF renderer runs on demand in the Spring API and draws vector floor-plan geometry directly from the persisted candidate; it never depends on a generated preview image. The set opens with one landscape A3 **layout sheet** carrying every storey of the home side by side at one shared scale, furnished to plan scale, with the plot dimensions, a `PLOT DETAILS` panel, a `SUMMARY` schedule counted off the drawing, a staircase detail and a north compass. Everything on that sheet is measured from the persisted geometry, so the schedule can never claim rooms the plates do not show, and the feature strip along its foot prints only the claims the placed rooms and the customer's own parameters support. The working sheets follow it: one vector A4 page for every saved floor, ordered ground to second, with floor-specific rooms, doors, windows, dimensions, orientation and `page n/N` labelling. For every stamped multi-floor schema the renderer requires the exact frozen floor set and rejects an incomplete artifact instead of inventing missing geometry. When the candidate carries a plot outline the plan sheets are drawn on that outline and its setback envelope rather than on a bounding rectangle, so the site sheet and the floor plates describe the same building. A legacy candidate without that schema marker remains readable: only its persisted floors are rendered, with an explicit incomplete/regeneration warning when they do not match the requested set. Regenerating the drawing creates a complete current-schema floor set.

The PDF response uses `application/pdf`, `Content-Disposition: inline` and private no-store caching. It records the selected state, project/drawing versions, cost range, validation status and the exact persisted generation provenance. The PDF records the provenance the candidate was frozen with rather than a fixed claim. A candidate arranged by `FloorPlanner` records the AVAS deterministic layout engine and that no generative AI model was used; one arranged by AVAS AI records the hub layout planner, and names the model only when a model actually placed the rooms — the AI service answering from its own rules is still `DETERMINISTIC`. The sentence a customer reads therefore matches what drew their drawing. Selecting a concept clears any prior project selection before the checked PDF is rendered. Rendering is synchronous and stateless, so it requires neither a queue nor a cron job.

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
