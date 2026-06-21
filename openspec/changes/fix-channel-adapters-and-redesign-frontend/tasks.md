## 1. Channel adapter fixes

- [x] 1.1 Fix Feishu `tenant_access_token` caching in `HttpChannelAdapters.Feishu`: treat `expire` as absolute epoch-second timestamp via `Instant.ofEpochSecond(expire)` instead of `Instant.now().plusSeconds(expire)`
- [x] 1.2 Add Feishu `receive_id_type` derivation by target form (`ou_`→open_id, `on_`→union_id, `oc_`/digits→chat_id, email-shaped→email, else user_id), with optional `configJson.receive_id_type` override read from the channel instance config
- [x] 1.3 Move the Feishu send URL `receive_id_type` query parameter to be set dynamically per send based on the derived/overridden value
- [x] 1.4 Relax Telegram target validation to accept `^-?\d+$` numeric chat ids and `^@[A-Za-z0-9_]{5,32}$` public channel usernames
- [x] 1.5 Add Telegram text length guard (reject > 4096 UTF-16 code units as permanent failure) in `Telegram.validate`/`send`
- [x] 1.6 Update Feishu and Telegram adapter unit/contract tests to cover numeric/username targets, token expiry semantics, receive_id_type derivation, and token eviction retry

## 2. Channel type metadata and DingTalk removal

- [x] 2.1 Update `ChannelType` metadata (credentialHint, setupGuide, targetHint, description) for Telegram and Feishu to match official provider documentation
- [x] 2.2 Remove `DINGTALK` from the `ChannelType` enum and delete the `HttpChannelAdapters.DingTalk` adapter class; rely on `implemented()` (excludes only EMAIL) for create/enable validation
- [x] 2.3 Keep `ChannelTypeView` (no `availableForCreation` field) and ensure `AdminController.channelTypes()` no longer emits DingTalk
- [x] 2.4 Ensure `ChannelService.create` rejects unimplemented channel types via `implemented()` and that DingTalk is gone entirely
- [x] 2.5 Replace the DingTalk availability test with a metadata test asserting only TELEGRAM/FEISHU/EMAIL are present and DingTalk is absent

## 3. Frontend dependencies and foundation

- [x] 3.1 Add `element-plus` to `frontend/package.json` dependencies and run `pnpm install`
- [x] 3.2 Register element-plus styles/select/table/message as needed in `main.js` or per-component imports
- [x] 3.3 Refresh global `style.css` design tokens (spacing, radius, surface colors, typography) for the redesigned console

## 4. Frontend layout redesign

- [x] 4.1 Redesign `AdminTopbar.vue` into a persistent top navigation bar with brand mark, refresh, and logout actions using `KButton`
- [x] 4.2 Rework `AdminConsolePage.vue` into a single-column workspace with a two-column responsive grid for Application and Channel panels
- [x] 4.3 Redesign `ConsoleHero.vue` to present application/channel summary counts in the new visual system
- [x] 4.4 Keep `SecretAlert.vue` secret one-time display behavior while restyling it to the new visual system

## 5. Frontend panels and forms

- [x] 5.1 Rebuild `ApplicationPanel.vue` using `KCard`/`KInput`/`KButton`/`KStatusBadge`/`KCopyButton` and an `ElTable`-based credential list; keep create/rotate/delete flows
- [x] 5.2 Rebuild `ChannelPanel.vue` creation form with `ElSelect`/`ElOption` (replacing native `<select>`), disabling only unimplemented types via `implemented === false`
- [x] 5.3 Rebuild the channel instance list using `ElTable` while keeping `KStatusBadge`/`KLongText`/`KCopyButton` cell content and the test-send action
- [x] 5.4 Update the channel type setup guide `KAlert` rendering to reflect the corrected Telegram/Feishu metadata
- [x] 5.5 Rebuild `GrantPanel.vue` to the new visual system, preserving grant/revoke calls against the existing admin API
- [x] 5.6 Keep `useAdminConsole.js` state shape and action signatures stable so redesigned components remain drop-in

## 6. Send-message dialog and contact enumeration

- [x] 6.1 Add `ContactOption` DTO and `listContacts` default method on `ChannelAdapter`
- [x] 6.2 Implement Telegram `listContacts` via `getUpdates` (dedup, category mapping) and Feishu `listContacts` via `im/v1/chats`
- [x] 6.3 Add `GET /channels/{id}/contacts` endpoint; rename `/channels/{id}/test` to `/channels/{id}/send-message`
- [x] 6.4 Rebuild the dialog in `ChannelPanel.vue`: load contacts on open, target picker with manual-entry fallback, textarea content, send + result display
- [x] 6.5 Add adapter contract tests for Telegram getUpdates parsing, Feishu chats parsing, and default empty listContacts

## 7. Channel deletion

- [x] 7.1 Add repository cascade-delete methods (`GrantRepository`, `MessageRepository`, `TaskRepository`, `AttemptRepository`)
- [x] 7.2 Implement `ChannelService.previewDelete` and `delete` (FK-safe cascade order)
- [x] 7.3 Add `GET /channels/{id}/delete-preview` and `DELETE /channels/{id}` endpoints
- [x] 7.4 Add `useAdminConsole.deleteChannel` (preview → confirm → delete → refresh) and wire through `App.vue` / `AdminConsolePage.vue`
- [x] 7.5 Add delete + delete-row button in `ChannelPanel.vue`
- [x] 7.6 Add integration test for send-message, delete-preview counts, and delete cascade (grants/messages/tasks/attempts cleared)

