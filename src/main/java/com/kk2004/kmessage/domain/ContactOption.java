package com.kk2004.kmessage.domain;

/**
 * A selectable send target offered by a channel adapter for the admin "send message" flow.
 *
 * @param id    the target identifier to pass as {@code target} (e.g. Telegram chat id, Feishu chat_id)
 * @param label human-readable name shown in the picker
 * @param type  target category: {@code user}, {@code group}, or {@code channel}
 */
public record ContactOption(String id, String label, String type) {}
