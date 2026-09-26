package io.github.avacadowizard120.mobamputation.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * Lossless working document for the comma-separated projectile rules.
 *
 * <p>An untouched document returns the exact source string. Once edited, rows
 * that were not touched retain their source spelling and separators. This is
 * important for hand-authored class names, duplicate keys, and intentionally
 * malformed values that rely on the parser's fallback behaviour.</p>
 */
final class ProjectileRuleDocument {
    // Match the config parser's literal `split(", *")`: only ordinary
    // spaces after a comma delimit entries. Tabs/newlines remain raw text.
    private static final Pattern SEPARATOR = Pattern.compile(", *");
    private static final Pattern BRACKETED_ID = Pattern.compile(
            "^\\s*\\[([^]\\r\\n]+)]\\s*(?::\\s*(.*?))?\\s*$",
            Pattern.DOTALL
    );
    private static final Pattern MODERN_ID = Pattern.compile(
            "^\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\\s*(?::\\s*(-?\\d+))?\\s*$"
    );
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern JAVA_CLASS = Pattern.compile(
            "(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*"
    );

    enum TargetType {
        ENTITY_ID("mobamputation.config.projectiles.type.entity_id"),
        LEGACY_NAME("mobamputation.config.projectiles.type.legacy_name"),
        JAVA_CLASS("mobamputation.config.projectiles.type.java_class"),
        RAW("mobamputation.config.projectiles.type.raw");

        private final String translationKey;

