---
name: easy-next-admin-vibecoding
description: Use when inspecting, modifying, reviewing, or documenting EasyNextAdmin, including Spring Boot backend, Vue admin frontend, sys_menu permissions, dynamic routes, Flyway, workflows, monitoring, audits, reports, deployment, or agent/vibecoding guidance.
---

# EasyNextAdmin Vibe Coding

## Purpose

Use this skill to keep EasyNextAdmin changes aligned with its product boundary and implementation contracts. EasyNextAdmin is a Chinese-first enterprise admin scaffold, not a low-code platform, BI platform, BPM platform, or template showcase.

The main job is contract preservation: backend APIs, frontend feature wrappers, `sys_menu`, permission constants, dynamic routes, Flyway seed data, tests, and docs should move together.

## Read First

- `AGENTS.md` for repo-wide agent rules.
- `README.md` for project overview and quick start.
- `docs/features-and-components.md` before changing user-facing capabilities.
- `docs/architecture.md` before changing backend contracts, permissions, audit, data scope, workflow, schedule, report, or monitoring behavior.
- `docs/deployment.md` before changing Docker, Nginx, profiles, ports, or build output.
- `docs/development/adding-system-page.md` before adding a new admin page or page-level capability.

For skill or agent-entry changes, read:

- `.codex/skills/easy-next-admin-vibecoding/SKILL.md`
- `.codex/skills/easy-next-admin-vibecoding/agents/openai.yaml`
- `AGENTS.md`

## Core Workflow

1. Inspect the existing implementation with `rg` / `rg --files`.
2. Classify the task before editing:
   - Backend/API/SQL: controller, service, entity, mapper, DTO, `EasyPermissions`, Flyway SQL, tests.
   - Frontend/page: `src/features/<domain>`, `src/views`, `PermissionCodes`, dynamic route component paths, tests.
   - Menu/permission: MySQL seed, H2 test seed, backend constants, frontend constants, role seed blocks.
   - Docs/product surface: README, `docs/features-and-components.md`, architecture/deployment docs as relevant.
   - Skill/agent entry: `SKILL.md`, `agents/openai.yaml`, `AGENTS.md`.
3. Identify the full contract before changing code. Do not update only one side of a frontend/backend or permission/menu contract.
4. Make small scoped edits that follow existing naming and layout.
5. Keep Chinese enterprise admin UX: efficient CRUD, dense but readable information, clear permissions, stable internal-network deployment.
6. Verify with the narrowest meaningful command, then report exactly what passed.

## Contract Map

| Change type | Required checks |
| --- | --- |
| Backend endpoint | Standard `Response<T>` / `PageResponse<T>`, `@EasyPermission`, audit for important writes, service boundary, DTO instead of persistence entity exposure. |
| Frontend page | Feature API wrapper and types, loading/empty/error states, no raw Axios in views, `v-permission` for restricted buttons. |
| Menu or permission | `EasyPermissions`, `PermissionCodes`, MySQL `sys_menu`, H2 seed SQL, role permission seed, dynamic `component_path`. |
| Database schema | Flyway migration, MySQL/H2 test compatibility, docs or local startup notes when behavior changes. |
| Workflow behavior | Runtime service, task policy/dispatcher/navigator, message sync, instance/detail views, workflow tests. |
| Docs | Only describe implemented, runnable, verifiable capabilities. Put speculative work in issues or design notes. |
| Skill/agent guide | Keep trigger metadata concise, update `agents/openai.yaml`, avoid duplicating long docs, run skill validation. |

## Non-Negotiables

- Do not create or restore a root `scripts/` directory unless the user explicitly asks.
- Do not add CDN, online icon, or online font dependencies.
- Do not copy a full admin template or keep third-party template branding.
- Do not expose abstract extension-center concepts in product UI or public API.
- Do not write planned or speculative features as if they already exist.
- Do not change frontend/backend response contracts on only one side.
- Do not keep adding behavior into very large files when a local component, helper, or service boundary already exists or can be extracted safely.

## Frontend Rules

- Use Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios, Element Plus, ECharts, and LogicFlow as already present.
- Views live under `easy-next-admin-web/src/views`.
- Business API wrappers and types live under `easy-next-admin-web/src/features/<domain>`.
- Route, menu, and page permission metadata live in backend `sys_menu`; frontend resolves `component_path` through `src/router/dynamicRoutes.ts`.
- Button permissions use `v-permission`; permission constants in `src/permissions/codes.ts` must match backend `EasyPermissions` and `sys_menu`.
- Pages should call feature API functions, not raw Axios.
- If a Vue SFC is already large, prefer extracting stable subcomponents or feature helpers before adding more unrelated logic.
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
- If a service is already large, prefer extracting cohesive domain collaborators instead of appending another workflow branch.

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

Skill validation:

```bash
python3 /Users/lonli2/.codex/skills/.system/skill-creator/scripts/quick_validate.py .codex/skills/easy-next-admin-vibecoding
```
