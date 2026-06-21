## ADDED Requirements

### Requirement: Supported channel instance management
The system SHALL provide a web management interface where Session-authenticated administrators can create, update, disable, and inspect channel instances for `telegram`, `feishu`, and `dingtalk`.

#### Scenario: Administrator logs in
- **WHEN** an administrator submits valid management credentials through the login page
- **THEN** the system creates an authenticated Session and allows access to management pages and APIs

#### Scenario: Anonymous user opens management page
- **WHEN** a user without an authenticated Session opens a protected management page or API
- **THEN** the system redirects the page request to login or rejects the API request

#### Scenario: Administrator creates a supported channel instance
- **WHEN** an authorized administrator supplies a supported channel type, unique name, valid configuration, and credential reference
- **THEN** the system stores the channel instance and makes it available according to its enabled state

#### Scenario: Non-administrator attempts configuration change
- **WHEN** a caller without administration permission attempts to create, update, or disable a channel instance
- **THEN** the system rejects the request without changing configuration

### Requirement: Email extension reservation
The system SHALL define `email` as a reserved channel type and SHALL prevent email channel instances from being enabled until an email adapter is implemented.

#### Scenario: Administrator attempts to enable email
- **WHEN** an administrator creates or updates an `email` channel instance as enabled before email support exists
- **THEN** the system rejects the enabled configuration as unsupported

### Requirement: Secret protection
The system MUST store channel credentials as protected secret references or encrypted values and MUST NOT expose secret values through configuration queries or logs.

#### Scenario: Administrator inspects channel instance
- **WHEN** an authorized administrator retrieves a channel instance
- **THEN** the system returns non-secret configuration and a redacted credential indicator without returning the credential value

#### Scenario: Channel credential is rotated
- **WHEN** an authorized administrator replaces a channel instance credential reference
- **THEN** subsequent delivery attempts use the new credential without exposing either credential

### Requirement: Caller authorization scopes
The system SHALL allow administrators to control which enabled channel instances each caller may use.

#### Scenario: Channel access is granted
- **WHEN** an administrator grants a caller access to an enabled channel instance
- **THEN** subsequent valid submissions from that caller may select the channel instance

#### Scenario: Channel access is revoked
- **WHEN** an administrator revokes a caller's access to a channel instance
- **THEN** subsequent submissions from that caller for the channel instance are rejected
