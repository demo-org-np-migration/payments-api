# ADR-001: Deploy por GitOps (Argo CD + Helm), no desde este repo

## Estado

Aceptado.

## Contexto

payments-api es el servicio con más tráfico de Cauri. Un deploy manual o un `kubectl apply` corrido
a mano desde un workflow de CI es una superficie de error demasiado grande para el servicio que
mueve la plata: alguien corre el paso equivocado, contra el cluster equivocado, con el tag
equivocado, y no hay un registro único de "qué versión está corriendo dónde" más allá de lo que
haya quedado en los logs de Actions.

El resto de la organización ya resolvió esto para varios repos con el estilo **C** de
`las convenciones internas de API` (Argo CD + Helm, chart en el propio repo, values por entorno en el repo `gitops`).
payments-api, payments-worker, merchant-portal-bff, web-app y reporting-etl comparten el mismo
patrón.

## Decisión

- El chart vive en `chart/` de este repo. `chart/values.yaml` trae los defaults de staging, pero
  **no** hay `values-staging.yaml` ni `values-prod.yaml` acá: esos viven en
  `gitops/apps/payments-api/`.
- `build.yml` es el único lugar que sabe hacer `docker build`/`push`. Al mergear a `main`, además
  de pushear la imagen a ghcr, hace un commit en `gitops` bumpeando
  `apps/payments-api/values-staging.yaml` con el tag nuevo (`sha-<7>`). Al taggear `v*`, hace lo
  mismo con `values-prod.yaml`.
- Argo CD observa el repo `gitops` y sincroniza el cluster contra lo que ahí dice. Este repo nunca
  ejecuta `kubectl apply` ni `helm upgrade` directamente contra un cluster.

## Consecuencias

- El estado deployado de payments-api en cualquier entorno es siempre lo que dice el archivo de
  values en `gitops`, no lo que alguien recuerda haber corrido. Un rollback es un revert de un
  commit en `gitops`, no una operación manual contra el cluster.
- Si algo no levantó después de un merge a `main`, el primer lugar donde mirar es el estado de la
  `Application` en Argo CD (¿sincronizó? ¿el pod nuevo está `Ready`?), no los logs de `build.yml`
  — ese workflow solo se encarga de dejar el tag correcto en `gitops`, no de que el pod arranque.
- Este repo depende de un secret (`GITOPS_TOKEN`) con permiso de escritura sobre `gitops`. Si ese
  token expira o pierde permisos, `build.yml` sigue pusheando la imagen a ghcr pero el bump a
  `gitops` falla silenciosamente para quien no lea el job hasta el final — vale la pena que alguien
  revise el resultado del job `update-gitops-staging`/`update-gitops-prod`, no solo el de
  `build-and-push`.
