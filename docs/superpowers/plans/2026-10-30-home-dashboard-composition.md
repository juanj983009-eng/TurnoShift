# Home Dashboard Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recompose the existing Inicio page into a compact three-panel operational dashboard based only on verified TurnoShift data.

**Architecture:** Keep `HomePage` as the sole data owner. Restructure its existing JSX into modules, summary and action regions; use CSS Grid for the desktop composition and one-column stacking below 980 px. Existing API calls, routes and role gates remain unchanged.

**Tech Stack:** React, TypeScript, CSS Grid, Vite.

**Spec:** `docs/design/2026-10-30-home-dashboard-composition.md`

## Global Constraints

- Do not modify backend, OpenAPI, API clients, routes, RBAC, dependencies or I3 state documentation.
- Use only live values already loaded by `HomePage`; no fabricated tasks, planning, charts or metrics.
- Keep Indigio `#5D5FEF` as primary and preserve keyboard-visible links.
- Fit the desktop demonstration viewport without exterior document scrolling.

## Review Focus

- ADMINISTRADOR sees configuration; SUPERVISOR does not receive an unauthorized configuration action.
- A failed summary request retains a useful dashboard and its existing error state.
- Long organization/display names remain contained in cards.
- A viewport below 980 px stacks without horizontal overflow.
- The content does not falsely imply an implemented planning module.

---

### Task 1: Recompose the existing HomePage sections

**Files:**
- Modify: `frontend/src/pages/home/HomePage.tsx`
- Test: `frontend` production type/build check

**Interfaces:**
- Consumes: existing `session`, `operationalSummary`, `canReadOperationalSummary`, and `Link` routes.
- Produces: semantic module, summary and action regions without new data contracts.

- [ ] **Step 1: Preserve the existing role conditions and summary state while grouping cards into the three approved regions.**
- [ ] **Step 2: Keep all links as existing `Link` components and render only routes permitted by their existing role conditions.**
- [ ] **Step 3: Run `cmd /c npm run build` in `frontend`.**

### Task 2: Implement the responsive visual composition

**Files:**
- Modify: `frontend/src/pages/home/HomePage.css`
- Test: `frontend` production type/build check

**Interfaces:**
- Consumes: the semantic class names introduced or retained in Task 1.
- Produces: a three-column desktop grid and a one-column narrow layout.

- [ ] **Step 1: Style the modules, summary and actions as panels with the approved spacing, surface, indigo accent and compact card hierarchy.**
- [ ] **Step 2: Add breakpoint rules below 980 px and compact-height rules that prevent horizontal or exterior overflow.**
- [ ] **Step 3: Run `cmd /c npm run build` in `frontend`; expected result: TypeScript and Vite complete with exit code 0.**
