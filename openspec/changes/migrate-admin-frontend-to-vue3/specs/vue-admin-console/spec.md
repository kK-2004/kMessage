## ADDED Requirements

### Requirement: Vue admin application
The system SHALL provide the admin console as a Vue 3 application served from `/admin/`.

#### Scenario: Authenticated administrator opens console
- **WHEN** an authenticated administrator opens `/admin/`
- **THEN** the Vue application displays application, channel, and grant management capabilities

#### Scenario: Unauthenticated visitor opens console
- **WHEN** a visitor without an authenticated admin Session opens `/admin/`
- **THEN** the Vue application displays the admin login form

### Requirement: Shared component reuse
The admin application SHALL directly use exported components from `@kk-2004/ui-components` for applicable common controls and SHALL NOT modify the shared component library.

#### Scenario: Common control is rendered
- **WHEN** the admin application renders buttons, text inputs, passwords, cards, badges, switches, loading, empty, or copy controls
- **THEN** it uses the corresponding exported shared component

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
