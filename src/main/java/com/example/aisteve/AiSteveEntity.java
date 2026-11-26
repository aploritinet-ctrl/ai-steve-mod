package com.example.aisteve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.List;

public class AiSteveEntity extends PathfinderMob {
    public final SimpleContainer inv = new SimpleContainer(36);
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private Step cur = null;
    private BlockPos mineTarget = null;

    public AiSteveEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("AI Steve"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    public void setPlan(List<Step> steps) {
        queue.clear(); cur = null; mineTarget = null;
        getNavigation().stop();
        queue.addAll(steps);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Позиция Стива: %.0f %.0f %.0f, HP %.0f. ", getX(), getY(), getZ(), getHealth()));
        sb.append("Текущий шаг: ").append(cur == null ? "нет" : cur.type).append(". В очереди: ").append(queue.size()).append(". Инвентарь: ");
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) { sb.append(s.getCount()).append("x").append(s.getItem().toString()).append(" "); any = true; }
        }
        if (!any) sb.append("пусто");
        return sb.toString();
    }

    private void say(ServerLevel sl, String t) {
        for (Player p : sl.players()) p.sendSystemMessage(Component.literal("<AI Steve> " + t));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        Player p = sl.getNearestPlayer(this, 256);
        if (cur == null) {
            cur = queue.poll();
            if (cur == null) return;
            cur.ticks = 0;
        }
        cur.ticks++;
        boolean finished = false;
        switch (cur.type) {
            case "goto" -> {
                getNavigation().moveTo(cur.x, cur.y, cur.z, 1.1);
                if (distanceToSqr(cur.x, cur.y, cur.z) < 4 || cur.ticks > 1200) finished = true;
            }
            case "come" -> {
                if (p == null) { finished = true; break; }
                getNavigation().moveTo(p, 1.2);
                if (distanceTo(p) < 3 || cur.ticks > 1200) finished = true;
            }
            case "follow" -> { if (p != null && distanceTo(p) > 3) getNavigation().moveTo(p, 1.1); } // до новой команды
            case "stay" -> { getNavigation().stop(); finished = true; }
            case "wait" -> { if (cur.ticks >= cur.sec * 20) finished = true; }
            case "say" -> { say(sl, cur.text); finished = true; }
            case "attack" -> {
                LivingEntity t = sl.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16))
                        .stream().findFirst().orElse(null);
                if (t == null || cur.ticks > 2400) { finished = true; break; }
                getNavigation().moveTo(t, 1.2);
                if (distanceTo(t) < 2.2 && cur.ticks % 12 == 0) doHurtTarget(sl, t);
            }
            case "mine" -> finished = tickMine(sl);
            case "give" -> {
                if (p == null) { finished = true; break; }
                getNavigation().moveTo(p, 1.2);
                if (distanceTo(p) < 3) {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack s = inv.removeItemNoUpdate(i);
                        if (!s.isEmpty()) spawnAtLocation(sl, s);
                    }
                    finished = true;
                } else if (cur.ticks > 1200) finished = true;
            }
            default -> finished = true;
        }
        if (finished) { cur = null; mineTarget = null; }
    }

    private boolean tickMine(ServerLevel sl) {
        Block want = BuiltInRegistries.BLOCK.getValue(Identifier.parse(cur.block.contains(":") ? cur.block : "minecraft:" + cur.block));
        if (cur.done >= cur.count) return true;
        if (cur.ticks > 6000) { say(sl, "Долго копаю, останавливаюсь."); return true; }
        if (mineTarget != null && !sl.getBlockState(mineTarget).is(want)) mineTarget = null;
        if (mineTarget == null && cur.ticks % 10 == 1) {
            double best = Double.MAX_VALUE;
            BlockPos o = blockPosition();
            for (BlockPos b : BlockPos.betweenClosed(o.offset(-16, -8, -16), o.offset(16, 8, 16))) {
                if (sl.getBlockState(b).is(want)) {
                    double d = b.distSqr(o);
                    if (d < best) { best = d; mineTarget = b.immutable(); }
                }
            }
            if (mineTarget == null && cur.ticks > 30) { say(sl, "Не нашёл " + cur.block + " рядом."); return true; }
        }
        if (mineTarget != null) {
            getNavigation().moveTo(mineTarget.getX() + 0.5, mineTarget.getY(), mineTarget.getZ() + 0.5, 1.1);
            if (distanceToSqr(mineTarget.getX() + 0.5, mineTarget.getY() + 0.5, mineTarget.getZ() + 0.5) < 12) {
                BlockState st = sl.getBlockState(mineTarget);
                for (ItemStack drop : Block.getDrops(st, sl, mineTarget, sl.getBlockEntity(mineTarget), this, ItemStack.EMPTY)) {
                    ItemStack left = inv.addItem(drop);
                    if (!left.isEmpty()) spawnAtLocation(sl, left);
                }
                sl.destroyBlock(mineTarget, false);
                mineTarget = null;
                cur.done++;
            }
        }
        return false;
    }
}
