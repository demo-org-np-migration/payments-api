# Changelog

Todos los cambios notables de este repo. Formato libre, orden cronológico inverso.

## [Unreleased]

### Fixed
- `amount` viajaba como número JSON hacia fraud-scoring y ledger-core; el contrato manda string
  decimal (las convenciones internas de API) y fraud-scoring lo rechazaba con `422 Input should be a valid string`,
  que nosotros convertíamos en un `500` mudo. Encontrado con tráfico real. `ScoreRequest.amount` y
  `TransferRequest.amount` ahora serializan con `PlainDecimalSerializer`.
- `GET /v1/payments/{id}/status` (y cualquier path con un id que no parsea como UUID) devolvía el
  body default de Spring en vez del envelope `{"error":{...}}` del contrato. Ahora mapea a
  `400 invalid_id`.
- Un 4xx/5xx no manejado de ledger-core o fraud-scoring salía como `500` sin rastro en los logs.
  Ahora se loguea a nivel ERROR (status + body del upstream) y se devuelve `502 upstream_error`.

### Added
- Chart de Helm (`chart/`) para el deploy vía Argo CD, con `ServiceMonitor` y `ConfigMap`.
- Workflows `ci.yml` (tests + `helm lint`) y `build.yml` (build, push a ghcr, bump del tag en
  `gitops`).
- ADR-001 documentando por qué el deploy es GitOps y no algo que corre desde este repo.

## [0.1.0] - 2026-09-09

### Added
- `POST /v1/payments`: flujo completo score → transfer → publish.
- `GET /v1/payments/{id}`, `GET /v1/payments?account_id=`.
- `POST /v1/merchants/{id}/charges`, `GET /v1/merchants/{id}`.
- Publicación de `payments.completed` a Kafka cuando el pago llega a `completed`.
- Migraciones Flyway del esquema `payments` (`V1__init.sql`) y semilla de los dos merchants fijos
  (`V2__merchants_seed.sql`).
- Resource server JWT contra Keycloak con roles de `realm_access.roles`.

### Deprecated
- `GET /v1/payments/{id}/status`. Se elimina el 2026-10-01. Usar `GET /v1/payments/{id}`.