## 8. Contact persistence (incremental sync)

- [x] 8.1 Add `V4__channel_contacts.sql` migration (table + unique(channel_instance_id, target_id) + FK)
- [x] 8.2 Add `ChannelContact` entity + `ChannelContactRepository`
- [x] 8.3 Implement `ChannelService.syncContacts` (upsert by channel+targetId, refresh label/lastSeenAt, return persisted full list)
- [x] 8.4 Wire `GET /channels/{id}/contacts` to fetch → sync → return persisted list (transparent incremental sync)
- [x] 8.5 Add contacts to channel delete cascade order
- [x] 8.6 Add integration test for incremental sync (insert, update label no dup, empty fetch serves history) and delete cascade clearing contacts

## 9. Channel editing

- [x] 9.1 Extend `ChannelService.update` signature with `name` (null = unchanged; non-null validates non-blank + dedup excluding self)
- [x] 9.2 Add `name` to `UpdateChannel` record and wire through `PUT /channels/{id}` endpoint
- [x] 9.3 Add `adminApi.updateChannel(id, payload)` and `useAdminConsole.editChannel` action
- [x] 9.4 Add edit dialog in `ChannelPanel.vue` (name, enabled switch, credential blank=unchanged, configJson textarea) + edit button in action column
- [x] 9.5 Add integration test for edit (rename success, duplicate-name reject, credential blank unchanged)

## 10. User groups — data model and user import

- [x] 10.1 Add `V5__user_groups.sql` migration (app_users, user_groups, user_group_members)
- [x] 10.2 Add AppUser/UserGroup/UserGroupMember entities + ResolvedUser DTO
- [x] 10.3 Add AppUserRepository, UserGroupRepository, UserGroupMemberRepository with derived queries
- [x] 10.4 Add `ChannelAdapter.lookupUsers` default method + Feishu batch_get_id implementation
- [x] 10.5 Implement `UserGroupService.importUsers` (resolve via adapter, upsert app_users, report unresolved)

## 11. User groups — tree, membership, and targeted sending

- [x] 11.1 Implement group tree CRUD in `UserGroupService` (create root/child, rename, move with cycle prevention, delete with re-parent)
- [x] 11.2 Implement membership add/remove/list + `expandGroup` (members → target ids)
- [x] 11.3 Extend `MessageService.submit` with groupId/userId fan-out (per-member idempotency key, BatchView result)
- [x] 11.4 Add user/group management endpoints to `AdminController` (import, users, groups, members)
- [x] 11.5 Add user-group cascade to `ChannelService.delete` (null parent_id, members, groups, app_users)
- [x] 11.6 Add integration test (import + cycle prevention + group fan-out send + single-user send)

## 12. User groups — SDK

- [x] 12.1 Add `KMessageClient.sendToGroup` + `sendToUser` + `MessageBatchResult` + `SendTargetMessage`
- [x] 12.2 Add SDK tests for group batch result and single-user result

## 13. User groups — frontend

- [x] 13.1 Add user/group API methods to `adminApi.js`
- [x] 13.2 Create `UserGroupPanel.vue` (app+channel scope selector, user table + import dialog, el-tree group CRUD + member management)
- [x] 13.3 Render `UserGroupPanel` in `AdminConsolePage.vue` (full width below panel-grid)

## 14. Refactor: users belong to channel, groups belong to app

- [x] 14.1 Edit `V5__user_groups.sql`: drop `caller_id` from app_users, unique key → `(channel_instance_id, target_id)`
- [x] 14.2 `AppUser` entity drop `callerId`; `AppUserRepository` → channel-level methods (`findByChannelInstanceId`, `findByChannelInstanceIdAndTargetId`)
- [x] 14.3 `UserGroupService`: user-level methods (`importUsers`/`listUsers`/`deleteUser`/`resolveUser`) drop callerId; `expandGroup` keeps callerId for group ownership but drops user callerId filter; `addMembers` checks user belongs to channel only
- [x] 14.4 `AdminController`: user endpoints become channel-level (`/channels/{channelId}/users*`, no appId); group endpoints keep appId
- [x] 14.5 `MessageService.submit`: `resolveUser` drops caller.id (user is channel-level; grant already checked)
- [x] 14.6 Frontend `adminApi.js`: user methods drop applicationId; `UserGroupPanel.vue` split into channel-users region (channel only) + app-groups region (app+channel)
- [x] 14.7 Update 2 integration tests (channel-level user URLs + `findByChannelInstanceId` assertions)

## 15. Verification and documentation

- [x] 15.1 Run backend build via IntelliJ MCP `build_project` and ensure tests pass
- [x] 15.2 Run `pnpm build` in `frontend/` and confirm the production build emits into Spring Boot static resources
- [x] 15.3 Verify packaged jar contains the redesigned `/admin/` assets
- [ ] 15.4 Manually verify login, application credential, channel creation/edit, send-message, user import, group tree, group/user send, and channel delete flows against a running backend
- [x] 15.5 Update deployment docs with corrected Feishu/Telegram configuration examples and remove all DingTalk references
