package com.shadowhound;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.joml.Vector3f;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ShadowHoundMod.MODID)
public class HoundEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        Player p = e.player;
        if (p.tickCount % 20 != 0 || p.isSpectator()) return;
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;

        int stage = HoundEntity.stageFor(level, p);
        RandomSource r = level.getRandom();
        boolean night = level.isNight();

        // Vigia da cama: o cachorro aparece ao lado da cama enquanto voce dorme
        if (stage >= 1 && p.isSleeping() && p.getSleepTimer() > 20) {
            trySpawnBedside(level, p, stage);
        }

        // O sono "cancela" no meio da noite
        if (stage >= 2 && p.isSleeping() && p.getSleepTimer() > 60 && r.nextInt(4) == 0
                && p instanceof ServerPlayer sp) {
            sp.stopSleepInBed(true, true);
            p.displayClientMessage(Component.translatable("message.shadowhound.woken"), true);
        }

        // Cachorro sentado olhando o nascer do sol
        long tod = level.getDayTime() % 24000L;
        if (stage >= 1 && stage < 3 && (tod < 1200L || tod > 23000L) && level.canSeeSky(p.blockPosition())
                && r.nextInt(12) == 0
                && level.getEntitiesOfClass(HoundEntity.class, p.getBoundingBox().inflate(80)).isEmpty()) {
            spawnSunriseWatcher(level, p, stage);
            return;
        }

        // Rosnados distantes
        if (stage >= 1 && night && r.nextInt(90) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            level.playSound(null, p.getX() + Math.cos(a) * 30, p.getY(), p.getZ() + Math.sin(a) * 30,
                    SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 2.0F, 0.5F);
        }

        // Aparicao
        if (stage >= 1 && !night) return;
        if (!level.canSeeSky(p.blockPosition())) return;
        int chance = switch (stage) {
            case 0 -> 300;
            case 1 -> 150;
            case 2 -> 100;
            default -> 70;
        };
        if (r.nextInt(chance) != 0) return;
        if (!level.getEntitiesOfClass(HoundEntity.class, p.getBoundingBox().inflate(80)).isEmpty()) return;

        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 22 + r.nextInt(8);
        if (stage >= 2) { // aparece atras de voce
            double yaw = Math.toRadians(p.getYRot());
            ang = Math.atan2(-Math.cos(yaw), Math.sin(yaw)) + (r.nextDouble() - 0.5D);
            dist = 14 + r.nextInt(6);
        }
        int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
        BlockPos top = new BlockPos(x, 0, z);
        if (!level.hasChunkAt(top)) return;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top);
        if (Math.abs(pos.getY() - p.getY()) > 12) return;
        if (!level.getFluidState(pos.below()).isEmpty()) return;

        HoundEntity h = ModEntities.HOUND.get().create(level);
        if (h == null) return;
        h.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
        h.setStage(stage);
        level.addFreshEntity(h);
    }

    private static void spawnSunriseWatcher(ServerLevel level, Player p, int stage) {
        RandomSource r = level.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 20 + r.nextInt(6);
        int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
        BlockPos top = new BlockPos(x, 0, z);
        if (!level.hasChunkAt(top)) return;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top);
        if (Math.abs(pos.getY() - p.getY()) > 12 || !level.getFluidState(pos.below()).isEmpty()) return;
        HoundEntity h = ModEntities.HOUND.get().create(level);
        if (h == null) return;
        h.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        h.setStage(stage);
        h.setSunrise(true);
        level.addFreshEntity(h);
    }

    private static void trySpawnBedside(ServerLevel level, Player p, int stage) {
        if (!level.getEntitiesOfClass(HoundEntity.class, p.getBoundingBox().inflate(40)).isEmpty()) return;
        if (level.getRandom().nextInt(3) != 0) return;
        BlockPos bed = p.blockPosition();
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Direction d : dirs) {
            BlockPos pos = bed.relative(d, 2);
            HoundEntity h = ModEntities.HOUND.get().create(level);
            if (h == null) return;
            h.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            if (level.noCollision(h) && !level.getBlockState(pos.below()).isAir()) {
                h.setStage(stage);
                h.setBedside(true);
                level.addFreshEntity(h);
                return;
            }
        }
    }

    /** Ao acordar, um dos seus lobos domesticados (filhotes) pode ter sumido durante a noite. */
    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent e) {
        Player p = e.getEntity();
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        int stage = HoundEntity.stageFor(level, p);
        if (stage < 2 || level.getRandom().nextFloat() > 0.7F) return;
        takePup(level, p);
    }

    private static void takePup(ServerLevel level, Player p) {
        List<Wolf> pups = level.getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(64),
                w -> !(w instanceof HoundEntity) && w.isTame() && w.isOwnedBy(p));
        if (pups.isEmpty()) return;
        Wolf w = pups.get(level.getRandom().nextInt(pups.size()));

        level.sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.0F), 1.0F),
                w.getX(), w.getY() + 0.5D, w.getZ(), 30, 0.3D, 0.3D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), w.getY() + 0.5D, w.getZ(), 15, 0.3D, 0.3D, 0.3D, 0.02D);
        level.playSound(null, w.blockPosition(), SoundEvents.WOLF_WHINE, SoundSource.NEUTRAL, 1.0F, 0.7F);
        Component name = w.getDisplayName();
        w.discard();

        CompoundTag data = p.getPersistentData();
        int n = data.getInt(HoundEntity.TAKEN_KEY) + 1;
        data.putInt(HoundEntity.TAKEN_KEY, n);
        p.displayClientMessage(Component.translatable("message.shadowhound.taken", name, n), false);
        if (n == 6) {
            p.displayClientMessage(Component.translatable("message.shadowhound.final"), false);
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            BlockPos top = new BlockPos(Mth.floor(p.getX() + Math.cos(a) * 22), 0, Mth.floor(p.getZ() + Math.sin(a) * 22));
            if (level.hasChunkAt(top)) {
                RitualSite.build(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top));
                p.displayClientMessage(Component.translatable("message.shadowhound.ritual"), false);
            }
        }
    }

    /** Lobos domesticados fogem do Cao Sombrio. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof Wolf wolf && !(wolf instanceof HoundEntity)) {
            wolf.goalSelector.addGoal(1, new AvoidEntityGoal<>(wolf, HoundEntity.class, 16.0F, 1.2D, 1.6D));
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("shadowhound")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("stage")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 3))
                                .executes(ctx -> {
                                    HoundEntity.forcedStage = IntegerArgumentType.getInteger(ctx, "n");
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Shadow Hound stage = " + HoundEntity.forcedStage), true);
                                    return 1;
                                })))
                .then(Commands.literal("auto")
                        .executes(ctx -> {
                            HoundEntity.forcedStage = -1;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Shadow Hound stage = automatic (by days)"), true);
                            return 1;
                        }))
                .then(Commands.literal("ritual")
                        .executes(ctx -> {
                            ServerLevel lvl = ctx.getSource().getLevel();
                            Player pl = ctx.getSource().getPlayerOrException();
                            BlockPos base = pl.blockPosition().relative(pl.getDirection(), 9);
                            RitualSite.build(lvl, lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base));
                            return 1;
                        })));
    }
}
