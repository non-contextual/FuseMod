package net.milazy.fusemod.arrow;

import net.milazy.fusemod.FuseModMain;
import net.milazy.fusemod.data.FuseMaterialBonus;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * 融合箭实体。
 *
 * 通过 SynchedEntityData 同步 materialId 到客户端（供颜色渲染用）。
 * 命中事件分发到 ArrowEffectRegistry；homing/bounce 在本类内部处理。
 *
 * 继承自 Arrow 而非 AbstractArrow，保留全部原版箭矢行为（重力、穿透、伤害计算）。
 */
public class FusedArrowEntity extends Arrow {

    /** 同步材料 ID 到客户端，用于渲染颜色 tint */
    private static final EntityDataAccessor<String> MATERIAL_ID =
        SynchedEntityData.defineId(FusedArrowEntity.class, EntityDataSerializers.STRING);

    /** bounce 剩余次数（服务端状态，不需要同步） */
    public int bounceCount = 0;

    // =========================================================================
    //  构造器
    // =========================================================================

    /** EntityType.Builder 使用的基础构造器 */
    public FusedArrowEntity(EntityType<? extends FusedArrowEntity> type, Level world) {
        super(type, world);
        this.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    // =========================================================================
    //  Material ID 存取
    // =========================================================================

    public void setMaterialId(String materialId) {
        this.entityData.set(MATERIAL_ID, materialId == null ? "" : materialId);
    }

    public String getMaterialId() {
        return this.entityData.get(MATERIAL_ID);
    }

    /** 获取特效附加参数（例如 homing 追踪半径，chain 范围） */
    public int getMaterialParam() {
        FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(getMaterialId());
        return (bonus != null) ? bonus.arrowEffectParam() : 0;
    }

    private String getMaterialEffect() {
        FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(getMaterialId());
        return (bonus != null) ? bonus.arrowEffect() : null;
    }

    // =========================================================================
    //  SynchedEntityData 注册
    // =========================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MATERIAL_ID, "");
    }

    // =========================================================================
    //  颜色 tint（供 Arrow entity 渲染用）
    // =========================================================================

    @Override
    public int getColor() {
        return ArrowColorMap.getColor(getMaterialId());
    }

    // =========================================================================
    //  tick：homing 追踪逻辑（服务端，最多 10 tick/帧不限制，靠 max 10 concurrent 限制）
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (isInGround()) return;
        if ("homing".equals(getMaterialEffect())) {
            tickHoming();
        }
        // homing 每 5 tick 发一次粒子轨迹
        if ("homing".equals(getMaterialEffect()) && this.tickCount % 5 == 0) {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    getX(), getY(), getZ(),
                    3, 0.15, 0.15, 0.15, 0.0
                );
            }
        }
    }

    private void tickHoming() {
        int radius = getMaterialParam();
        if (radius <= 0) radius = 12;

        AABB box = this.getBoundingBox().inflate(radius);
        List<LivingEntity> hostiles = level().getEntitiesOfClass(LivingEntity.class, box,
            e -> e instanceof Enemy && e.isAlive() && e != getOwner());

        if (hostiles.isEmpty()) return;

        LivingEntity target = hostiles.stream()
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(this)))
            .orElse(null);
        if (target == null) return;

        Vec3 toTarget = target.getEyePosition().subtract(position()).normalize();
        Vec3 vel = getDeltaMovement();
        double speed = vel.length();
        if (speed < 0.001) return;

        // 每 tick 向目标偏转 0.05 速度单位
        Vec3 newVel = vel.add(toTarget.scale(0.05));
        // 保持原速度大小
        setDeltaMovement(newVel.normalize().scale(speed));
    }

    // =========================================================================
    //  命中实体：效果分发
    // =========================================================================

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        dispatchEffect(hit, ArrowEffectRegistry.TriggerType.ENTITY);
    }

    // =========================================================================
    //  命中方块：bounce 拦截 + 效果分发
    // =========================================================================

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        // bounce 特效：最多反弹 3 次，拦截 super（阻止箭插入地面）
        if ("bounce".equals(getMaterialEffect()) && bounceCount < 3) {
            handleBounce(hit);
            return;
        }
        super.onHitBlock(hit);
        dispatchEffect(hit, ArrowEffectRegistry.TriggerType.BLOCK);
    }

    private void handleBounce(BlockHitResult hit) {
        bounceCount++;
        Direction face = hit.getDirection();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 vel = getDeltaMovement();
        double dot = vel.dot(normal);
        // 反射：v - 2*(v·n)*n，速度衰减为 0.6
        Vec3 reflected = vel.subtract(normal.scale(2.0 * dot)).scale(0.6);
        setDeltaMovement(reflected);

        if (level() instanceof ServerLevel sl) {
            Vec3 pos = hit.getLocation();
            sl.sendParticles(
                ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                5, 0.1, 0.1, 0.1, 0.05
            );
        }
        level().playSound(null,
            getX(), getY(), getZ(),
            net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.8f, 1.2f
        );
    }

    // =========================================================================
    //  效果分发（ENTITY / BLOCK / BOTH 路由）
    // =========================================================================

    private void dispatchEffect(HitResult hit, ArrowEffectRegistry.TriggerType eventType) {
        if (level().isClientSide()) return;
        String effectKey = getMaterialEffect();
        if (effectKey == null) return;

        ArrowEffectRegistry.ArrowEffectFunction fn = ArrowEffectRegistry.get(effectKey);
        if (fn == null) return;

        ArrowEffectRegistry.TriggerType registered = fn.trigger();
        // 只在匹配的事件类型中触发
        if (registered == ArrowEffectRegistry.TriggerType.ENTITY
                && eventType != ArrowEffectRegistry.TriggerType.ENTITY) return;
        if (registered == ArrowEffectRegistry.TriggerType.BLOCK
                && eventType != ArrowEffectRegistry.TriggerType.BLOCK) return;

        fn.apply(this, hit, level());
    }
}
