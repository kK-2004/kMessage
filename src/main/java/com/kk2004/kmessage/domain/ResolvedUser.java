package com.kk2004.kmessage.domain;

/**
 * A user resolved from a phone/email lookup against a provider (e.g. Feishu batch_get_id).
 *
 * @param targetId the channel target id to send to (e.g. Feishu open_id)
 * @param phone    the phone used to resolve, if any
 * @param email    the email used to resolve, if any
 * @param name     display name, if the provider returned one
 */
public record ResolvedUser(String targetId, String phone, String email, String name) {}
