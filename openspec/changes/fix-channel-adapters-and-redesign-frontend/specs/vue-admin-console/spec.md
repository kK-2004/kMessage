## MODIFIED Requirements

### Requirement: Vue admin application
The system SHALL provide the admin console as a Vue 3 application served from `/admin/`. The application SHALL present a cohesive visual system with a persistent top navigation bar, a single-column workspace, and panel-based content, built primarily on `@kk-2004/ui-components` and supplemented by `element-plus` for controls the shared library does not provide.

#### Scenario: Authenticated administrator opens console
- **WHEN** an authenticated administrator opens `/admin/`
- **THEN** the Vue application displays application, channel, and grant management capabilities in the redesigned layout

#### Scenario: Unauthenticated visitor opens console
- **WHEN** a visitor without an authenticated admin Session opens `/admin/`
- **THEN** the Vue application displays the admin login form

### Requirement: Shared component reuse
The admin application SHALL directly use exported components from `@kk-2004/ui-components` for applicable common controls and SHALL use `element-plus` only for controls the shared library does not export (for example dropdowns, tables, and transient notifications). The application SHALL NOT modify the shared component library.

#### Scenario: Common control is rendered
- **WHEN** the admin application renders buttons, text inputs, passwords, cards, badges, switches, loading, empty, or copy controls
- **THEN** it uses the corresponding exported shared component

#### Scenario: Supplementary control is rendered
- **WHEN** the admin application renders a dropdown select, a data table, or a transient message notification not provided by the shared library
- **THEN** it uses the corresponding `element-plus` component

### Requirement: Existing management workflows
The Vue admin application SHALL preserve the existing application credential, channel instance, and application-channel grant workflows.

#### Scenario: Administrator manages resources
- **WHEN** an authenticated administrator creates or rotates an application credential, creates a channel, or changes a grant
- **THEN** the application invokes the existing admin API and refreshes the displayed state

### Requirement: Backend-hosted production assets
The production frontend build SHALL emit assets into Spring Boot static resources and SHALL be included in the packaged application.

#### Scenario: Application is packaged
- **WHEN** the Maven package lifecycle completes
- **THEN** the packaged jar contains the Vue admin entry point and its generated assets
