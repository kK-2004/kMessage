## Context

kMessage currently serves two hand-written admin HTML pages and a global DOM script from Spring Boot static resources. The existing APIs already support browser Session authentication and all required management operations. A shared Vue 3 component library is available as `@kk-2004/ui-components`.

## Goals / Non-Goals

**Goals:**
- Replace the legacy admin pages with a Vue 3 single-page application.
- Directly reuse shared UI components and their design tokens.
- Keep the current admin API contract and single Spring Boot deployment.
- Provide a local Vite development loop with API proxying.

**Non-Goals:**
- Change backend business APIs or authentication semantics.
- Modify the shared UI component library.
- Add client-side routing for multiple public URLs.

## Decisions

- Create an independent `frontend/` Vite workspace. This keeps Node dependencies outside the Maven source tree while giving the admin UI a conventional build and development setup.
- Render login and console states in one Vue application. The application checks the current Session on startup and switches states on `401`, avoiding separate login assets and router complexity.
- Import shared components directly from `@kk-2004/ui-components`. Project-specific layout, native tables, native selects, and notifications remain local because the shared package does not provide equivalent exported components.
- Configure the production frontend build to write into `src/main/resources/static/admin`. Spring Boot remains the only runtime process and existing `/admin/` links remain valid.
- Run the frontend build from Maven's `generate-resources` phase. This ensures packaged jars contain current frontend assets while allowing frontend-only development through pnpm.

## Risks / Trade-offs

- [Maven builds now require Node.js and pnpm] → Document prerequisites and keep frontend commands independently runnable.
- [The shared library's utility classes must be included in Tailwind scanning] → Add an explicit `@source` rule for its built modules.
- [Vite clears generated static assets] → Keep all source files in `frontend/` and treat `src/main/resources/static/admin` as generated output.
- [Session expiry can occur during any API request] → Centralize API handling and return the application to the login state on `401`.

## Migration Plan

1. Add the frontend workspace and migrate the existing workflows.
2. Build assets into the existing `/admin/` static path.
3. Remove legacy source assets after the Vue build is verified.
4. Package the Spring Boot jar and verify it contains the generated admin assets.

Rollback consists of restoring the former static admin files and removing the frontend build execution from Maven.

## Open Questions

None.
