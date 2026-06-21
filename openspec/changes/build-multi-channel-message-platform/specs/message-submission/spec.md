## ADDED Requirements

### Requirement: Authenticated message submission
The system SHALL expose an internal API authenticated by `appKey` and `appSecret` that accepts a channel instance, target, text content, caller-scoped idempotency key, and optional channel-specific parameters.

#### Scenario: SDK submits a valid message
- **WHEN** a Java service configures the SDK with a valid endpoint, appKey, and appSecret and sends a valid message
- **THEN** the SDK submits the authenticated HTTP request and returns the accepted message result

#### Scenario: Valid submission is accepted
- **WHEN** an authenticated caller submits a valid request for an authorized enabled channel instance
- **THEN** the system creates one message and returns its identifier with status `accepted`

#### Scenario: Unauthenticated submission is rejected
- **WHEN** a request is submitted without valid caller credentials
- **THEN** the system rejects the request without creating a message

#### Scenario: Unauthorized channel instance is rejected
- **WHEN** an authenticated caller submits a request for a channel instance outside its authorization scope
- **THEN** the system rejects the request without creating a message

### Requirement: Submission validation
The system SHALL validate the target, text content, channel-specific parameters, enabled state, and implemented state of the selected channel before accepting a message.

#### Scenario: Invalid request is rejected
- **WHEN** a submission contains an empty target, empty text, or parameters invalid for the selected channel
- **THEN** the system returns validation details and does not create a delivery task

#### Scenario: Email is not yet available
- **WHEN** a caller attempts to submit a message through the reserved `email` channel type before its adapter is implemented
- **THEN** the system rejects the request as an unsupported channel without creating a delivery task

### Requirement: Idempotent submission
The system MUST treat the idempotency key as unique within each caller and MUST prevent duplicate delivery tasks for repeated equivalent requests.

#### Scenario: Equivalent request is repeated
- **WHEN** a caller repeats an equivalent request with the same idempotency key
- **THEN** the system returns the original message and does not create another delivery task

#### Scenario: Idempotency key is reused for different content
- **WHEN** a caller reuses an idempotency key with a different normalized request payload
- **THEN** the system returns a conflict error and preserves the original message

### Requirement: Message status query
The system SHALL allow an authenticated caller to query the current status and sanitized delivery history of messages it submitted.

#### Scenario: Caller queries its message
- **WHEN** a caller requests a message it submitted
- **THEN** the system returns the message status, timestamps, attempt count, and sanitized failure information

#### Scenario: Caller queries another caller's message
- **WHEN** a caller requests a message submitted by a different caller
- **THEN** the system does not disclose the message
