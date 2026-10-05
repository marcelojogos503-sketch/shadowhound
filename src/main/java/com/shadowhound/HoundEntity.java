package com.shadowhound;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fases:
 * 0 = amigavel (segue o jogador)
 * 1 = observa de longe e some se voce chega perto ou encara demais
 * 2 = persegue quando voce NAO esta olhando (estilo Weeping Angel) e fica parado quando olha
 * 3 = agressivo: persegue e ataca, causando Escuridao
 */
public class HoundEntity extends Wolf {

    /** -1 = automatico (por dias do mundo). 0-3 = forcado via /shadowhound stage */
    public static int forcedStage = -1;

    private static final EntityDataAccessor<Integer> STAGE =
            SynchedEntityData.defineId(HoundEntity.class, EntityDataSerializers.INT);

    /** Chave (no NBT do jogador) que conta quantos filhotes foram levados. */
    public static final String TAKEN_KEY = "shadowhound_taken";

    private int watchedTicks = 0;
    private boolean bedside = false;
    private boolean sunrise = false;
    private int sunriseAge = 0;

    /** Modo "nascer do sol": fica sentado de um jeito estranho encarando o sol. */
    public void setSunrise(boolean b) {
        this.sunrise = b;
    }
    private int bedsideAge = 0;

    /** Modo "vigia da cama": fica parado olhando o jogador dormir. */
    public void setBedside(boolean b) {
        this.bedside = b;
    }

    public HoundEntity(EntityType<? extends HoundEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createHoundAttributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.38D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static int stageFor(Level level) {
        if (forcedStage >= 0) return forcedStage;
        long day = level.getDayTime() / 24000L;
        if (day < 2) return 0;
        if (day < 5) return 1;
        if (day < 8) return 2;
        return 3;
    }

    public static int stageFor(Level level, Player p) {
        if (forcedStage >= 0) return forcedStage;
        if (p != null && p.getPersistentData().getInt(TAKEN_KEY) >= 6) return 3;
        return stageFor(level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STAGE, 0);
    }

    public int getStage() {
        return this.entityData.get(STAGE);
    }

    public void setStage(int stage) {
        this.entityData.set(STAGE, stage);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Cat.class, 12.0F, 1.2D, 1.6D)); // ele tem medo de gatos
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, true) {
            @Override
            public boolean canUse() {
                return getStage() >= 3 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getStage() >= 3 && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(3, new BehaviorGoal());
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1,
                new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, t -> getStage() >= 3));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.tickCount % 40 == 0) {
            setStage(stageFor(level(), level().getNearestPlayer(this, 64.0D)));
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS; // nao pode ser domesticado
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getStage() >= 2 ? SoundEvents.WOLF_GROWL : null;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && getStage() >= 3 && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
        }
        return hit;
    }

    private boolean lookedAt(Player p) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
        double d = to.length();
        if (d < 0.001D) return true;
        to = to.normalize();
        return view.dot(to) > 1.0D - 0.25D / d && p.hasLineOfSight(this);
    }

    private void vanish(Player p) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5D, getZ(), 25, 0.3D, 0.4D, 0.3D, 0.02D);
            sl.playSound(null, blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.0F, 0.6F);
            if (getStage() >= 1 && getRandom().nextFloat() < 0.3F) {
                spawnAtLocation(Items.BONE); // ossos deixados pra tras
            }
            p.displayClientMessage(
                    Component.translatable("message.shadowhound.vanish." + (1 + getRandom().nextInt(3))), true);
        }
        discard();
    }

    private class BehaviorGoal extends Goal {
        private Player player;

        BehaviorGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (getStage() >= 3) return false;
            player = level().getNearestPlayer(HoundEntity.this, 48.0D);
            return player != null && !player.isSpectator();
        }

        @Override
        public boolean canContinueToUse() {
            return getStage() < 3 && player != null && player.isAlive() && distanceTo(player) < 64.0F;
        }

        @Override
        public void start() {
            watchedTicks = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (sunrise) {
                getNavigation().stop();
                setInSittingPose(true);
                getLookControl().setLookAt(getX() + 100.0D, getEyeY() + 6.0D, getZ());
                sunriseAge++;
                if (lookedAt(player)) watchedTicks++;
                if (distanceTo(player) < 6.0F || watchedTicks > 80 || sunriseAge > 1500) {
                    setInSittingPose(false);
                    vanish(player);
                }
                return;
            }
            getLookControl().setLookAt(player, 30.0F, 30.0F);
            double dist = distanceTo(player);
            boolean watched = lookedAt(player);
            int stage = getStage();

            if (bedside) {
                getNavigation().stop();
                bedsideAge++;
                if (!player.isSleeping() && watched) watchedTicks++;
                if (watchedTicks > 60 || bedsideAge > 1200) vanish(player);
                return;
            }

            if (stage == 0) {
                if (dist > 6.0D) getNavigation().moveTo(player, 1.0D);
                else getNavigation().stop();
            } else if (stage == 1) {
                watchedTicks = watched ? watchedTicks + 1 : Math.max(0, watchedTicks - 1);
                if (!watched && dist > 22.0D) getNavigation().moveTo(player, 0.9D);
                else getNavigation().stop();
                if (dist < 9.0D || watchedTicks > 80) vanish(player);
            } else {
                if (watched) {
                    getNavigation().stop();
                    watchedTicks++;
                    if (watchedTicks > 120 && dist < 14.0D) vanish(player);
                } else {
                    watchedTicks = Math.max(0, watchedTicks - 2);
                    getNavigation().moveTo(player, 1.5D);
                    if (dist < 2.5D) {
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                        vanish(player);
                    }
                }
            }
        }
    }
}
