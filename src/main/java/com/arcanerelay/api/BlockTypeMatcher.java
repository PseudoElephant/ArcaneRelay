package com.arcanerelay.api;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

/**
 * Functional interface for matching block type keys.
 * Used to register handlers that apply to multiple block types.
 */
@FunctionalInterface
public interface BlockTypeMatcher {

   /**
    * Tests if this matcher matches the given block type key.
    *
    * @param blockTypeKey the block type key (e.g. "furniture_crude_torch")
    * @return true if this matcher matches the block type
    */
   boolean matches(@Nonnull String blockTypeKey);

   // ─── Factory Methods ───────────────────────────────────────────────────────

   /**
    * Creates a matcher that matches block type keys containing the given substring.
    *
    * @param substring the substring to search for (case-insensitive)
    * @return a matcher that matches keys containing the substring
    */
   static BlockTypeMatcher contains(@Nonnull String substring) {
      String lowerSubstring = substring.toLowerCase();
      return key -> key.toLowerCase().contains(lowerSubstring);
   }

   /**
    * Creates a matcher that matches block type keys containing the given substring (case-sensitive).
    *
    * @param substring the substring to search for
    * @return a matcher that matches keys containing the substring
    */
   static BlockTypeMatcher containsCaseSensitive(@Nonnull String substring) {
      return key -> key.contains(substring);
   }

   /**
    * Creates a matcher that matches block type keys starting with the given prefix.
    *
    * @param prefix the prefix to match (case-insensitive)
    * @return a matcher that matches keys starting with the prefix
    */
   static BlockTypeMatcher startsWith(@Nonnull String prefix) {
      String lowerPrefix = prefix.toLowerCase();
      return key -> key.toLowerCase().startsWith(lowerPrefix);
   }

   /**
    * Creates a matcher that matches block type keys ending with the given suffix.
    *
    * @param suffix the suffix to match (case-insensitive)
    * @return a matcher that matches keys ending with the suffix
    */
   static BlockTypeMatcher endsWith(@Nonnull String suffix) {
      String lowerSuffix = suffix.toLowerCase();
      return key -> key.toLowerCase().endsWith(lowerSuffix);
   }

   /**
    * Creates a matcher that matches block type keys matching the given regex pattern.
    *
    * @param regex the regex pattern to match
    * @return a matcher that matches keys matching the pattern
    */
   static BlockTypeMatcher regex(@Nonnull String regex) {
      Pattern pattern = Pattern.compile(regex);
      return key -> pattern.matcher(key).matches();
   }

   /**
    * Creates a matcher that matches block type keys matching the given regex pattern.
    *
    * @param pattern the compiled regex pattern to match
    * @return a matcher that matches keys matching the pattern
    */
   static BlockTypeMatcher regex(@Nonnull Pattern pattern) {
      return key -> pattern.matcher(key).matches();
   }

   /**
    * Creates a matcher that matches block type keys where the pattern is found anywhere in the key.
    *
    * @param regex the regex pattern to find
    * @return a matcher that matches keys where the pattern is found
    */
   static BlockTypeMatcher regexFind(@Nonnull String regex) {
      Pattern pattern = Pattern.compile(regex);
      return key -> pattern.matcher(key).find();
   }

   /**
    * Creates a matcher that matches any of the given exact keys.
    *
    * @param keys the keys to match
    * @return a matcher that matches any of the given keys
    */
   static BlockTypeMatcher anyOf(@Nonnull String... keys) {
      return key -> {
         for (String k : keys) {
            if (k.equals(key)) {
               return true;
            }
         }
         return false;
      };
   }

   /**
    * Creates a matcher that combines multiple matchers with OR logic.
    *
    * @param matchers the matchers to combine
    * @return a matcher that matches if any of the given matchers match
    */
   static BlockTypeMatcher or(@Nonnull BlockTypeMatcher... matchers) {
      return key -> {
         for (BlockTypeMatcher m : matchers) {
            if (m.matches(key)) {
               return true;
            }
         }
         return false;
      };
   }

   /**
    * Creates a matcher that combines multiple matchers with AND logic.
    *
    * @param matchers the matchers to combine
    * @return a matcher that matches if all of the given matchers match
    */
   static BlockTypeMatcher and(@Nonnull BlockTypeMatcher... matchers) {
      return key -> {
         for (BlockTypeMatcher m : matchers) {
            if (!m.matches(key)) {
               return false;
            }
         }
         return true;
      };
   }

   /**
    * Creates a matcher that negates this matcher.
    *
    * @return a matcher that matches if this matcher does not match
    */
   default BlockTypeMatcher negate() {
      return key -> !this.matches(key);
   }
}
