## 1. Project Foundation

- [x] 1.1 Bootstrap a single-module Maven application using Spring Boot 3.5.6+, JDK 17+, and `com.kk2004.kmessage.*` packages
- [x] 1.2 Configure GitHub Packages dependency resolution and add `com.kK-2004:kk-common:0.1.2`
- [x] 1.3 Enable kk-common web, exception, and RequestContext integration while explicitly disabling Redis and Redisson auto-configurations
- [x] 1.4 Select and document the relational database, migration tool, persistence layer, HTTP client, test framework, and secret provider
- [x] 1.5 Configure separate API and worker runtime roles plus local development configuration
- [x] 1.6 Add database migrations for callers, API credentials, caller channel grants, channel instances, messages, delivery tasks, and immutable delivery attempts
- [x] 1.7 Define shared domain types for channel types, message envelopes, delivery states, adapter results, and sanitized errors

## 2. Authentication and Channel Configuration

- [x] 2.1 Implement API key creation, secure hashing, authentication, rotation, and caller rate-limit hooks
- [x] 2.2 Implement administrator authorization and protected channel instance create, update, disable, and inspect APIs
- [x] 2.3 Implement credential reference resolution and ensure configuration responses and logs redact secret values
- [x] 2.4 Implement caller-to-channel-instance grant and revoke APIs with authorization enforcement
- [x] 2.5 Register the reserved `email` channel type and reject attempts to enable email instances
- [x] 2.6 Add tests for administrator permissions, secret redaction, grants, disabled instances, and email rejection

## 3. Message Submission

- [x] 3.1 Implement the authenticated message submission API and validate the common message envelope
- [x] 3.2 Implement strict per-channel target and extension-parameter validation for Telegram, Feishu, and DingTalk
- [x] 3.3 Persist accepted messages and delivery tasks atomically before returning `202 Accepted`
- [x] 3.4 Implement caller-scoped idempotency with equivalent-request replay and conflicting-payload rejection
- [x] 3.5 Implement caller-scoped message status and sanitized attempt-history query APIs
- [x] 3.6 Return API results through kk-common `TransDTO` and map domain errors through the kk-common exception hierarchy
- [x] 3.7 Add API tests for authentication, authorization, validation, idempotency, durable acceptance, response envelopes, and query isolation

## 4. Delivery Engine

- [x] 4.1 Define the channel adapter interface and adapter registry, including an unimplemented email extension point
- [x] 4.2 Implement transactional batch claiming, lease recovery, and safe concurrent processing of delivery tasks
- [x] 4.3 Implement message state transitions and immutable attempt recording
- [x] 4.4 Implement transient versus permanent error classification and bounded backoff with jitter
- [x] 4.5 Propagate kk-common RequestContext trace IDs from accepted messages into asynchronous worker execution
- [x] 4.6 Add delivery-engine tests for concurrent claiming, restart recovery, successful delivery, permanent failure, retries, retry exhaustion, and trace propagation

## 5. Channel Adapters

- [x] 5.1 Implement the Telegram text-message adapter with provider response sanitization and error classification
- [x] 5.2 Implement the Feishu text-message adapter with provider response sanitization and error classification
- [x] 5.3 Implement the DingTalk text-message adapter with provider response sanitization and error classification
- [x] 5.4 Add contract tests and mocked provider integration tests for all three adapters

## 6. Operations and Verification

- [x] 6.1 Add structured logs, metrics, health checks, queue-backlog visibility, and delivery-failure alert hooks
- [x] 6.2 Verify logs, metrics, API responses, and stored diagnostics do not expose credentials or complete message content
- [x] 6.3 Add deployment configuration and operator documentation for API, worker, database migrations, secrets, retry policy, and credential rotation
- [ ] 6.4 Run end-to-end tests that submit and deliver messages through Telegram, Feishu, and DingTalk test instances
- [ ] 6.5 Run load and failure-recovery tests, document initial capacity limits, and validate horizontal worker scaling

## 7. Session Management UI and SDK

- [x] 7.1 Remove admin-key configuration, guard, documentation, and header-based management access
- [x] 7.2 Implement administrator Session login, logout, and protected management API/page filtering
- [x] 7.3 Implement web management pages for applications, app credential rotation, channels, and channel grants
- [x] 7.4 Replace API-key caller authentication with appKey/appSecret creation, hashing, rotation, and request authentication
- [x] 7.5 Create a standalone Java SDK artifact using JDK HttpClient for message submission and status queries
- [x] 7.6 Add Session, appKey/appSecret, management UI, and SDK integration tests
- [x] 7.7 Update deployment and consumer documentation and run server plus SDK verification
