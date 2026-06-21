## Why

The current admin console is implemented as hand-written HTML, CSS, and DOM scripts, which makes stateful interactions and ongoing UI maintenance unnecessarily difficult. Migrating it to Vue 3 also allows kMessage to consistently reuse the existing shared UI component library.

## What Changes

- Add a Vue 3 and Vite frontend workspace for the admin console.
- Rebuild login, application credential, channel instance, and grant management flows as Vue components.
- Directly reuse components exported by `@kk-2004/ui-components` without modifying or wrapping the shared library.
- Build the frontend into Spring Boot's static resources so deployment remains a single application.
- Remove the legacy hand-written admin HTML, JavaScript, and stylesheet.

## Capabilities

### New Capabilities
- `vue-admin-console`: Covers the Vue 3 admin console, shared component reuse, authentication flow, and backend-hosted production assets.

### Modified Capabilities

None.

## Impact

- Adds Node.js, pnpm, Vue 3, Vite, Tailwind CSS, and `@kk-2004/ui-components` as frontend build dependencies.
- Replaces files under `src/main/resources/static/admin` with Vite build output.
- Preserves the existing `/api/admin/**` API contract and Spring Boot deployment model.
- Updates Maven packaging and project documentation to include the frontend build.
