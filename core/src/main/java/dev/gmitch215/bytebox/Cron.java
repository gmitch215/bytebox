package dev.gmitch215.bytebox;

/**
 * The Cron Trigger that started a scheduled invocation.
 *
 * @param expression the cron expression, as written in the Wrangler configuration
 * @param scheduledAt when the invocation was due, in milliseconds since the epoch
 * @since 1.0.0
 */
public record Cron(String expression, long scheduledAt) {}
