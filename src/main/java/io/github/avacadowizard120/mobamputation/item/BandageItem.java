package io.github.avacadowizard120.mobamputation.item;

import io.github.avacadowizard120.mobamputation.logic.PlayerTraumaManager;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Consumable field dressing that immediately closes every active arm wound. */
public final class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return MobAmputationConfig.get().playerTrauma().bandagesEnabled()
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.pass(stack);
        }
        if (!PlayerTraumaManager.canUseBandage(player)) {
            return InteractionResultHolder.pass(stack);
        }
        PlayerTraumaManager.bandage(player);
        if (!player.getAbilities().instabuild) {
            stack.consume(1, player);
        }
        player.getCooldowns().addCooldown(
                this, MobAmputationConfig.get().playerTrauma().bandageCooldownTicks()
        );
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }
}
