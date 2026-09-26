package io.github.avacadowizard120.mobamputation.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Editable, ordered melee-tool chance rules.
 *
 * <p>Rules use one line each: {@code namespace:item=50},
 * {@code #namespace:tag=50}, or {@code *=0}. Exact item rules are evaluated
 * before tag rules regardless of their relative position. Within each kind,
 * the first matching rule wins. The first fallback rule is used only when no
 * item or tag rule matches; with no fallback, the chance is zero.</p>
 *
 * <p>Untouched rows retain their source spelling and line endings. Invalid
 * rows are preserved and ignored by evaluation, allowing a guided editor to
 * repair hand-authored configuration without silently deleting it.</p>
 */
public final class ToolRuleDocument {
    public static final int MIN_CHANCE = 0;
    public static final int MAX_CHANCE = 100;

    private static final String DEFAULT_TEXT = String.join("\n",
            "#minecraft:swords=50",
            "#minecraft:axes=50",
            "#minecraft:pickaxes=33",
            "#minecraft:shovels=25",
            "*=0"
    );

    public enum TargetType {
        ITEM,
        TAG,
        FALLBACK,
        RAW
    }

    public enum ValidationError {
        NONE,
        EMPTY_TARGET,
        INVALID_ITEM_ID,
        INVALID_TAG_ID,
        INVALID_CHANCE,
        MALFORMED
    }

    public final class Rule {
        private final String sourceLine;
        private String separatorAfter;
        private TargetType targetType;
        private String target;
        private ResourceLocation targetId;
        private int chance;
        private String invalidChanceText;
        private boolean dirty;

        private Rule(
                String sourceLine,
                String separatorAfter,
                TargetType targetType,
                String target,
                int chance,
                String invalidChanceText,
                boolean dirty
        ) {
            this.sourceLine = sourceLine;
            this.separatorAfter = separatorAfter;
            this.targetType = targetType;
            this.target = target;
            this.targetId = parseExplicitResourceLocation(target);
            this.chance = chance;
            this.invalidChanceText = invalidChanceText;
            this.dirty = dirty;
        }

        public TargetType targetType() {
            return targetType;
        }

        /** Returns the registry ID without the tag prefix, or the raw row. */
        public String target() {
            return target;
        }

        /** Returns the user-facing selector, including {@code #} or {@code *}. */
        public String selector() {
            return switch (targetType) {
                case ITEM -> target;
                case TAG -> "#" + target;
                case FALLBACK -> "*";
                case RAW -> target;
            };
        }

        public int chance() {
            return chance;
        }

        /** Parsed explicit ID, or {@code null} for fallback, raw, or invalid rows. */
        public ResourceLocation targetId() {
            return targetId;
        }

        public boolean isValid() {
            return validationError() == ValidationError.NONE;
        }

        public ValidationError validationError() {
            if (targetType == TargetType.RAW) {
                return ValidationError.MALFORMED;
            }
            if (invalidChanceText != null || chance < MIN_CHANCE || chance > MAX_CHANCE) {
                return ValidationError.INVALID_CHANCE;
            }
            if (targetType == TargetType.FALLBACK) {
                return ValidationError.NONE;
            }
            String candidate = target.trim();
            if (candidate.isEmpty()) {
                return ValidationError.EMPTY_TARGET;
            }
            if (targetId == null) {
                return targetType == TargetType.TAG
                        ? ValidationError.INVALID_TAG_ID
                        : ValidationError.INVALID_ITEM_ID;
            }
            return ValidationError.NONE;
        }

        public void setTargetType(TargetType targetType) {
            TargetType requested = Objects.requireNonNull(targetType, "targetType");
            if (this.targetType == requested) {
                return;
            }
            if (requested == TargetType.RAW) {
                this.target = canonicalLine();
            } else if (requested == TargetType.FALLBACK) {
                this.target = "";
            } else if (this.targetType == TargetType.FALLBACK) {
                this.target = "";
            } else if (this.targetType == TargetType.RAW) {
                String selector = rawSelector(this.target);
                this.target = selector.startsWith("#") ? selector.substring(1).trim() : selector.trim();
            }
            this.targetType = requested;
            this.targetId = requested == TargetType.ITEM || requested == TargetType.TAG
                    ? parseExplicitResourceLocation(this.target)
                    : null;
            markDirty();
        }

        /** Infers item, tag, or fallback kind from a user-facing selector. */
        public void setSelector(String selector) {
            String candidate = selector == null ? "" : selector.trim();
            if (candidate.equals("*")) {
                targetType = TargetType.FALLBACK;
                target = "";
            } else if (candidate.startsWith("#")) {
                targetType = TargetType.TAG;
                target = candidate.substring(1).trim();
            } else {
                targetType = TargetType.ITEM;
                target = candidate;
            }
            targetId = targetType == TargetType.ITEM || targetType == TargetType.TAG
                    ? parseExplicitResourceLocation(target)
                    : null;
            markDirty();
        }

        /** Sets the ID portion without changing item/tag kind. */
        public void setTarget(String target) {
            String requested = target == null ? "" : target;
            if (!this.target.equals(requested)) {
                this.target = requested;
                this.targetId = targetType == TargetType.ITEM || targetType == TargetType.TAG
                        ? parseExplicitResourceLocation(requested)
                        : null;
                markDirty();
            }
        }

        public void setChance(int chance) {
            int clamped = clampChance(chance);
            if (this.chance != clamped || invalidChanceText != null) {
                this.chance = clamped;
                this.invalidChanceText = null;
                markDirty();
            }
        }

        private String serializeLine() {
            return dirty ? canonicalLine() : sourceLine;
        }

        private String canonicalLine() {
            if (targetType == TargetType.RAW) {
                return target;
            }
            return selector().trim() + "=" + (invalidChanceText == null ? chance : invalidChanceText);
        }

        private void markDirty() {
            dirty = true;
            ToolRuleDocument.this.dirty = true;
        }
    }

    private final String sourceText;
    private final List<Rule> rules = new ArrayList<>();
    private boolean dirty;

    private ToolRuleDocument(String text) {
        sourceText = text;
        parseRows(text);
    }

    public static ToolRuleDocument parse(String text) {
        return new ToolRuleDocument(text == null ? "" : text);
    }

    public static String defaultText() {
        return DEFAULT_TEXT;
    }

    /** Converts the retired fixed-family fields without discarding any value. */
    public static String fromLegacyChances(
            int swordChance,
            int axeChance,
            int pickaxeChance,
            int shovelChance,
            int unmatchedChance
    ) {
        return String.join("\n",
                "#minecraft:swords=" + clampChance(swordChance),
                "#minecraft:axes=" + clampChance(axeChance),
                "#minecraft:pickaxes=" + clampChance(pickaxeChance),
                "#minecraft:shovels=" + clampChance(shovelChance),
                "*=" + clampChance(unmatchedChance)
        );
    }

    public List<Rule> rules() {
        return Collections.unmodifiableList(rules);
    }

    public Rule addRule(TargetType targetType, String target, int chance) {
        TargetType type = Objects.requireNonNull(targetType, "targetType");
        String cleanTarget = target == null ? "" : target;
        if (type == TargetType.TAG && cleanTarget.trim().startsWith("#")) {
            cleanTarget = cleanTarget.trim().substring(1);
        } else if (type == TargetType.FALLBACK) {
            cleanTarget = "";
        }
        Rule rule = new Rule("", "", type, cleanTarget, clampChance(chance), null, true);
        int insertionIndex = rules.size();
        if (type != TargetType.FALLBACK) {
            for (int index = 0; index < rules.size(); index++) {
                if (rules.get(index).targetType == TargetType.FALLBACK) {
                    insertionIndex = index;
                    break;
                }
            }
        }
        rules.add(insertionIndex, rule);
        dirty = true;
        return rule;
    }

    public Rule addItemRule(String itemId, int chance) {
        return addRule(TargetType.ITEM, itemId, chance);
    }

    public Rule addTagRule(String tagId, int chance) {
        return addRule(TargetType.TAG, tagId, chance);
    }

    public Rule addFallbackRule(int chance) {
        return addRule(TargetType.FALLBACK, "", chance);
    }

    public void removeRule(Rule rule) {
        if (rules.remove(rule)) {
            dirty = true;
        }
    }

    public void moveRule(Rule rule, int newIndex) {
        int currentIndex = rules.indexOf(rule);
        if (currentIndex < 0 || rules.size() < 2) {
            return;
        }
        int clampedIndex = Math.max(0, Math.min(rules.size() - 1, newIndex));
        if (currentIndex == clampedIndex) {
            return;
        }
        rules.remove(currentIndex);
        rules.add(clampedIndex, rule);
        dirty = true;
    }

    public String serialize() {
        if (!dirty) {
            return sourceText;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < rules.size(); index++) {
            Rule rule = rules.get(index);
            result.append(rule.serializeLine());
            if (index < rules.size() - 1) {
                result.append(rule.separatorAfter.isEmpty() ? "\n" : rule.separatorAfter);
            } else if (!rule.separatorAfter.isEmpty()) {
                result.append(rule.separatorAfter);
            }
        }
        return result.toString();
    }

    /** Evaluates this document with exact-item precedence over ordered tags. */
    public int chanceFor(ResourceLocation itemId, Predicate<ResourceLocation> tagMatcher) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(tagMatcher, "tagMatcher");

        for (Rule rule : rules) {
            if (rule.targetType == TargetType.ITEM
                    && rule.isValid()
                    && itemId.equals(rule.targetId)) {
                return rule.chance;
            }
        }
        for (Rule rule : rules) {
            if (rule.targetType == TargetType.TAG && rule.isValid()) {
                if (tagMatcher.test(rule.targetId)) {
                    return rule.chance;
                }
            }
        }
        for (Rule rule : rules) {
            if (rule.targetType == TargetType.FALLBACK && rule.isValid()) {
                return rule.chance;
            }
        }
        return 0;
    }

    private void parseRows(String text) {
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '\r' && character != '\n') {
                continue;
            }
            int separatorEnd = index + 1;
            if (character == '\r' && separatorEnd < text.length() && text.charAt(separatorEnd) == '\n') {
                separatorEnd++;
            }
            rules.add(parseRule(text.substring(start, index), text.substring(index, separatorEnd)));
            start = separatorEnd;
            index = separatorEnd - 1;
        }
        if (start < text.length()) {
            rules.add(parseRule(text.substring(start), ""));
        }
    }

    private Rule parseRule(String line, String separatorAfter) {
        String candidate = line.trim();
        int equals = candidate.indexOf('=');
        if (equals < 0 || equals != candidate.lastIndexOf('=')) {
            return new Rule(line, separatorAfter, TargetType.RAW, line, -1, null, false);
        }

        String selector = candidate.substring(0, equals).trim();
        String chanceText = candidate.substring(equals + 1).trim();
        TargetType targetType;
        String target;
        if (selector.equals("*")) {
            targetType = TargetType.FALLBACK;
            target = "";
        } else if (selector.startsWith("#")) {
            targetType = TargetType.TAG;
            target = selector.substring(1).trim();
        } else {
            targetType = TargetType.ITEM;
            target = selector;
        }

        int chance;
        String invalidChanceText = null;
        try {
            chance = Integer.parseInt(chanceText);
        } catch (NumberFormatException ignored) {
            chance = -1;
            invalidChanceText = chanceText;
        }
        return new Rule(line, separatorAfter, targetType, target, chance, invalidChanceText, false);
    }

    private static String rawSelector(String rawLine) {
        int equals = rawLine.indexOf('=');
        return equals < 0 ? rawLine : rawLine.substring(0, equals);
    }

    private static ResourceLocation parseExplicitResourceLocation(String candidate) {
        String trimmed = candidate == null ? "" : candidate.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0
                || separator != trimmed.lastIndexOf(':')
                || separator >= trimmed.length() - 1) {
            return null;
        }
        return ResourceLocation.tryParse(trimmed);
    }

    private static int clampChance(int chance) {
        return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, chance));
    }
}
