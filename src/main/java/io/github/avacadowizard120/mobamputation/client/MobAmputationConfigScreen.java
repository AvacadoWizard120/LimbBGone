package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.ArmorProtection;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.BloodSurfacePhysics;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.CreeperAmputation;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.DeathDismemberment;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.DeathDismembermentMode;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.PlayerTrauma;
import io.github.avacadowizard120.mobamputation.config.ToolRuleDocument;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Clear, category-based in-game editor for Mob Amputation settings. */
public final class MobAmputationConfigScreen extends Screen {
    private static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);

    private enum Page {
        OVERVIEW("overview"),
        EFFECTS("effects"),
        EFFECTS_ADVANCED("effects_advanced"),
        COMBAT("combat"),
        TOOL_RULES("tool_rules"),
        PROJECTILE_RULES("projectile_rules"),
        PLAYERS("players"),
        PLAYERS_ADVANCED("players_advanced"),
        DEATH("death"),
        DEATH_ADVANCED("death_advanced");

        private final String key;

        Page(String key) {
            this.key = key;
        }

        String titleKey() {
            return "mobamputation.config.page." + key + ".title";
        }

        Page parent() {
            return switch (this) {
                case EFFECTS, COMBAT, PLAYERS, DEATH -> OVERVIEW;
                case EFFECTS_ADVANCED -> EFFECTS;
                case TOOL_RULES, PROJECTILE_RULES -> COMBAT;
                case PLAYERS_ADVANCED -> PLAYERS;
                case DEATH_ADVANCED -> DEATH;
                case OVERVIEW -> OVERVIEW;
            };
        }
    }

    private final Screen parent;
    private MobAmputationConfig.Snapshot values;
    private ProjectileRuleDocument projectileRules;
    private ToolRuleDocument toolRules;
    private Page page = Page.OVERVIEW;

    private SettingsList settingsList;
    private ProjectileRuleList projectileList;
    private ToolRuleList toolList;
    private Button backButton;
    private Button doneButton;
    private boolean sessionControlled;
    private List<FormattedCharSequence> sessionNoticeLines = List.of();
    private int sessionNoticeY;

    private EditBox gibTime;
    private EditBox gibGroundTime;
    private EditBox bloodCount;
    private EditBox bloodSurfaceCheckInterval;
    private EditBox unlistedProjectileChance;
    private EditBox fishingChance;
    private EditBox armBleedDuration;
    private EditBox armBleedInterval;
    private EditBox armBleedDamage;
    private EditBox headBleedMin;
    private EditBox headBleedMax;
    private EditBox headBleedInterval;
    private EditBox headBleedDamage;
    private EditBox bandageCooldown;
    private EditBox armorGlobalReduction;
    private EditBox armorCoveredReduction;
    private EditBox armorToughnessReduction;
    private EditBox armorProtectionCap;
    private EditBox reinforcementReduction;
    private EditBox deathGibTime;
    private EditBox deathGibGroundTime;
    private EditBox deathBloodCount;

    public MobAmputationConfigScreen(Screen parent) {
        super(Component.translatable("mobamputation.config.title"));
        this.parent = parent;
        this.values = MobAmputationConfig.get();
        this.projectileRules = ProjectileRuleDocument.parse(values.projectileList());
        this.toolRules = ToolRuleDocument.parse(values.toolRules());
        ensureFallbackRule();
    }

    @Override
    protected void init() {
        clearPageState();
        sessionControlled = MobAmputationConfig.hasSessionOverride();

        int contentWidth = Math.max(180, Math.min(460, width - 30));
        contentWidth = Math.min(contentWidth, width - 10);
        int left = (width - contentWidth) / 2;
        int gap = 5;
        int halfWidth = (contentWidth - gap) / 2;
        int listTop = 42;
        sessionNoticeLines = List.of();
        if (sessionControlled && (page == Page.TOOL_RULES || page == Page.PROJECTILE_RULES)) {
            sessionNoticeY = 29;
            sessionNoticeLines = font.split(
                    Component.translatable("mobamputation.config.server_controlled"),
                    Math.max(80, contentWidth)
            );
            // Leave one text row between the notice and the list header. This
            // grows naturally when a translation wraps on a compact screen.
            listTop = Math.max(listTop, sessionNoticeY + sessionNoticeLines.size() * 10 + 11);
        }

        if (page != Page.OVERVIEW) {
            backButton = withTooltip(Button.builder(
                    Component.translatable("mobamputation.config.back"),
                    button -> switchPage(page.parent())
            ).bounds(left, 7, Math.min(72, contentWidth / 4), 20).build(),
                    "mobamputation.config.back.tooltip");
            addRenderableWidget(backButton);
        }

        if (page == Page.TOOL_RULES) {
            initToolRulesPage(left, contentWidth, listTop);
        } else if (page == Page.PROJECTILE_RULES) {
            initProjectileRulesPage(left, contentWidth, listTop);
        } else {
            int listBottom = height - 36;
            settingsList = new SettingsList(minecraft, contentWidth, Math.max(28, listBottom - listTop), listTop, 28);
            settingsList.setX(left);
            addRenderableWidget(settingsList);
            switch (page) {
                case OVERVIEW -> initOverviewPage();
                case EFFECTS -> initEffectsPage();
                case EFFECTS_ADVANCED -> initAdvancedEffectsPage();
                case COMBAT -> initCombatPage();
                case PLAYERS -> initPlayersPage();
                case PLAYERS_ADVANCED -> initAdvancedPlayersPage();
                case DEATH -> initDeathPage();
                case DEATH_ADVANCED -> initAdvancedDeathPage();
                case TOOL_RULES, PROJECTILE_RULES -> throw new IllegalStateException("Custom list page");
            }
        }

        int buttonsY = height - 28;
        doneButton = withTooltip(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(left, buttonsY, halfWidth, 20).build(), "mobamputation.config.done.tooltip");
        addRenderableWidget(doneButton);
        addRenderableWidget(withTooltip(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + halfWidth + gap, buttonsY, contentWidth - halfWidth - gap, 20).build(),
                "mobamputation.config.cancel.tooltip"));
    }

    private void clearPageState() {
        settingsList = null;
        projectileList = null;
        toolList = null;
        backButton = null;
        gibTime = null;
        gibGroundTime = null;
        bloodCount = null;
        bloodSurfaceCheckInterval = null;
        unlistedProjectileChance = null;
        fishingChance = null;
        armBleedDuration = null;
        armBleedInterval = null;
        armBleedDamage = null;
        headBleedMin = null;
        headBleedMax = null;
        headBleedInterval = null;
        headBleedDamage = null;
        bandageCooldown = null;
        armorGlobalReduction = null;
        armorCoveredReduction = null;
        armorToughnessReduction = null;
        armorProtectionCap = null;
        reinforcementReduction = null;
        deathGibTime = null;
        deathGibGroundTime = null;
        deathBloodCount = null;
    }

    private void initOverviewPage() {
        addNavigation("effects", Page.EFFECTS);
        addNavigation("combat", Page.COMBAT);
        addNavigation("players", Page.PLAYERS);
        addNavigation("death", Page.DEATH);
    }

    private void initEffectsPage() {
        addBoolean("blood", values.blood(), true,
                value -> values = withBooleans(value, values.bloodSplurt(), values.greenBlood(), values.playerGibs(),
                        values.gibPushing(), values.headlessDeath(), values.allowProjectileGibbing()));
        addBoolean("bloodSplurt", values.bloodSplurt(), true,
                value -> values = withBooleans(values.blood(), value, values.greenBlood(), values.playerGibs(),
                        values.gibPushing(), values.headlessDeath(), values.allowProjectileGibbing()));
        addBoolean("greenBlood", values.greenBlood(), true,
                value -> values = withBooleans(values.blood(), values.bloodSplurt(), value, values.playerGibs(),
                        values.gibPushing(), values.headlessDeath(), values.allowProjectileGibbing()));
        addBoolean("gibPushing", values.gibPushing(), true,
                value -> values = withBooleans(values.blood(), values.bloodSplurt(), values.greenBlood(),
                        values.playerGibs(), value, values.headlessDeath(), values.allowProjectileGibbing()));
        addNavigation("advanced_effects", Page.EFFECTS_ADVANCED);
    }

    private void initAdvancedEffectsPage() {
        gibTime = integerField("gibTime", values.gibTime(), true);
        gibGroundTime = integerField("gibGroundTime", values.gibGroundTime(), true);
        bloodCount = integerField("bloodCount", values.bloodCount(), true);
        BloodSurfacePhysics physics = values.bloodSurfacePhysics();
        addBoolean("dynamicBloodSurfaces", physics.enabled(), true,
                value -> updateBloodSurfacePhysics(new BloodSurfacePhysics(
                        value, bloodSurfacePhysics().dripping(), bloodSurfacePhysics().checkIntervalTicks(),
                        bloodSurfacePhysics().entityCollisions())));
        addBoolean("bloodSurfaceDripping", physics.dripping(), true,
                value -> updateBloodSurfacePhysics(new BloodSurfacePhysics(
                        bloodSurfacePhysics().enabled(), value, bloodSurfacePhysics().checkIntervalTicks(),
                        bloodSurfacePhysics().entityCollisions())));
        addBoolean("bloodEntityCollisions", physics.entityCollisions(), true,
                value -> updateBloodSurfacePhysics(new BloodSurfacePhysics(
                        bloodSurfacePhysics().enabled(), bloodSurfacePhysics().dripping(),
                        bloodSurfacePhysics().checkIntervalTicks(), value)));
        bloodSurfaceCheckInterval = integerField(
                "bloodSurfaceCheckIntervalTicks",
                physics.checkIntervalTicks(),
                true
        );
    }

    private void initCombatPage() {
        boolean editable = !sessionControlled;
        addBoolean("headlessDeath", values.headlessDeath(), editable,
                value -> values = withBooleans(values.blood(), values.bloodSplurt(), values.greenBlood(),
                        values.playerGibs(), values.gibPushing(), value, values.allowProjectileGibbing()));
        unlistedProjectileChance = integerField(
                "unlistedProjectileChance",
                values.unlistedProjectileChance(),
                editable
        );
        fishingChance = integerField("fishingChance", values.fishingChance(), editable);
        addBoolean("allowProjectileGibbing", values.allowProjectileGibbing(), editable,
                value -> values = withBooleans(values.blood(), values.bloodSplurt(), values.greenBlood(),
                        values.playerGibs(), values.gibPushing(), values.headlessDeath(), value));
        addBoolean("enchantmentsEnabled", values.enchantmentsEnabled(), editable,
                value -> values = withExtensions(value, values.decapitationCamera()));
        addBoolean("decapitationCamera", values.decapitationCamera(), true,
                value -> values = withExtensions(values.enchantmentsEnabled(), value));
        addNavigation("tool_rules", Page.TOOL_RULES);
        addNavigation("projectile_rules", Page.PROJECTILE_RULES);
    }

    private void initPlayersPage() {
        boolean editable = !sessionControlled;
        PlayerTrauma trauma = values.playerTrauma();
        ArmorProtection armor = values.armorProtection();
        addBoolean("playerGibs", values.playerGibs(), editable,
                value -> values = withBooleans(values.blood(), values.bloodSplurt(), values.greenBlood(), value,
                        values.gibPushing(), values.headlessDeath(), values.allowProjectileGibbing()));
        addBoolean("playerArmAmputation", trauma.armAmputation(), editable,
                value -> updatePlayerTrauma(new PlayerTrauma(
                        value, trauma().fatalDecapitation(), trauma().headBleedoutMinTicks(), trauma().headBleedoutMaxTicks(),
                        trauma().headBleedIntervalTicks(), trauma().headBleedDamageTenths(), trauma().armBleeding(),
                        trauma().armBleedDurationTicks(), trauma().armBleedIntervalTicks(), trauma().armBleedDamageTenths(),
                        trauma().bandagesEnabled(), trauma().bandageCooldownTicks())));
        addBoolean("fatalPlayerDecapitation", trauma.fatalDecapitation(), editable,
                value -> updatePlayerTrauma(new PlayerTrauma(
                        trauma().armAmputation(), value, trauma().headBleedoutMinTicks(), trauma().headBleedoutMaxTicks(),
                        trauma().headBleedIntervalTicks(), trauma().headBleedDamageTenths(), trauma().armBleeding(),
                        trauma().armBleedDurationTicks(), trauma().armBleedIntervalTicks(), trauma().armBleedDamageTenths(),
                        trauma().bandagesEnabled(), trauma().bandageCooldownTicks())));
        addBoolean("armBleeding", trauma.armBleeding(), editable,
                value -> updatePlayerTrauma(new PlayerTrauma(
                        trauma().armAmputation(), trauma().fatalDecapitation(), trauma().headBleedoutMinTicks(),
                        trauma().headBleedoutMaxTicks(), trauma().headBleedIntervalTicks(), trauma().headBleedDamageTenths(),
                        value, trauma().armBleedDurationTicks(), trauma().armBleedIntervalTicks(),
                        trauma().armBleedDamageTenths(), trauma().bandagesEnabled(), trauma().bandageCooldownTicks())));
        addBoolean("bandagesEnabled", trauma.bandagesEnabled(), editable,
                value -> updatePlayerTrauma(new PlayerTrauma(
                        trauma().armAmputation(), trauma().fatalDecapitation(), trauma().headBleedoutMinTicks(),
                        trauma().headBleedoutMaxTicks(), trauma().headBleedIntervalTicks(), trauma().headBleedDamageTenths(),
                        trauma().armBleeding(), trauma().armBleedDurationTicks(), trauma().armBleedIntervalTicks(),
                        trauma().armBleedDamageTenths(), value, trauma().bandageCooldownTicks())));
        addBoolean("armorAmputationProtection", armor.enabled(), editable,
                value -> updateArmor(new ArmorProtection(
                        value, armor().globalReductionPerArmorPoint(), armor().coveredReductionPerArmorPoint(),
                        armor().toughnessReductionPerPoint(), armor().maximumReduction(), armor().enchantmentsEnabled(),
                        armor().reinforcementReductionPerLevel(), armor().anatomicalIntegrityEnabled())));
        addBoolean("armorProtectionEnchantments", armor.enchantmentsEnabled(), editable,
                value -> updateArmor(new ArmorProtection(
                        armor().enabled(), armor().globalReductionPerArmorPoint(), armor().coveredReductionPerArmorPoint(),
                        armor().toughnessReductionPerPoint(), armor().maximumReduction(), value,
                        armor().reinforcementReductionPerLevel(), armor().anatomicalIntegrityEnabled())));
        addBoolean("anatomicalIntegrityEnabled", armor.anatomicalIntegrityEnabled(), editable,
                value -> updateArmor(new ArmorProtection(
                        armor().enabled(), armor().globalReductionPerArmorPoint(), armor().coveredReductionPerArmorPoint(),
                        armor().toughnessReductionPerPoint(), armor().maximumReduction(), armor().enchantmentsEnabled(),
                        armor().reinforcementReductionPerLevel(), value)));
        addNavigation("advanced_players", Page.PLAYERS_ADVANCED);
    }

    private void initAdvancedPlayersPage() {
        boolean editable = !sessionControlled;
        PlayerTrauma trauma = values.playerTrauma();
        ArmorProtection armor = values.armorProtection();
        armBleedDuration = integerField("armBleedDurationTicks", trauma.armBleedDurationTicks(), editable);
        armBleedInterval = integerField("armBleedIntervalTicks", trauma.armBleedIntervalTicks(), editable);
        armBleedDamage = integerField("armBleedDamageTenths", trauma.armBleedDamageTenths(), editable);
        bandageCooldown = integerField("bandageCooldownTicks", trauma.bandageCooldownTicks(), editable);
        headBleedMin = integerField("headBleedoutMinTicks", trauma.headBleedoutMinTicks(), editable);
        headBleedMax = integerField("headBleedoutMaxTicks", trauma.headBleedoutMaxTicks(), editable);
        headBleedInterval = integerField("headBleedIntervalTicks", trauma.headBleedIntervalTicks(), editable);
        headBleedDamage = integerField("headBleedDamageTenths", trauma.headBleedDamageTenths(), editable);
        armorGlobalReduction = integerField(
                "armorGlobalReductionPerPoint",
                armor.globalReductionPerArmorPoint(),
                editable
        );
        armorCoveredReduction = integerField(
                "armorCoveredReductionPerPoint",
                armor.coveredReductionPerArmorPoint(),
                editable
        );
        armorToughnessReduction = integerField(
                "armorToughnessReductionPerPoint",
                armor.toughnessReductionPerPoint(),
                editable
        );
        armorProtectionCap = integerField("armorProtectionCap", armor.maximumReduction(), editable);
        reinforcementReduction = integerField(
                "reinforcementReductionPerLevel",
                armor.reinforcementReductionPerLevel(),
                editable
        );
    }

    private void initDeathPage() {
        boolean gameplayEditable = !sessionControlled;
        CreeperAmputation creeper = values.creeperAmputation();
        DeathDismemberment death = values.deathDismemberment();
        addBoolean("creeperAmputation", creeper.enabled(), gameplayEditable,
                value -> updateCreeper(new CreeperAmputation(value, creeperConfig().alwaysGreenBlood())));
        addBoolean("creeperGreenBlood", creeper.alwaysGreenBlood(), gameplayEditable,
                value -> updateCreeper(new CreeperAmputation(creeperConfig().enabled(), value)));

        CycleButton<DeathDismembermentMode> mode = CycleButton
                .<DeathDismembermentMode>builder(value -> Component.translatable(
                        "mobamputation.config.death_mode." + value.name().toLowerCase(Locale.ROOT)))
                .withValues(DeathDismembermentMode.values())
                .withInitialValue(death.mode())
                .create(0, 0, 100, 20, Component.translatable(nameKey("deathDismembermentMode")),
                        (button, value) -> updateDeath(deathConfig().withMode(value)));
        withTooltip(mode, tooltipKey("deathDismembermentMode"));
        settingsList.addWidget(mode);

        addBoolean("deathDismembermentExplosions", death.explosions(), true,
                value -> updateDeath(deathConfig().withTriggers(value, deathConfig().ironGolems())));
        addBoolean("deathDismembermentIronGolems", death.ironGolems(), true,
                value -> updateDeath(deathConfig().withTriggers(deathConfig().explosions(), value)));
        addNavigation("advanced_death", Page.DEATH_ADVANCED);
    }

    private void initAdvancedDeathPage() {
        DeathDismemberment death = values.deathDismemberment();
        deathGibTime = integerField("deathDismembermentGibTime", death.gibTime(), true);
        deathGibGroundTime = integerField("deathDismembermentGibGroundTime", death.gibGroundTime(), true);
        deathBloodCount = integerField("deathDismembermentBloodCount", death.bloodCount(), true);
        addBoolean("deathDismembermentBlood", death.blood(), true,
                value -> updateDeath(deathConfig().withClientSettings(
                        deathConfig().gibTime(), deathConfig().gibGroundTime(), value,
                        deathConfig().bloodCount(), deathConfig().greenBlood(), deathConfig().gibPushing())));
        addBoolean("deathDismembermentGreenBlood", death.greenBlood(), true,
                value -> updateDeath(deathConfig().withClientSettings(
                        deathConfig().gibTime(), deathConfig().gibGroundTime(), deathConfig().blood(),
                        deathConfig().bloodCount(), value, deathConfig().gibPushing())));
        addBoolean("deathDismembermentGibPushing", death.gibPushing(), true,
                value -> updateDeath(deathConfig().withClientSettings(
                        deathConfig().gibTime(), deathConfig().gibGroundTime(), deathConfig().blood(),
                        deathConfig().bloodCount(), deathConfig().greenBlood(), value)));
    }

    private void initProjectileRulesPage(int left, int contentWidth, int listTop) {
        boolean editable = !sessionControlled;
        int actionY = height - 52;
        int listHeight = Math.max(26, actionY - 4 - listTop);
        projectileList = new ProjectileRuleList(minecraft, contentWidth, listHeight, listTop, 28);
        projectileList.setX(left);
        projectileList.setEditable(editable);
        projectileList.load(projectileRules.rules());
        addRenderableWidget(projectileList);

        int gap = 5;
        int actionWidth = (contentWidth - gap * 2) / 3;
        Button add = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.add"),
                button -> {
                    ProjectileRuleDocument.Rule rule = projectileRules.addRule();
                    projectileList.addRule(rule);
                    setFocused(projectileList);
                }
        ).bounds(left, actionY, actionWidth, 20).build(), "mobamputation.config.projectiles.add.tooltip");
        Button advanced = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.advanced"),
                button -> openProjectileRawEditor()
        ).bounds(left + actionWidth + gap, actionY, actionWidth, 20).build(),
                "mobamputation.config.projectiles.advanced.tooltip");
        Button help = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.help"),
                button -> minecraft.setScreen(new ProjectileRuleHelpScreen(this))
        ).bounds(left + (actionWidth + gap) * 2, actionY, contentWidth - (actionWidth + gap) * 2, 20).build(),
                "mobamputation.config.projectiles.help.tooltip");
        add.active = editable;
        advanced.active = editable;
        if (!editable) {
            serverControlledTooltip(add);
            serverControlledTooltip(advanced);
        }
        addRenderableWidget(add);
        addRenderableWidget(advanced);
        addRenderableWidget(help);
    }

    private void initToolRulesPage(int left, int contentWidth, int listTop) {
        boolean editable = !sessionControlled;
        int actionY = height - 52;
        int listHeight = Math.max(26, actionY - 4 - listTop);
        toolList = new ToolRuleList(minecraft, contentWidth, listHeight, listTop, 28);
        toolList.setX(left);
        toolList.setEditable(editable);
        toolList.load(toolRules.rules());
        addRenderableWidget(toolList);

        int gap = 5;
        int actionWidth = (contentWidth - gap * 2) / 3;
        Button addItem = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.add_item"),
                button -> addToolRule(ToolRuleDocument.TargetType.ITEM)
        ).bounds(left, actionY, actionWidth, 20).build(),
                "mobamputation.config.tool_rules.add_item.tooltip");
        Button addTag = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.add_tag"),
                button -> addToolRule(ToolRuleDocument.TargetType.TAG)
        ).bounds(left + actionWidth + gap, actionY, actionWidth, 20).build(),
                "mobamputation.config.tool_rules.add_tag.tooltip");
        Button raw = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.advanced"),
                button -> openToolRawEditor()
        ).bounds(left + (actionWidth + gap) * 2, actionY, contentWidth - (actionWidth + gap) * 2, 20).build(),
                "mobamputation.config.tool_rules.advanced.tooltip");
        addItem.active = editable;
        addTag.active = editable;
        raw.active = editable;
        if (!editable) {
            serverControlledTooltip(addItem);
            serverControlledTooltip(addTag);
            serverControlledTooltip(raw);
        }
        addRenderableWidget(addItem);
        addRenderableWidget(addTag);
        addRenderableWidget(raw);
    }

    private void addToolRule(ToolRuleDocument.TargetType targetType) {
        ToolRuleDocument.Rule rule = targetType == ToolRuleDocument.TargetType.TAG
                ? toolRules.addTagRule("", 50)
                : toolRules.addItemRule("", 50);
        toolList.addRule(rule);
        setFocused(toolList);
    }

    private void ensureFallbackRule() {
        boolean found = toolRules.rules().stream()
                .anyMatch(rule -> rule.targetType() == ToolRuleDocument.TargetType.FALLBACK);
        if (!found) {
            toolRules.addFallbackRule(0);
        }
    }

    private boolean isRequiredFallback(ToolRuleDocument.Rule candidate) {
        for (ToolRuleDocument.Rule rule : toolRules.rules()) {
            if (rule.targetType() == ToolRuleDocument.TargetType.FALLBACK) {
                return rule == candidate;
            }
        }
        return false;
    }

    private void addNavigation(String key, Page target) {
        Button button = withTooltip(Button.builder(
                Component.translatable("mobamputation.config.category." + key),
                ignored -> switchPage(target)
        ).bounds(0, 0, 100, 20).build(),
                "mobamputation.config.category." + key + ".tooltip");
        settingsList.addWidget(button);
    }

    private EditBox integerField(String key, int value, boolean editable) {
        EditBox box = new EditBox(font, 0, 0, 100, 20, Component.translatable(nameKey(key)));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        box.setEditable(editable);
        if (editable) {
            withTooltip(box, tooltipKey(key));
        } else {
            serverControlledTooltip(box);
        }
        settingsList.addLabeled(Component.translatable(nameKey(key)), box, false);
        return box;
    }

    private EditBox textField(String key, String value, boolean editable) {
        EditBox box = new EditBox(font, 0, 0, 100, 20, Component.translatable(nameKey(key)));
        box.setMaxLength(32767);
        box.setValue(value);
        box.setCursorPosition(0);
        box.setHighlightPos(0);
        box.setEditable(editable);
        if (editable) {
            withTooltip(box, tooltipKey(key));
        } else {
            serverControlledTooltip(box);
        }
        settingsList.addLabeled(Component.translatable(nameKey(key)), box, true);
        return box;
    }

    private void addBoolean(String key, boolean value, boolean editable, Consumer<Boolean> change) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value).create(
                0,
                0,
                100,
                20,
                Component.translatable(nameKey(key)),
                (cycleButton, selected) -> change.accept(selected)
        );
        button.active = editable;
        if (editable) {
            withTooltip(button, tooltipKey(key));
        } else {
            serverControlledTooltip(button);
        }
        settingsList.addWidget(button);
    }

    private void openProjectileRawEditor() {
        captureInputs();
        minecraft.setScreen(new ProjectileRuleEditorScreen(
                this,
                projectileRules.serialize(),
                raw -> {
                    projectileRules = ProjectileRuleDocument.parse(raw);
                    values = withProjectileList(raw);
                }
        ));
    }

    private void openToolRawEditor() {
        captureInputs();
        minecraft.setScreen(new ToolRuleEditorScreen(
                this,
                toolRules.serialize(),
                raw -> {
                    toolRules = ToolRuleDocument.parse(raw);
                    ensureFallbackRule();
                    values = values.withToolRules(toolRules.serialize());
                }
        ));
    }

    private void switchPage(Page next) {
        if (next == page) {
            return;
        }
        if (page == Page.TOOL_RULES && !sessionControlled && toolList != null && toolList.hasInvalidRows()) {
            return;
        }
        captureInputs();
        page = next;
        rebuildWidgets();
    }

    private PlayerTrauma trauma() {
        return values.playerTrauma();
    }

    private ArmorProtection armor() {
        return values.armorProtection();
    }

    private CreeperAmputation creeperConfig() {
        return values.creeperAmputation();
    }

    private DeathDismemberment deathConfig() {
        return values.deathDismemberment();
    }

    private BloodSurfacePhysics bloodSurfacePhysics() {
        return values.bloodSurfacePhysics();
    }

    private void updatePlayerTrauma(PlayerTrauma trauma) {
        values = withExtensionGroups(trauma, values.armorProtection(), values.creeperAmputation(),
                values.deathDismemberment());
    }

    private void updateArmor(ArmorProtection armor) {
        values = withExtensionGroups(values.playerTrauma(), armor, values.creeperAmputation(),
                values.deathDismemberment());
    }

    private void updateCreeper(CreeperAmputation creeper) {
        values = withExtensionGroups(values.playerTrauma(), values.armorProtection(), creeper,
                values.deathDismemberment());
    }

    private void updateDeath(DeathDismemberment death) {
        values = withExtensionGroups(values.playerTrauma(), values.armorProtection(), values.creeperAmputation(), death);
    }

    private void captureInputs() {
        String serializedProjectileRules = projectileRules.serialize();
        String serializedToolRules = toolRules.serialize();
        PlayerTrauma trauma = values.playerTrauma();
        trauma = new PlayerTrauma(
                trauma.armAmputation(),
                trauma.fatalDecapitation(),
                headBleedMin == null ? trauma.headBleedoutMinTicks()
                        : integer(headBleedMin, trauma.headBleedoutMinTicks(), 1, 72000),
                headBleedMax == null ? trauma.headBleedoutMaxTicks()
                        : integer(headBleedMax, trauma.headBleedoutMaxTicks(), 1, 72000),
                headBleedInterval == null ? trauma.headBleedIntervalTicks()
                        : integer(headBleedInterval, trauma.headBleedIntervalTicks(), 1, 1200),
                headBleedDamage == null ? trauma.headBleedDamageTenths()
                        : integer(headBleedDamage, trauma.headBleedDamageTenths(), 0, 10000),
                trauma.armBleeding(),
                armBleedDuration == null ? trauma.armBleedDurationTicks()
                        : integer(armBleedDuration, trauma.armBleedDurationTicks(), 1, 72000),
                armBleedInterval == null ? trauma.armBleedIntervalTicks()
                        : integer(armBleedInterval, trauma.armBleedIntervalTicks(), 1, 1200),
                armBleedDamage == null ? trauma.armBleedDamageTenths()
                        : integer(armBleedDamage, trauma.armBleedDamageTenths(), 0, 10000),
                trauma.bandagesEnabled(),
                bandageCooldown == null ? trauma.bandageCooldownTicks()
                        : integer(bandageCooldown, trauma.bandageCooldownTicks(), 0, 1200)
        ).normalized();
        ArmorProtection armor = values.armorProtection();
        armor = new ArmorProtection(
                armor.enabled(),
                armorGlobalReduction == null ? armor.globalReductionPerArmorPoint()
                        : integer(armorGlobalReduction, armor.globalReductionPerArmorPoint(), 0, 100),
                armorCoveredReduction == null ? armor.coveredReductionPerArmorPoint()
                        : integer(armorCoveredReduction, armor.coveredReductionPerArmorPoint(), 0, 100),
                armorToughnessReduction == null ? armor.toughnessReductionPerPoint()
                        : integer(armorToughnessReduction, armor.toughnessReductionPerPoint(), 0, 100),
                armorProtectionCap == null ? armor.maximumReduction()
                        : integer(armorProtectionCap, armor.maximumReduction(), 0, 100),
                armor.enchantmentsEnabled(),
                reinforcementReduction == null ? armor.reinforcementReductionPerLevel()
                        : integer(reinforcementReduction, armor.reinforcementReductionPerLevel(), 0, 100),
                armor.anatomicalIntegrityEnabled()
        ).normalized();
        DeathDismemberment death = values.deathDismemberment();
        if (deathGibTime != null || deathGibGroundTime != null || deathBloodCount != null) {
            death = death.withClientSettings(
                    deathGibTime == null ? death.gibTime()
                            : integer(deathGibTime, death.gibTime(), 0, Integer.MAX_VALUE),
                    deathGibGroundTime == null ? death.gibGroundTime()
                            : integer(deathGibGroundTime, death.gibGroundTime(), 0, Integer.MAX_VALUE),
                    death.blood(),
                    deathBloodCount == null ? death.bloodCount()
                            : integer(deathBloodCount, death.bloodCount(), 1, 1000),
                    death.greenBlood(),
                    death.gibPushing()
            ).normalized();
        }
        BloodSurfacePhysics bloodPhysics = values.bloodSurfacePhysics();
        if (bloodSurfaceCheckInterval != null) {
            bloodPhysics = new BloodSurfacePhysics(
                    bloodPhysics.enabled(),
                    bloodPhysics.dripping(),
                    integer(bloodSurfaceCheckInterval, bloodPhysics.checkIntervalTicks(), 2, 40),
                    bloodPhysics.entityCollisions()
            ).normalized();
        }

        values = copySnapshot(
                gibTime == null ? values.gibTime() : integer(gibTime, values.gibTime(), 0, Integer.MAX_VALUE),
                gibGroundTime == null
                        ? values.gibGroundTime()
                        : integer(gibGroundTime, values.gibGroundTime(), 0, Integer.MAX_VALUE),
                bloodCount == null ? values.bloodCount() : integer(bloodCount, values.bloodCount(), 1, 1000),
                unlistedProjectileChance == null
                        ? values.unlistedProjectileChance()
                        : integer(unlistedProjectileChance, values.unlistedProjectileChance(), 0, 100),
                fishingChance == null
                        ? values.fishingChance()
                        : integer(fishingChance, values.fishingChance(), 0, 100),
                serializedProjectileRules,
                serializedToolRules,
                trauma,
                armor,
                death,
                bloodPhysics
        );
    }

    private void saveAndClose() {
        captureInputs();
        MobAmputationConfig.save(values);
        onClose();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        ResizeState resizeState = captureResizeState();
        // Update the working snapshot before vanilla rebuilds the widgets. No
        // file write occurs here; Done remains the only save path.
        captureInputs();
        super.resize(minecraft, width, height);
        restoreResizeState(resizeState);
    }

    private ResizeState captureResizeState() {
        Map<String, String> fields = new HashMap<>();
        putField(fields, "gibTime", gibTime);
        putField(fields, "gibGroundTime", gibGroundTime);
        putField(fields, "bloodCount", bloodCount);
        putField(fields, "bloodSurfaceCheckInterval", bloodSurfaceCheckInterval);
        putField(fields, "unlistedProjectileChance", unlistedProjectileChance);
        putField(fields, "fishingChance", fishingChance);
        putField(fields, "armBleedDuration", armBleedDuration);
        putField(fields, "armBleedInterval", armBleedInterval);
        putField(fields, "armBleedDamage", armBleedDamage);
        putField(fields, "headBleedMin", headBleedMin);
        putField(fields, "headBleedMax", headBleedMax);
        putField(fields, "headBleedInterval", headBleedInterval);
        putField(fields, "headBleedDamage", headBleedDamage);
        putField(fields, "bandageCooldown", bandageCooldown);
        putField(fields, "armorGlobalReduction", armorGlobalReduction);
        putField(fields, "armorCoveredReduction", armorCoveredReduction);
        putField(fields, "armorToughnessReduction", armorToughnessReduction);
        putField(fields, "armorProtectionCap", armorProtectionCap);
        putField(fields, "reinforcementReduction", reinforcementReduction);
        putField(fields, "deathGibTime", deathGibTime);
        putField(fields, "deathGibGroundTime", deathGibGroundTime);
        putField(fields, "deathBloodCount", deathBloodCount);

        Map<ToolRuleDocument.Rule, String> toolChanceFields = new IdentityHashMap<>();
        if (toolList != null) {
            for (ToolRuleRow row : toolList.children()) {
                toolChanceFields.put(row.rule, row.chanceBox.getValue());
            }
        }
        Map<ProjectileRuleDocument.Rule, String> projectileChanceFields = new IdentityHashMap<>();
        if (projectileList != null) {
            for (ProjectileRuleRow row : projectileList.children()) {
                projectileChanceFields.put(row.rule, row.chanceBox.getValue());
            }
        }

        double scrollAmount = settingsList != null
                ? settingsList.getScrollAmount()
                : toolList != null
                ? toolList.getScrollAmount()
                : projectileList != null ? projectileList.getScrollAmount() : 0.0D;
        return new ResizeState(fields, toolChanceFields, projectileChanceFields, scrollAmount);
    }

    private void restoreResizeState(ResizeState state) {
        restoreField(state.fields(), "gibTime", gibTime);
        restoreField(state.fields(), "gibGroundTime", gibGroundTime);
        restoreField(state.fields(), "bloodCount", bloodCount);
        restoreField(state.fields(), "bloodSurfaceCheckInterval", bloodSurfaceCheckInterval);
        restoreField(state.fields(), "unlistedProjectileChance", unlistedProjectileChance);
        restoreField(state.fields(), "fishingChance", fishingChance);
        restoreField(state.fields(), "armBleedDuration", armBleedDuration);
        restoreField(state.fields(), "armBleedInterval", armBleedInterval);
        restoreField(state.fields(), "armBleedDamage", armBleedDamage);
        restoreField(state.fields(), "headBleedMin", headBleedMin);
        restoreField(state.fields(), "headBleedMax", headBleedMax);
        restoreField(state.fields(), "headBleedInterval", headBleedInterval);
        restoreField(state.fields(), "headBleedDamage", headBleedDamage);
        restoreField(state.fields(), "bandageCooldown", bandageCooldown);
        restoreField(state.fields(), "armorGlobalReduction", armorGlobalReduction);
        restoreField(state.fields(), "armorCoveredReduction", armorCoveredReduction);
        restoreField(state.fields(), "armorToughnessReduction", armorToughnessReduction);
        restoreField(state.fields(), "armorProtectionCap", armorProtectionCap);
        restoreField(state.fields(), "reinforcementReduction", reinforcementReduction);
        restoreField(state.fields(), "deathGibTime", deathGibTime);
        restoreField(state.fields(), "deathGibGroundTime", deathGibGroundTime);
        restoreField(state.fields(), "deathBloodCount", deathBloodCount);

        if (toolList != null) {
            for (ToolRuleRow row : toolList.children()) {
                String value = state.toolChanceFields().get(row.rule);
                if (value != null) {
                    row.chanceBox.setValue(value);
                }
            }
            toolList.setScrollAmount(state.scrollAmount());
        } else if (projectileList != null) {
            for (ProjectileRuleRow row : projectileList.children()) {
                String value = state.projectileChanceFields().get(row.rule);
                if (value != null) {
                    row.chanceBox.setValue(value);
                }
            }
            projectileList.setScrollAmount(state.scrollAmount());
        } else if (settingsList != null) {
            settingsList.setScrollAmount(state.scrollAmount());
        }
    }

    private static void putField(Map<String, String> fields, String key, EditBox box) {
        if (box != null) {
            fields.put(key, box.getValue());
        }
    }

    private static void restoreField(Map<String, String> fields, String key, EditBox box) {
        if (box != null && fields.containsKey(key)) {
            box.setValue(fields.get(key));
        }
    }

    private record ResizeState(
            Map<String, String> fields,
            Map<ToolRuleDocument.Rule, String> toolChanceFields,
            Map<ProjectileRuleDocument.Rule, String> projectileChanceFields,
            double scrollAmount
    ) {
    }

    private static int integer(EditBox box, int fallback, int minimum, int maximum) {
        try {
            return Mth.clamp(Integer.parseInt(box.getValue()), minimum, maximum);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String nameKey(String key) {
        return "mobamputation.config.prop." + key + ".name";
    }

    private static String tooltipKey(String key) {
        return "mobamputation.config.prop." + key + ".tooltip";
    }

    private static <T extends AbstractWidget> T withTooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(TOOLTIP_DELAY);
        return widget;
    }

    private static void serverControlledTooltip(AbstractWidget widget) {
        widget.setTooltip(Tooltip.create(Component.translatable("mobamputation.config.server_controlled.tooltip")));
        widget.setTooltipDelay(TOOLTIP_DELAY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean canLeavePage = page != Page.TOOL_RULES
                    || sessionControlled
                    || toolList == null
                    || !toolList.hasInvalidRows();
        if (backButton != null) {
            backButton.active = canLeavePage;
        }
        if (doneButton != null) {
            doneButton.active = canLeavePage;
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.translatable(page.titleKey()), width / 2, 12, 0xFFFFFFFF);

        if (projectileList != null) {
            graphics.drawString(
                    font,
                    Component.translatable("mobamputation.config.projectiles.title"),
                    projectileList.getX(),
                    projectileList.getY() - 11,
                    0xFFA0A0A0
            );
            if (projectileList.children().isEmpty()) {
                graphics.drawCenteredString(
                        font,
                        Component.translatable("mobamputation.config.projectiles.empty"),
                        width / 2,
                        projectileList.getY() + 9,
                        0xFFAAAAAA
                );
            }
        }
        if (toolList != null) {
            if (toolList.hasInvalidRows()) {
                Component error = Component.translatable("mobamputation.config.tool_rules.invalid");
                graphics.drawString(
                        font,
                        error,
                        toolList.getRowLeft(),
                        toolList.getY() - 11,
                        0xFFFF5555
                );
            } else {
                graphics.drawString(
                        font,
                        Component.translatable("mobamputation.config.tool_rules.selector"),
                        toolList.getRowLeft(),
                        toolList.getY() - 11,
                        0xFFA0A0A0
                );
                graphics.drawString(
                        font,
                        Component.translatable("mobamputation.config.tool_rules.chance"),
                        toolList.chanceColumnX(),
                        toolList.getY() - 11,
                        0xFFA0A0A0
                );
            }
        }
        if (!sessionNoticeLines.isEmpty()) {
            int y = sessionNoticeY;
            for (FormattedCharSequence line : sessionNoticeLines) {
                graphics.drawCenteredString(font, line, width / 2, y, 0xFFFFC966);
                y += 10;
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /* Snapshot-copy methods are kept together so each page only changes the values it owns. */
    private MobAmputationConfig.Snapshot withBooleans(
            boolean blood,
            boolean bloodSplurt,
            boolean greenBlood,
            boolean playerGibs,
            boolean gibPushing,
            boolean headlessDeath,
            boolean allowProjectileGibbing
    ) {
        return new MobAmputationConfig.Snapshot(
                values.gibTime(), values.gibGroundTime(), blood, values.bloodCount(), bloodSplurt, greenBlood,
                playerGibs, gibPushing, headlessDeath, values.unlistedProjectileChance(), values.fishingChance(),
                values.projectileList(), allowProjectileGibbing, values.toolRules(), values.enchantmentsEnabled(),
                values.decapitationCamera(), values.playerTrauma(), values.armorProtection(), values.creeperAmputation(),
                values.deathDismemberment(), values.bloodSurfacePhysics()
        );
    }

    private MobAmputationConfig.Snapshot withExtensions(boolean enchantmentsEnabled, boolean decapitationCamera) {
        return new MobAmputationConfig.Snapshot(
                values.gibTime(), values.gibGroundTime(), values.blood(), values.bloodCount(), values.bloodSplurt(),
                values.greenBlood(), values.playerGibs(), values.gibPushing(), values.headlessDeath(),
                values.unlistedProjectileChance(), values.fishingChance(), values.projectileList(),
                values.allowProjectileGibbing(), values.toolRules(), enchantmentsEnabled, decapitationCamera,
                values.playerTrauma(), values.armorProtection(), values.creeperAmputation(), values.deathDismemberment(),
                values.bloodSurfacePhysics()
        );
    }

    private MobAmputationConfig.Snapshot withProjectileList(String projectileList) {
        return new MobAmputationConfig.Snapshot(
                values.gibTime(), values.gibGroundTime(), values.blood(), values.bloodCount(), values.bloodSplurt(),
                values.greenBlood(), values.playerGibs(), values.gibPushing(), values.headlessDeath(),
                values.unlistedProjectileChance(), values.fishingChance(), projectileList,
                values.allowProjectileGibbing(), values.toolRules(), values.enchantmentsEnabled(),
                values.decapitationCamera(), values.playerTrauma(),
                values.armorProtection(), values.creeperAmputation(), values.deathDismemberment(),
                values.bloodSurfacePhysics()
        );
    }

    private void updateBloodSurfacePhysics(BloodSurfacePhysics physics) {
        values = new MobAmputationConfig.Snapshot(
                values.gibTime(), values.gibGroundTime(), values.blood(), values.bloodCount(), values.bloodSplurt(),
                values.greenBlood(), values.playerGibs(), values.gibPushing(), values.headlessDeath(),
                values.unlistedProjectileChance(), values.fishingChance(), values.projectileList(),
                values.allowProjectileGibbing(), values.toolRules(), values.enchantmentsEnabled(),
                values.decapitationCamera(),
                values.playerTrauma(), values.armorProtection(), values.creeperAmputation(), values.deathDismemberment(),
                physics.normalized()
        );
    }

    private MobAmputationConfig.Snapshot withExtensionGroups(
            PlayerTrauma trauma,
            ArmorProtection armor,
            CreeperAmputation creeper,
            DeathDismemberment death
    ) {
        return new MobAmputationConfig.Snapshot(
                values.gibTime(), values.gibGroundTime(), values.blood(), values.bloodCount(), values.bloodSplurt(),
                values.greenBlood(), values.playerGibs(), values.gibPushing(), values.headlessDeath(),
                values.unlistedProjectileChance(), values.fishingChance(), values.projectileList(),
                values.allowProjectileGibbing(), values.toolRules(), values.enchantmentsEnabled(),
                values.decapitationCamera(),
                trauma, armor, creeper, death, values.bloodSurfacePhysics()
        );
    }

    private MobAmputationConfig.Snapshot copySnapshot(
            int gibTime,
            int gibGroundTime,
            int bloodCount,
            int unlistedProjectileChance,
            int fishingChance,
            String projectileList,
            String toolRules,
            PlayerTrauma trauma,
            ArmorProtection armor,
            DeathDismemberment death,
            BloodSurfacePhysics bloodPhysics
    ) {
        return new MobAmputationConfig.Snapshot(
                gibTime, gibGroundTime, values.blood(), bloodCount, values.bloodSplurt(), values.greenBlood(),
                values.playerGibs(), values.gibPushing(), values.headlessDeath(), unlistedProjectileChance,
                fishingChance, projectileList, values.allowProjectileGibbing(), toolRules, values.enchantmentsEnabled(),
                values.decapitationCamera(), trauma, armor, values.creeperAmputation(), death, bloodPhysics
        );
    }

    private final class SettingsList extends ContainerObjectSelectionList<SettingRow> {
        private SettingsList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            centerListVertically = false;
        }

        void addWidget(AbstractWidget widget) {
            addEntry(new SettingRow(null, widget, false));
        }

        void addLabeled(Component label, AbstractWidget widget, boolean wideWidget) {
            addEntry(new SettingRow(label, widget, wideWidget));
        }

        @Override
        public int getRowWidth() {
            return Math.max(150, getWidth() - 12);
        }
    }

    private final class SettingRow extends ContainerObjectSelectionList.Entry<SettingRow> {
        private final Component label;
        private final AbstractWidget widget;
        private final boolean wideWidget;
        private final List<GuiEventListener> children;
        private final List<NarratableEntry> narratables;

        private SettingRow(Component label, AbstractWidget widget, boolean wideWidget) {
            this.label = label;
            this.widget = widget;
            this.wideWidget = wideWidget;
            children = List.of(widget);
            narratables = List.of(widget);
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int rowWidth,
                int rowHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick
        ) {
            int x = left + 2;
            int y = top + 3;
            int innerWidth = rowWidth - 4;
            if (label == null) {
                widget.setRectangle(innerWidth, 20, x, y);
            } else {
                int widgetWidth = wideWidget
                        ? Mth.clamp((int) (innerWidth * 0.62F), 100, Math.max(100, innerWidth - 52))
                        : Mth.clamp(innerWidth / 3, 76, 130);
                int labelWidth = Math.max(30, innerWidth - widgetWidth - 8);
                String visibleLabel = font.plainSubstrByWidth(label.getString(), labelWidth);
                graphics.drawString(font, visibleLabel, x + 2, y + 6, 0xFFE0E0E0);
                widget.setRectangle(widgetWidth, 20, x + innerWidth - widgetWidth, y);
            }
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return narratables;
        }
    }

    private final class ProjectileRuleList extends ContainerObjectSelectionList<ProjectileRuleRow> {
        private boolean editable = true;

        private ProjectileRuleList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            centerListVertically = false;
        }

        void setEditable(boolean editable) {
            this.editable = editable;
        }

        void load(List<ProjectileRuleDocument.Rule> rules) {
            clearEntries();
            rules.forEach(rule -> addEntry(new ProjectileRuleRow(rule)));
        }

        void addRule(ProjectileRuleDocument.Rule rule) {
            ProjectileRuleRow row = new ProjectileRuleRow(rule);
            addEntry(row);
            setScrollAmount(getMaxScroll());
            setFocused(row);
            row.focusTarget();
        }

        void removeRule(ProjectileRuleRow row) {
            projectileRules.removeRule(row.rule);
            removeEntry(row);
        }

        @Override
        public int getRowWidth() {
            return Math.max(244, Math.min(444, getWidth() - 12));
        }
    }

    private final class ProjectileRuleRow extends ContainerObjectSelectionList.Entry<ProjectileRuleRow> {
        private final ProjectileRuleDocument.Rule rule;
        private final CycleButton<ProjectileRuleDocument.TargetType> typeButton;
        private final EditBox targetBox;
        private final CycleButton<ProjectileRuleDocument.ChanceMode> modeButton;
        private final EditBox chanceBox;
        private final Button removeButton;
        private final List<GuiEventListener> children;
        private final List<NarratableEntry> narratables;

        private ProjectileRuleRow(ProjectileRuleDocument.Rule rule) {
            this.rule = rule;
            targetBox = new EditBox(
                    font,
                    0,
                    0,
                    100,
                    20,
                    Component.translatable("mobamputation.config.projectiles.target")
            );
            targetBox.setMaxLength(32767);
            targetBox.setValue(rule.target());
            targetBox.setCursorPosition(0);
            targetBox.setHighlightPos(0);
            targetBox.setResponder(rule::setTarget);
            typeButton = CycleButton
                    .<ProjectileRuleDocument.TargetType>builder(value -> Component.translatable(value.translationKey()))
                    .withValues(ProjectileRuleDocument.TargetType.values())
                    .withInitialValue(rule.targetType())
                    .displayOnlyValue()
                    .create(Component.translatable("mobamputation.config.projectiles.type"), (button, value) -> {
                        rule.setTargetType(value);
                        targetBox.setValue(rule.target());
                        targetBox.setCursorPosition(0);
                        targetBox.setHighlightPos(0);
                        syncImportedChance();
                        updateState();
                    });
            modeButton = CycleButton
                    .<ProjectileRuleDocument.ChanceMode>builder(value -> Component.translatable(value.translationKey()))
                    .withValues(ProjectileRuleDocument.ChanceMode.values())
                    .withInitialValue(rule.chanceMode())
                    .displayOnlyValue()
                    .create(Component.translatable("mobamputation.config.projectiles.mode"), (button, value) -> {
                        rule.setChanceMode(value);
                        updateState();
                    });
            chanceBox = new EditBox(
                    font,
                    0,
                    0,
                    34,
                    20,
                    Component.translatable("mobamputation.config.projectiles.chance")
            );
            chanceBox.setMaxLength(3);
            chanceBox.setValue(Integer.toString(rule.customChance()));
            chanceBox.setFilter(MobAmputationConfigScreen::validChanceText);
            chanceBox.setResponder(value -> {
                try {
                    rule.setCustomChance(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            });
            removeButton = Button.builder(
                    Component.translatable("mobamputation.config.rule.remove"),
                    button -> projectileList.removeRule(this)
            ).build();

            withTooltip(typeButton, "mobamputation.config.projectiles.type.tooltip");
            withTooltip(targetBox, "mobamputation.config.projectiles.target.tooltip");
            withTooltip(modeButton, "mobamputation.config.projectiles.mode.tooltip");
            withTooltip(chanceBox, "mobamputation.config.projectiles.chance.tooltip");
            withTooltip(removeButton, "mobamputation.config.projectiles.remove.tooltip");
            children = List.of(typeButton, targetBox, modeButton, chanceBox, removeButton);
            narratables = List.of(typeButton, targetBox, modeButton, chanceBox, removeButton);
            updateState();
        }

        private void syncImportedChance() {
            modeButton.setValue(rule.chanceMode());
            chanceBox.setValue(Integer.toString(rule.customChance()));
        }

        private void focusTarget() {
            setFocused(targetBox);
        }

        private void updateState() {
            boolean editable = projectileList == null || projectileList.editable;
            chanceBox.visible = rule.chanceMode() == ProjectileRuleDocument.ChanceMode.CUSTOM
                    && rule.targetType() != ProjectileRuleDocument.TargetType.RAW;
            chanceBox.setEditable(editable && chanceBox.visible);
            modeButton.visible = rule.targetType() != ProjectileRuleDocument.TargetType.RAW;
            modeButton.active = editable;
            typeButton.active = editable;
            targetBox.setEditable(editable);
            removeButton.active = editable;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int rowWidth,
                int rowHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick
        ) {
            updateState();
            int x = left + 2;
            int y = top + 3;
            int innerWidth = rowWidth - 4;
            int typeWidth = Mth.clamp(innerWidth / 4, 58, 90);
            int modeWidth = modeButton.visible ? Mth.clamp(innerWidth / 4, 62, 92) : 0;
            int chanceWidth = chanceBox.visible ? 38 : 0;
            int removeWidth = 20;
            int gap = 3;
            int componentCount = 3 + (modeButton.visible ? 1 : 0) + (chanceBox.visible ? 1 : 0);
            int targetWidth = Math.max(
                    40,
                    innerWidth - typeWidth - modeWidth - chanceWidth - removeWidth - gap * (componentCount - 1)
            );

            typeButton.setRectangle(typeWidth, 20, x, y);
            x += typeWidth + gap;
            targetBox.setRectangle(targetWidth, 20, x, y);
            x += targetWidth + gap;
            if (modeButton.visible) {
                modeButton.setRectangle(modeWidth, 20, x, y);
                x += modeWidth + gap;
            }
            if (chanceBox.visible) {
                chanceBox.setRectangle(chanceWidth, 20, x, y);
                x += chanceWidth + gap;
            }
            removeButton.setRectangle(removeWidth, 20, x, y);

            typeButton.render(graphics, mouseX, mouseY, partialTick);
            targetBox.render(graphics, mouseX, mouseY, partialTick);
            if (modeButton.visible) {
                modeButton.render(graphics, mouseX, mouseY, partialTick);
            }
            if (chanceBox.visible) {
                chanceBox.render(graphics, mouseX, mouseY, partialTick);
            }
            removeButton.render(graphics, mouseX, mouseY, partialTick);

            if (!rule.targetLooksValid() && !targetBox.getValue().isEmpty()) {
                graphics.fill(
                        targetBox.getX(),
                        targetBox.getBottom() - 1,
                        targetBox.getRight(),
                        targetBox.getBottom(),
                        0xFFFF5555
                );
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return narratables;
        }
    }

    private final class ToolRuleList extends ContainerObjectSelectionList<ToolRuleRow> {
        private boolean editable = true;

        private ToolRuleList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            centerListVertically = false;
        }

        void setEditable(boolean editable) {
            this.editable = editable;
        }

        void load(List<ToolRuleDocument.Rule> rules) {
            clearEntries();
            rules.forEach(rule -> addEntry(new ToolRuleRow(rule)));
        }

        void addRule(ToolRuleDocument.Rule rule) {
            load(toolRules.rules());
            for (ToolRuleRow row : children()) {
                if (row.rule == rule) {
                    int index = toolRules.rules().indexOf(rule);
                    setScrollAmount(Math.max(0, index * 28 - getHeight() / 2.0D));
                    setFocused(row);
                    row.focusSelector();
                    break;
                }
            }
        }

        void removeRule(ToolRuleRow row) {
            if (isRequiredFallback(row.rule)) {
                return;
            }
            toolRules.removeRule(row.rule);
            removeEntry(row);
        }

        void moveRule(ToolRuleRow row, int direction) {
            List<ToolRuleDocument.Rule> rules = toolRules.rules();
            int current = rules.indexOf(row.rule);
            int destination = current + direction;
            if (current < 0 || destination < 0 || destination >= rules.size()) {
                return;
            }
            if (isRequiredFallback(row.rule)) {
                return;
            }
            toolRules.moveRule(row.rule, destination);
            load(toolRules.rules());
            setScrollAmount(Math.max(0, destination * 28 - getHeight() / 2.0D));
        }

        boolean hasInvalidRows() {
            return children().stream().anyMatch(ToolRuleRow::hasInvalidInput);
        }

        @Override
        public int getRowWidth() {
            return Math.max(244, Math.min(444, getWidth() - 12));
        }

        int chanceColumnX() {
            return getRowRight() - 42 - 3 - 20 - 3 - 20 - 3 - 20;
        }
    }

    private final class ToolRuleRow extends ContainerObjectSelectionList.Entry<ToolRuleRow> {
        private final ToolRuleDocument.Rule rule;
        private final EditBox selectorBox;
        private final EditBox chanceBox;
        private final Button moveUpButton;
        private final Button moveDownButton;
        private final Button removeButton;
        private final List<GuiEventListener> children;
        private final List<NarratableEntry> narratables;

        private ToolRuleRow(ToolRuleDocument.Rule rule) {
            this.rule = rule;
            boolean fallback = rule.targetType() == ToolRuleDocument.TargetType.FALLBACK;
            boolean requiredFallback = isRequiredFallback(rule);
            selectorBox = new EditBox(
                    font,
                    0,
                    0,
                    100,
                    20,
                    Component.translatable("mobamputation.config.tool_rules.selector")
            );
            selectorBox.setMaxLength(256);
            selectorBox.setValue(fallback
                    ? Component.translatable("mobamputation.config.tool_rules.any_other").getString()
                    : rule.selector());
            selectorBox.setCursorPosition(0);
            selectorBox.setHighlightPos(0);
            if (!fallback) {
                selectorBox.setResponder(rule::setSelector);
            }

            chanceBox = new EditBox(
                    font,
                    0,
                    0,
                    42,
                    20,
                    Component.translatable("mobamputation.config.tool_rules.chance")
            );
            chanceBox.setMaxLength(3);
            chanceBox.setValue(rule.targetType() == ToolRuleDocument.TargetType.RAW
                    || rule.validationError() == ToolRuleDocument.ValidationError.INVALID_CHANCE
                    ? ""
                    : Integer.toString(rule.chance()));
            chanceBox.setFilter(MobAmputationConfigScreen::validChanceText);
            chanceBox.setResponder(value -> {
                try {
                    rule.setChance(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            });

            moveUpButton = Button.builder(
                    Component.translatable("mobamputation.config.rule.move_up"),
                    button -> toolList.moveRule(this, -1)
            ).build();
            moveDownButton = Button.builder(
                    Component.translatable("mobamputation.config.rule.move_down"),
                    button -> toolList.moveRule(this, 1)
            ).build();
            removeButton = Button.builder(
                    Component.translatable("mobamputation.config.rule.remove"),
                    button -> toolList.removeRule(this)
            ).build();

            withTooltip(moveUpButton, "mobamputation.config.rule.move_up.tooltip");
            withTooltip(moveDownButton, "mobamputation.config.rule.move_down.tooltip");
            withTooltip(removeButton, requiredFallback
                    ? "mobamputation.config.tool_rules.fallback.remove.tooltip"
                    : "mobamputation.config.rule.remove.tooltip");
            children = List.of(selectorBox, chanceBox, moveUpButton, moveDownButton, removeButton);
            narratables = List.of(selectorBox, chanceBox, moveUpButton, moveDownButton, removeButton);
            updateState();
        }

        private void focusSelector() {
            if (rule.targetType() != ToolRuleDocument.TargetType.FALLBACK) {
                setFocused(selectorBox);
                int cursor = rule.targetType() == ToolRuleDocument.TargetType.TAG ? 1 : 0;
                selectorBox.setCursorPosition(cursor);
                selectorBox.setHighlightPos(cursor);
            } else {
                setFocused(chanceBox);
            }
        }

        private void updateState() {
            boolean editable = toolList == null || toolList.editable;
            boolean fallback = rule.targetType() == ToolRuleDocument.TargetType.FALLBACK;
            boolean requiredFallback = isRequiredFallback(rule);
            boolean raw = rule.targetType() == ToolRuleDocument.TargetType.RAW;
            selectorBox.setEditable(editable && !fallback);
            chanceBox.visible = !raw;
            chanceBox.setEditable(editable && !raw);
            removeButton.active = editable && !requiredFallback;

            List<ToolRuleDocument.Rule> rules = toolRules.rules();
            int index = rules.indexOf(rule);
            moveUpButton.active = editable
                    && !requiredFallback
                    && index > 0;
            moveDownButton.active = editable
                    && !requiredFallback
                    && index >= 0
                    && index < rules.size() - 1;

            String selectorTooltip = switch (rule.targetType()) {
                case ITEM -> "mobamputation.config.tool_rules.selector.item.tooltip";
                case TAG -> "mobamputation.config.tool_rules.selector.tag.tooltip";
                case FALLBACK -> "mobamputation.config.tool_rules.fallback.tooltip";
                case RAW -> "mobamputation.config.tool_rules.selector.raw.tooltip";
            };
            if (!rule.isValid()
                    && rule.validationError() != ToolRuleDocument.ValidationError.INVALID_CHANCE) {
                selectorTooltip = "mobamputation.config.tool_rules.validation."
                        + rule.validationError().name().toLowerCase(Locale.ROOT);
            }
            withTooltip(selectorBox, selectorTooltip);
            withTooltip(chanceBox, chanceBox.getValue().isEmpty()
                    || rule.validationError() == ToolRuleDocument.ValidationError.INVALID_CHANCE
                    ? "mobamputation.config.tool_rules.validation.invalid_chance"
                    : "mobamputation.config.tool_rules.chance.tooltip");
        }

        private boolean hasInvalidInput() {
            return !rule.isValid() || (chanceBox.visible && chanceBox.getValue().isEmpty());
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int rowWidth,
                int rowHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick
        ) {
            updateState();
            int x = left + 2;
            int y = top + 3;
            int innerWidth = rowWidth - 4;
            int gap = 3;
            int chanceWidth = chanceBox.visible ? 42 : 0;
            int smallButtonWidth = 20;
            int componentCount = chanceBox.visible ? 5 : 4;
            int selectorWidth = Math.max(
                    60,
                    innerWidth - chanceWidth - smallButtonWidth * 3 - gap * (componentCount - 1)
            );

            selectorBox.setRectangle(selectorWidth, 20, x, y);
            x += selectorWidth + gap;
            if (chanceBox.visible) {
                chanceBox.setRectangle(chanceWidth, 20, x, y);
                x += chanceWidth + gap;
            }
            moveUpButton.setRectangle(smallButtonWidth, 20, x, y);
            x += smallButtonWidth + gap;
            moveDownButton.setRectangle(smallButtonWidth, 20, x, y);
            x += smallButtonWidth + gap;
            removeButton.setRectangle(smallButtonWidth, 20, x, y);

            selectorBox.render(graphics, mouseX, mouseY, partialTick);
            if (chanceBox.visible) {
                chanceBox.render(graphics, mouseX, mouseY, partialTick);
            }
            moveUpButton.render(graphics, mouseX, mouseY, partialTick);
            moveDownButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);

            boolean invalidChance = chanceBox.visible
                    && (chanceBox.getValue().isEmpty()
                    || rule.validationError() == ToolRuleDocument.ValidationError.INVALID_CHANCE);
            if (!rule.isValid() || invalidChance) {
                AbstractWidget invalidWidget = invalidChance ? chanceBox : selectorBox;
                graphics.fill(
                        invalidWidget.getX(),
                        invalidWidget.getBottom() - 1,
                        invalidWidget.getRight(),
                        invalidWidget.getBottom(),
                        0xFFFF5555
                );
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return narratables;
        }
    }

    private static boolean validChanceText(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        try {
            return Integer.parseInt(value) <= 100;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
