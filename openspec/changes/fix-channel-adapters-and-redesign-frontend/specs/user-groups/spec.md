# Spec: User Groups

## ADDED Requirements

### Requirement: Channel-level users
The system SHALL maintain, per channel instance, a roster of resolved channel targets (e.g. Feishu open_id, Telegram chat id). Users belong to the channel (not to any application) and are shared by every application bound to that channel. A user is uniquely identified within a channel by its channel target id.

#### Scenario: User imported via phone resolution
- **WHEN** an administrator submits phone numbers or emails for a channel and the provider resolves them to channel target ids
- **THEN** the system persists each resolved user at the channel level and reports which inputs could not be resolved

#### Scenario: Duplicate target is not re-created
- **WHEN** the same phone resolves to a target id already persisted for the channel
- **THEN** the system updates the existing user record instead of creating a duplicate

#### Scenario: Channel contacts auto-merge into users
- **WHEN** contacts have been fetched for a channel (via getUpdates / im chats) and an administrator lists the channel's users
- **THEN** the system merges those contacts into the channel-level users on first sight, so every channel contact is manageable as a user without an explicit import

#### Scenario: Administrator deletes a user
- **WHEN** an administrator deletes a channel user
- **THEN** the system removes the user and all of its group memberships across every application bound to that channel

### Requirement: Application-scoped user groups
The system SHALL allow administrators to organize a channel's users into a tree of groups scoped per (application, channel). Groups belong to the application; users belong to the channel. A user may belong to multiple groups. A group has a single optional parent, forming a forest (multiple roots) per (application, channel) scope.

#### Scenario: Administrator creates a root group
- **WHEN** an administrator creates a group without a parent for an (application, channel) scope
- **THEN** the system stores the group as a root node of that scope's forest

#### Scenario: Administrator creates a child group
- **WHEN** an administrator creates a group with a parent in the same (application, channel) scope
- **THEN** the system stores the group as a child of that parent

#### Scenario: Moving a group into its own subtree is rejected
- **WHEN** an administrator moves a group to be a descendant of itself (which would create a cycle)
- **THEN** the system rejects the move without changing the tree

#### Scenario: Deleting a group re-parents its children
- **WHEN** an administrator deletes a group that has children
- **THEN** the system moves the children to the deleted group's parent (or to root) and removes the group's memberships, while leaving the users themselves intact

#### Scenario: A user belongs to multiple groups
- **WHEN** an administrator adds the same user to two different groups in the same scope
- **THEN** the user appears as a member of both groups

### Requirement: Targeted sending to a group or user
The system SHALL let an authenticated application send a message to a group (fan-out to all members) or to a single registered user, by specifying a channel instance plus a group id or user id. Targeting modes (raw target, group, user) are mutually exclusive within one request.

#### Scenario: Sending to a group fans out to members
- **WHEN** an application submits a message with a channel instance and a group id
- **THEN** the system creates one message and delivery task per group member, each targeted to that member's channel target id, and returns a batch summary

#### Scenario: Sending to a single user resolves to one message
- **WHEN** an application submits a message with a channel instance and a user id
- **THEN** the system resolves the user to its channel target id and creates a single message

#### Scenario: Group send is idempotent per member
- **WHEN** the same group send is submitted twice with the same idempotency key
- **THEN** each member's message is deduplicated independently by deriving a per-member idempotency key from the base key and the member id

#### Scenario: Sending to an empty group is rejected
- **WHEN** an application submits a message to a group that has no members
- **THEN** the system rejects the request without creating any messages

#### Scenario: Unauthorized application cannot address a group
- **WHEN** an application attempts to send to a group owned by a different application, or to a user on a channel it is not granted
- **THEN** the system rejects the request

### Requirement: SDK targeting helpers
The SDK SHALL provide helpers to send to a group (returning a batch result) and to send to a registered user, in addition to the existing raw-target send. The raw-target send SHALL remain unchanged.

#### Scenario: SDK sends to a group
- **WHEN** a caller invokes the SDK's group-send method with a channel instance and group id
- **THEN** the SDK posts the request to the message API and returns a batch result listing the fan-out messages

#### Scenario: SDK sends to a user
- **WHEN** a caller invokes the SDK's user-send method with a channel instance and user id
- **THEN** the SDK posts the request and returns a single message result
