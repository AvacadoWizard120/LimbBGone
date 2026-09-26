package io.github.avacadowizard120.mobamputation.logic;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** In-memory countdown maps matching the original server event handler. */
public final class HeadlessDeathManager {
    private static final Map<LivingEntity, Integer> remainingTicks = new IdentityHashMap<>();
    private static final Map<LivingEntity, Player> attackers = new IdentityHashMap<>();

    public static void schedule(LivingEntity target, Player attacker) {
        remainingTicks.put(target, 40 + target.getRandom().nextInt(60));
        attackers.put(target, attacker);
    }

    public static void tickServer() {
        Iterator<Map.Entry<LivingEntity, Integer>> iterator = remainingTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
                continue;
            }

            LivingEntity target = entry.getKey();
            Player attacker = attackers.remove(target);
            target.hurt(
                    attacker == null
                            ? target.level().damageSources().generic()
                            : target.level().damageSources().playerAttack(attacker),
                    200.0F
            );
            iterator.remove();
        }
    }

    public static void clear() {
        remainingTicks.clear();
        attackers.clear();
    }

    private HeadlessDeathManager() {
    }
}