        TargetType(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

    enum ChanceMode {
        GLOBAL("mobamputation.config.projectiles.mode.global"),
        CUSTOM("mobamputation.config.projectiles.mode.custom"),
        EXCLUDE("mobamputation.config.projectiles.mode.exclude");

        private final String translationKey;

        ChanceMode(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

    final class Rule {
        private final String sourceSegment;
        private String separatorAfter;
        private TargetType targetType;
        private ChanceMode chanceMode;
        private String target;
        private int customChance;
        private boolean dirty;

        private Rule(
                String sourceSegment,
                String separatorAfter,
                TargetType targetType,
                String target,
                ChanceMode chanceMode,
                int customChance,
                boolean dirty
        ) {
            this.sourceSegment = sourceSegment;
            this.separatorAfter = separatorAfter;
            this.targetType = targetType;
            this.target = target;
            this.chanceMode = chanceMode;
            this.customChance = customChance;
            this.dirty = dirty;
        }

        TargetType targetType() {
            return targetType;
        }

        ChanceMode chanceMode() {
            return chanceMode;
        }

        String target() {
            return target;
        }

        int customChance() {
            return customChance;
        }

        void setTargetType(TargetType targetType) {
            if (this.targetType == targetType) {
                return;
            }
            if (targetType == TargetType.RAW && this.targetType != TargetType.RAW) {
                this.target = canonicalSegment();
            } else if (this.targetType == TargetType.RAW && targetType == TargetType.ENTITY_ID) {
                Matcher bracketed = BRACKETED_ID.matcher(this.target);
                Matcher modernId = MODERN_ID.matcher(this.target);
                if (bracketed.matches()) {
                    adoptEntityId(bracketed.group(1), bracketed.group(2));
                } else if (modernId.matches()) {
                    adoptEntityId(modernId.group(1), modernId.group(2));
                }
            }
            this.targetType = targetType;
            markDirty();
        }

        private void adoptEntityId(String id, String chanceText) {
            this.target = id.trim();
            ParsedChance chance = parseChance(chanceText);
            if (chance != null) {
                this.chanceMode = chance.mode();
                this.customChance = chance.customChance();
            }
        }

        void setChanceMode(ChanceMode chanceMode) {
            if (this.chanceMode != chanceMode) {
                this.chanceMode = chanceMode;
                markDirty();
            }
        }

        void setTarget(String target) {
            if (!this.target.equals(target)) {
                this.target = target;
                markDirty();
            }
        }

        void setCustomChance(int customChance) {
            int clamped = Math.max(0, Math.min(100, customChance));
            if (this.customChance != clamped) {
                this.customChance = clamped;
                markDirty();
            }
        }

        boolean targetLooksValid() {
            String candidate = target.trim();
            return switch (targetType) {
                case ENTITY_ID -> candidate.indexOf(':') > 0 && ResourceLocation.tryParse(candidate) != null;
                case LEGACY_NAME -> !candidate.isEmpty() && candidate.indexOf(',') < 0;
                case JAVA_CLASS -> JAVA_CLASS.matcher(candidate).matches();
                case RAW -> !candidate.isEmpty();
            };
        }

        private void markDirty() {
            dirty = true;
            ProjectileRuleDocument.this.dirty = true;
        }

        private String serializeSegment() {
            return dirty ? canonicalSegment() : sourceSegment;
        }

        private String canonicalSegment() {
            if (targetType == TargetType.RAW) {
                return target;
            }

            String cleanTarget = target.trim();
            if (targetType == TargetType.ENTITY_ID) {
                if (cleanTarget.startsWith("[") && cleanTarget.endsWith("]")) {
                    cleanTarget = cleanTarget.substring(1, cleanTarget.length() - 1).trim();
                }
                cleanTarget = "[" + cleanTarget + "]";
            }

            return switch (chanceMode) {
                case GLOBAL -> cleanTarget;
                case CUSTOM -> cleanTarget + ": " + customChance;
                case EXCLUDE -> cleanTarget + ": -1";
            };
        }
    }

    private final String sourceText;
    private final List<Rule> rules = new ArrayList<>();
    private boolean dirty;

    private ProjectileRuleDocument(String sourceText) {
        this.sourceText = sourceText;
        parseRows(sourceText);
    }

    static ProjectileRuleDocument parse(String text) {
        return new ProjectileRuleDocument(text == null ? "" : text);
    }

    List<Rule> rules() {
        return Collections.unmodifiableList(rules);
    }

    Rule addRule() {
        if (!rules.isEmpty() && rules.get(rules.size() - 1).separatorAfter.isEmpty()) {
            rules.get(rules.size() - 1).separatorAfter = ", ";
        }
        Rule rule = new Rule("", "", TargetType.ENTITY_ID, "", ChanceMode.GLOBAL, 100, true);
        rules.add(rule);
        dirty = true;
        return rule;
    }

    void removeRule(Rule rule) {
        int index = rules.indexOf(rule);
        if (index < 0) {
            return;
        }
        rules.remove(index);
        if (index == rules.size() && !rules.isEmpty()) {
            rules.get(rules.size() - 1).separatorAfter = "";
        }
        dirty = true;
    }

    String serialize() {
        if (!dirty) {
            return sourceText;
        }

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < rules.size(); index++) {
            Rule rule = rules.get(index);
            result.append(rule.serializeSegment());
            if (index < rules.size() - 1) {
                result.append(rule.separatorAfter.isEmpty() ? ", " : rule.separatorAfter);
            }
        }
        return result.toString();
    }

    private void parseRows(String text) {
        if (text.isEmpty()) {
            return;
        }

        Matcher matcher = SEPARATOR.matcher(text);
        int segmentStart = 0;
        while (matcher.find()) {
            rules.add(parseRule(text.substring(segmentStart, matcher.start()), matcher.group()));
            segmentStart = matcher.end();
        }
        rules.add(parseRule(text.substring(segmentStart), ""));
    }

    private Rule parseRule(String segment, String separatorAfter) {
        String candidate = segment.trim();
        Matcher bracketed = BRACKETED_ID.matcher(segment);
        if (bracketed.matches()) {
            ParsedChance chance = parseChance(bracketed.group(2));
            if (chance != null) {
                return new Rule(
                        segment,
                        separatorAfter,
                        TargetType.ENTITY_ID,
                        bracketed.group(1).trim(),
                        chance.mode(),
                        chance.customChance(),
                        false
                );
            }
        }

        // Unbracketed namespace:path is ambiguous to the colon-based
        // grammar. Keep it opaque and visibly Raw until the user explicitly
        // chooses Entity ID, which safely canonicalizes it as [namespace:path].
        if (MODERN_ID.matcher(segment).matches()) {
            return new Rule(segment, separatorAfter, TargetType.RAW, segment, ChanceMode.GLOBAL, 100, false);
        }

        int separator = candidate.indexOf(':');
        String target = separator < 0 ? candidate : candidate.substring(0, separator).trim();
        String chanceText = separator < 0 ? null : candidate.substring(separator + 1).trim();
        // Multiple colons are either a modern ID (handled above) or an opaque
        // hand-authored value. Keep the latter in Raw mode instead of guessing.
        if (chanceText == null || chanceText.indexOf(':') < 0) {
            ParsedChance chance = parseChance(chanceText);
            if (!target.isEmpty() && chance != null) {
                TargetType type = JAVA_CLASS.matcher(target).matches()
                        ? TargetType.JAVA_CLASS
                        : TargetType.LEGACY_NAME;
                return new Rule(
                        segment,
                        separatorAfter,
                        type,
                        target,
                        chance.mode(),
                        chance.customChance(),
                        false
                );
            }
        }

        return new Rule(segment, separatorAfter, TargetType.RAW, segment, ChanceMode.GLOBAL, 100, false);
    }

    private static ParsedChance parseChance(String text) {
        if (text == null || text.isEmpty()) {
            return new ParsedChance(ChanceMode.GLOBAL, 100);
        }
        if (!INTEGER.matcher(text).matches()) {
            return null;
        }
        try {
            int chance = Integer.parseInt(text);
            if (chance == -1) {
                return new ParsedChance(ChanceMode.EXCLUDE, 100);
            }
            if (chance >= 0 && chance <= 100) {
                return new ParsedChance(ChanceMode.CUSTOM, chance);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private record ParsedChance(ChanceMode mode, int customChance) {
    }
}
