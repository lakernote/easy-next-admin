---
name: easy-next-admin-vibecoding
description: Use when working inside the EasyNextAdmin repository on Spring Boot 3 backend code, Vue 3 admin frontend code, open-source docs, permissions, workflows, monitoring, audits, reports, or deployment guidance.
---

# EasyNextAdmin Vibe Coding

## Purpose

Use this skill to make changes that fit EasyNextAdmin's product direction and codebase conventions. EasyNextAdmin is a Chinese-first enterprise admin scaffold, not a low-code platform, BI platform, BPM platform, or template showcase.

## Read First

- `AGENTS.md` for repo-wide agent rules.
- `README.md` for project overview and quick start.
- `docs/features-and-components.md` before changing user-facing capabilities.
- `docs/architecture.md` before changing backend contracts, permissions, audit, data scope, workflow, schedule, report, or monitoring behavior.
- `docs/deployment.md` before changing Docker, Nginx, profiles, ports, or build output.

## Core Workflow

1. Inspect the existing implementation with `rg` / `rg --files`.
2. Identify the full contract: backend controller/service/entity/mapper, frontend feature API/types/view, capability registration, permissions, and docs.
3. Make small scoped edits that follow existing naming and layout.
4. Keep Chinese enterprise admin UX: efficient CRUD, dense but readable information, clear permissions, stable internal-network deployment.
5. Verify with the narrowest meaningful command, then report exactly what passed.

## Non-Negotiables

- Do not create or restore a root `scripts/` directory unless the user explicitly asks.
- Do not add CDN, online icon, or online font dependencies.
- Do not copy a full admin template or keep third-party template branding.
- Do not expose abstract extension-center concepts in product UI or public API.
- Do not write planned or speculative features as if they already exist.
- Do not change frontend/backend response contracts on only one side.

## Frontend Rules

- Use Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios, Element Plus, ECharts, and LogicFlow as already present.
- Views live under `easy-next-admin-web/src/views`.
- Business API wrappers and types live under `easy-next-admin-web/src/features/<domain>`.
- Route, menu, and page permission metadata live in backend `sys_menu`; frontend resolves `component_path` through `src/router/dynamicRoutes.ts`.
- Button permissions use `v-permission`; permission constants in `src/permissions/codes.ts` must match backend `EasyPermissions` and `sys_menu`.
- Pages should call feature API functions, not raw Axios.
- After frontend changes, run:

```bash
cd easy-next-admin-web
npm run build
```

## Backend Rules

- Use Java 17, Spring Boot 3, MyBatis-Plus, Flyway.
- Keep modules under `easy-next-admin-server/src/main/java/com/laker/admin/module`.
- Shared platform behavior belongs in `common`, `config`, or `infrastructure`.
- Return `Response<T>` or `PageResponse<T>`.
- Protect real write operations and sensitive reads with `@EasyPermission`.
- Add or reuse `EasyPermissions` constants for permission codes.
- Use `@EasyAudit` or the audit collector for important business actions.
- Keep data-scope behavior aligned with current role and department rules.

## Feature Checklist

For a new user-facing capability, check all of these:

- Backend API exists and returns the standard response shape.
- Frontend feature API and types exist.
- View has loading, empty, and basic error states.
- `sys_menu` registration includes directory/page/button resources and the page `component_path`.
- Buttons with restricted actions use explicit permission strings.
- Docs mention the capability if it is part of the product surface.
- Verification command was run.

## Local Commands

Start dependencies:

```bash
docker compose up -d
```

Backend:

```bash
cd easy-next-admin-server
mvn spring-boot:run
```

Frontend:

```bash
cd easy-next-admin-web
npm ci
npm run dev
```

Build checks:

```bash
mvn -pl easy-next-admin-server -am -DskipTests package
cd easy-next-admin-web && npm run build
```
