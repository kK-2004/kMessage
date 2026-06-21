## MODIFIED Requirements

### Requirement: Supported channel instance management
The system SHALL provide a web management interface where Session-authenticated administrators can create, update, disable, and inspect channel instances for `telegram` and `feishu`. The channel type metadata exposed to the console SHALL reflect official provider configuration guidance. DingTalk is no longer a supported channel type and SHALL NOT appear in the channel type list.

#### Scenario: Administrator logs in
- **WHEN** an administrator submits valid management credentials through the login page
- **THEN** the system creates an authenticated Session and allows access to management pages and APIs

#### Scenario: Anonymous user opens management page
- **WHEN** a user without an authenticated Session opens a protected management page or API
- **THEN** the system redirects the page request to login or rejects the API request

#### Scenario: Administrator creates a supported channel instance
- **WHEN** an authorized administrator supplies an implemented channel type (`telegram` or `feishu`), unique name, valid configuration, and credential reference
- **THEN** the system stores the channel instance and makes it available according to its enabled state

#### Scenario: Non-administrator attempts configuration change
- **WHEN** a caller without administration permission attempts to create, update, or disable a channel instance
- **THEN** the system rejects the request without changing configuration

#### Scenario: Channel type metadata reflects official provider configuration
- **WHEN** the console requests the list of supported channel types
- **THEN** each entry's `credentialHint`, `setupGuide`, and `targetHint` match the official Telegram Bot API and Feishu Open Platform guidance for that provider, and no `dingtalk` entry is present

### Requirement: Email extension reservation
The system SHALL define `email` as a reserved channel type and SHALL prevent email channel instances from being enabled or created until an email adapter is implemented.

#### Scenario: Administrator attempts to enable or create email
- **WHEN** an administrator creates or updates an `email` channel instance as enabled, or attempts to create an email instance, before email support exists
- **THEN** the system rejects the configuration as unsupported

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

## ADDED Requirements

### Requirement: Channel instance deletion
The system SHALL allow an administrator to delete a channel instance. Before destructive deletion the system SHALL report what references the instance; deletion SHALL cascade to dependent grants, messages, delivery tasks, and delivery attempts.

#### Scenario: Administrator previews channel deletion
- **WHEN** an administrator requests a deletion preview for a channel instance that has granted applications and historical messages
- **THEN** the system returns the count and names of granted applications and the count of historical messages without deleting anything

#### Scenario: Administrator confirms channel deletion
- **WHEN** an administrator confirms deletion of a channel instance after previewing the impact
- **THEN** the system removes the channel instance together with its grants, contacts, messages, delivery tasks, and delivery attempts, and the instance no longer appears in listings

### Requirement: Channel send-target enumeration
The system SHALL allow an administrator to enumerate selectable send targets for an enabled channel instance so the console can offer a picker in the "send message" flow, while preserving a manual-entry fallback when enumeration is unavailable or empty.

#### Scenario: Telegram contacts enumerated from recent chats
- **WHEN** the console requests contacts for an enabled Telegram channel instance whose bot has been contacted
- **THEN** the system returns the recent chats (deduplicated) with their chat id, display label, and category (user/group/channel)

#### Scenario: Feishu contacts enumerated from bot groups
- **WHEN** the console requests contacts for an enabled Feishu channel instance
- **THEN** the system returns the groups the bot has joined with their chat_id, name, and `group` category

#### Scenario: Empty enumeration falls back to manual entry
- **WHEN** an adapter returns no contacts or enumeration fails
- **THEN** the system returns an empty list and the console falls back to manual target entry with a format hint

### Requirement: Contact persistence with incremental sync
The system SHALL persist send targets fetched from providers so the "send message" picker can offer contacts beyond a provider's limited lookback window. Each fetch SHALL incrementally upsert contacts keyed by channel instance and target id, and the contacts endpoint SHALL return the full persisted list (history plus freshly synced).

#### Scenario: First fetch persists new contacts
- **WHEN** the contacts endpoint returns targets that have never been persisted for the channel instance
- **THEN** the system stores each as a new contact and returns them in the merged list

#### Scenario: Subsequent fetch updates known contacts without duplicating
- **WHEN** the contacts endpoint returns a target already persisted for the channel instance (possibly with a changed label)
- **THEN** the system updates the label and refreshes the last-seen timestamp without creating a duplicate, and the returned list has no duplicate target ids

#### Scenario: Empty provider response still serves persisted history
- **WHEN** a provider returns no targets for a channel instance that has persisted contacts
- **THEN** the contacts endpoint still returns the previously persisted contacts so the picker is not empty

#### Scenario: Contact history is removed with the channel
- **WHEN** an administrator deletes a channel instance
- **THEN** the system removes all contacts persisted for that channel instance

### Requirement: Channel instance editing
The system SHALL allow an administrator to edit an existing channel instance's name, enabled state, credential reference, and configuration JSON.

#### Scenario: Administrator renames a channel
- **WHEN** an administrator submits a new non-blank name that is not used by another channel instance
- **THEN** the system updates the channel instance name

#### Scenario: Rename to a duplicate name is rejected
- **WHEN** an administrator submits a name already used by a different channel instance
- **THEN** the system rejects the change without renaming

#### Scenario: Credential left blank on edit is unchanged
- **WHEN** an administrator submits an edit without a credential reference (the stored credential is redacted server-side)
- **THEN** the system leaves the existing credential reference unchanged

#### Scenario: Credential updated on edit
- **WHEN** an administrator submits a new credential reference value
- **THEN** the system replaces the stored credential reference with the new value

## REMOVED Requirements

### Requirement: DingTalk channel support
**Reason**: DingTalk is no longer an operational channel for the platform; the DingTalk channel type and adapter have been fully removed.
**Migration**: Operators must clear any existing `channel_type='DINGTALK'` rows from `channel_instances` (the column is `varchar(32)`, so no schema migration is needed) before deploying this change, and callers must stop submitting to those instances.
