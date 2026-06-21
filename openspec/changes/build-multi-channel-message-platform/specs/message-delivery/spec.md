## ADDED Requirements

### Requirement: Durable asynchronous delivery
The system MUST durably record each accepted message and its pending delivery task before acknowledging the submission, and SHALL process delivery outside the submission request.

#### Scenario: Accepted message survives process restart
- **WHEN** the API process restarts after acknowledging an accepted message but before delivery
- **THEN** a worker can still claim and process the persisted delivery task

#### Scenario: Multiple workers claim tasks
- **WHEN** multiple workers poll for pending delivery tasks concurrently
- **THEN** each task is actively processed by at most one worker at a time

### Requirement: Supported channel adapters
The system SHALL provide adapters that deliver valid text messages through Telegram, Feishu, and DingTalk using the selected channel instance and target.

#### Scenario: Telegram delivery succeeds
- **WHEN** a worker processes a valid Telegram message and Telegram accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: Feishu delivery succeeds
- **WHEN** a worker processes a valid Feishu message and Feishu accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: DingTalk delivery succeeds
- **WHEN** a worker processes a valid DingTalk message and DingTalk accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

### Requirement: Delivery state and attempt history
The system SHALL maintain a current message state and an immutable record for each delivery attempt, including timestamps, result classification, and sanitized diagnostic information.

#### Scenario: Delivery attempt starts
- **WHEN** a worker begins processing a claimed task
- **THEN** the system records an attempt and changes the current message state to `delivering`

#### Scenario: Delivery succeeds
- **WHEN** a channel adapter reports successful delivery
- **THEN** the system records the successful attempt and changes the current message state to `delivered`

### Requirement: Bounded retry behavior
The system SHALL retry failures classified as transient using bounded backoff and SHALL stop retrying failures classified as permanent or messages that exhaust their retry policy.

#### Scenario: Transient failure can be retried
- **WHEN** an adapter reports a transient timeout, rate limit, or provider server error and retry limits remain
- **THEN** the system records the failure, changes the message state to `retrying`, and schedules a later attempt

#### Scenario: Permanent failure is not retried
- **WHEN** an adapter reports invalid target, invalid credentials, or unsupported content
- **THEN** the system records the failure and changes the message state to `failed` without scheduling another attempt

#### Scenario: Retry limit is exhausted
- **WHEN** a transient failure occurs after the configured retry limit or delivery deadline is exhausted
- **THEN** the system changes the message state to `failed` and does not schedule another automatic attempt

### Requirement: Delivery observability
The system SHALL emit structured logs and metrics for submissions, delivery latency, result states, retries, failures, and queue backlog without exposing credentials or complete message content.

#### Scenario: Delivery failure is observed
- **WHEN** a delivery attempt fails
- **THEN** operators can identify the message ID, caller, channel type, channel instance, attempt number, and sanitized error classification

#### Scenario: Sensitive data is excluded
- **WHEN** the system emits logs or metrics during submission or delivery
- **THEN** credentials and complete message content are not included
