package br.com.ctcore.identity;

import java.util.UUID;

/** RN-ACE-15: published after the account e-mail change was confirmed and committed; the contact e-mail of the registrations follows it. */
public record AccountEmailChanged(UUID accountId, String newEmail, String ip) {}
