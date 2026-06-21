## MODIFIED Requirements

### Requirement: Supported channel adapters
The system SHALL provide adapters that deliver valid text messages through Telegram and Feishu using the selected channel instance and target. Each adapter SHALL conform to the official provider API contract for authentication, request body, and target identifier handling. DingTalk is no longer a supported channel adapter.

#### Scenario: Telegram delivery succeeds with numeric chat id
- **WHEN** a worker processes a valid Telegram message whose target is a numeric chat id and Telegram accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: Telegram delivery succeeds with channel username
- **WHEN** a worker processes a valid Telegram message whose target is a public channel username in `@channelname` form and Telegram accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: Telegram target validation rejects invalid chat id
- **WHEN** a submission supplies a Telegram target that is neither a numeric chat id nor a `@channelname` username
- **THEN** the adapter rejects the target before delivery and classifies it as a permanent validation failure

#### Scenario: Feishu delivery succeeds with open_id target
- **WHEN** a worker processes a valid Feishu message whose target is an `open_id` (e.g. `ou_xxx`) and Feishu accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: Feishu delivery succeeds with chat_id target
- **WHEN** a worker processes a valid Feishu message whose target is a `chat_id` of a group the bot has joined and Feishu accepts it
- **THEN** the system marks the message `delivered` and records the sanitized provider result

#### Scenario: Feishu tenant access token cached by official expiry semantics
- **WHEN** the Feishu adapter obtains a `tenant_access_token` response whose `expire` field is an absolute epoch-second timestamp per the Feishu Open Platform documentation
- **THEN** the adapter caches the token until that absolute expiry minus a refresh buffer and does not treat the `expire` value as a relative number of seconds

#### Scenario: Feishu token evicted on invalid-token error and refreshed once
- **WHEN** Feishu returns the `99991401` invalid-token error on a send request
- **THEN** the adapter evicts the cached token, obtains a fresh token, retries the send exactly once, and records the result of the retry

### Requirement: Durable asynchronous delivery
The system MUST durably record each accepted message and its pending delivery task before acknowledging the submission, and SHALL process delivery outside the submission request.

#### Scenario: Accepted message survives process restart
- **WHEN** the API process restarts after acknowledging an accepted message but before delivery
- **THEN** a worker can still claim and process the persisted delivery task

#### Scenario: Multiple workers claim tasks
- **WHEN** multiple workers poll for pending delivery tasks concurrently
- **THEN** each task is actively processed by at most one worker at a time

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
