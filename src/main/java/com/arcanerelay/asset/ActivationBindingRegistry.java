package com.arcanerelay.asset;

import com.arcanerelay.api.BlockTypeMatcher;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registry for block-type-to-activation bindings.
 * Bindings are registered programmatically; resolution is first-match by registration order.
 * Use this to add bindings from plugins or configuration without coupling to the asset store.
 */
public final class ActivationBindingRegistry {

    public static final String DEFAULT_ACTIVATION_ID = "use_block";

    private final List<BindingEntry> bindings = new ArrayList<>();
    private String defaultActivationId = DEFAULT_ACTIVATION_ID;

    /**
     * Registers a binding: block types matching the pattern resolve to the given activation id.
     * Pattern syntax: {@code exact:key}, {@code contains:sub}, {@code startsWith:prefix},
     * {@code endsWith:suffix}, {@code regex:pattern}. First registered match wins.
     */
    public void registerBinding(@Nonnull String pattern, @Nonnull String activationId) {
        bindings.add(new BindingEntry(matcherFromPattern(pattern), Objects.requireNonNull(activationId)));
    }

    /**
     * Registers a binding using a custom matcher. First registered match wins.
     */
    public void registerBinding(@Nonnull BlockTypeMatcher matcher, @Nonnull String activationId) {
        bindings.add(new BindingEntry(Objects.requireNonNull(matcher), Objects.requireNonNull(activationId)));
    }

    /**
     * Registers a binding with a custom matcher and a priority. First registered match wins.
     */
    public void registerBindingWithPriority(@Nonnull String pattern, @Nonnull String activationId) {
        bindings.addFirst(new BindingEntry(matcherFromPattern(pattern), Objects.requireNonNull(activationId)));
    }

    /**
     * Sets the default activation id when no binding matches (e.g. {@link #DEFAULT_ACTIVATION_ID "use_block"}).
     */
    public void setDefaultActivationId(@Nonnull String activationId) {
        this.defaultActivationId = Objects.requireNonNull(activationId);
    }

    /**
     * Creates a BlockTypeMatcher from a pattern string: exact:x, contains:x, startsWith:x, endsWith:x, regex:x.
     */
    public static BlockTypeMatcher matcherFromPattern(@Nonnull String pattern) {
        String p = pattern.trim();
        if (p.isEmpty()) {
            return key -> false;
        }
        int colon = p.indexOf(':');
        if (colon <= 0 || colon == p.length() - 1) {
            return BlockTypeMatcher.contains(p);
        }
        String kind = p.substring(0, colon).trim().toLowerCase();
        String value = p.substring(colon + 1).trim();
        return switch (kind) {
            case "exact" -> key -> key.equals(value);
            case "contains" -> BlockTypeMatcher.contains(value);
            case "startswith" -> BlockTypeMatcher.startsWith(value);
            case "endswith" -> BlockTypeMatcher.endsWith(value);
            case "regex" -> BlockTypeMatcher.regex(value);
            default -> BlockTypeMatcher.contains(p);
        };
    }

    /**
     * Resolves the activation id for the given block type key (first matching binding, else default).
     */
    @Nonnull
    public String getActivationId(@Nonnull String blockTypeKey) {
        for (BindingEntry entry : bindings) {
            if (entry.matcher.matches(blockTypeKey)) {
                return entry.activationId;
            }
        }
        return defaultActivationId;
    }

    private static final class BindingEntry {
        final BlockTypeMatcher matcher;
        final String activationId;

        BindingEntry(BlockTypeMatcher matcher, String activationId) {
            this.matcher = matcher;
            this.activationId = activationId;
        }
    }
}
