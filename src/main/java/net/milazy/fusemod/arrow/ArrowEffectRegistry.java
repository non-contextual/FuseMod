package net.milazy.fusemod.arrow;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 箭矢特效注册表。
 *
 * 所有特效以字符串 key 注册（与 fuse_materials.json 的 arrow_effect 字段对应）。
 * 特效函数通过 TriggerType 声明自己在哪个命中事件触发。
 * FusedArrowEntity 的 onHitEntity / onHitBlock 根据 TriggerType 进行分发。
 *
 * Phase B 8 种特效：teleport, lightning, homing, scatter, web, bounce, nova, wither
 */
public final class ArrowEffectRegistry {

    /** 特效触发时机 */
    public enum TriggerType {
        ENTITY,  // 仅在命中生物时触发
        BLOCK,   // 仅在命中方块时触发
        BOTH     // 两种命中都触发
    }

    /**
     * 特效函数接口。
     * apply() 在命中事件发生时被调用（服务端）。
     */
    @FunctionalInterface
    public interface ArrowEffectFunction {
        void apply(FusedArrowEntity arrow, HitResult hit, Level world);

        default TriggerType trigger() { return TriggerType.BOTH; }
    }

    // 实际注册表
    private static final Map<String, ArrowEffectFunction> REGISTRY = new HashMap<>();

    // =========================================================================
    //  Web effect 状态管理（内聚在 ArrowEffectRegistry 内部，避免全局静态泄漏）
    // =========================================================================
    static final class WebArrowEffect {
        /**
         * 放置的蜘蛛网 → 放置时的游戏 tick。
         * Key 同时携带维度信息，避免不同维度相同坐标互相误删。
         */
        record DimBlockPos(ResourceKey<Level> dim, BlockPos pos) {}

        static final Map<DimBlockPos, Long> webSpawnTick = new HashMap<>();

        /** 蜘蛛网存在时间：15 秒 = 300 ticks */
        static final long WEB_LIFETIME_TICKS = 300L;

        static void registerCleanup() {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                long now = server.getTickCount();
                Iterator<Map.Entry<DimBlockPos, Long>> it = webSpawnTick.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<DimBlockPos, Long> entry = it.next();
                    if (now - entry.getValue() >= WEB_LIFETIME_TICKS) {
                        DimBlockPos key = entry.getKey();
                        ServerLevel level = server.getLevel(key.dim());
                        if (level != null) {
                            BlockState state = level.getBlockState(key.pos());
                            if (state.is(Blocks.COBWEB)) {
                                level.setBlockAndUpdate(key.pos(), Blocks.AIR.defaultBlockState());
                            }
                        }
                        it.remove();
                    }
                }
            });
        }
    }

    // =========================================================================
    //  初始化：注册所有内置特效
    // =========================================================================
    public static void init() {
        // ---- teleport ----
        // 命中方块时，箭的主人（玩家）传送到落点。类似投掷末影珍珠。
        REGISTRY.put("teleport", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BLOCK; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                if (!(world instanceof ServerLevel serverLevel)) return;
                Entity owner = arrow.getOwner();
                if (owner == null) return;

                Vec3 pos = hit.getLocation();
                // 传送到命中点正上方一格，避免卡进方块
                owner.teleportTo(serverLevel, pos.x, pos.y + 0.1, pos.z,
                    java.util.Set.of(), owner.getYRot(), owner.getXRot(), true);

                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.PORTAL,
                    pos.x, pos.y + 1.0, pos.z,
                    20, 0.3, 0.5, 0.3, 0.05
                );
                world.playSound(null, pos.x, pos.y, pos.z,
                    net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f, 1.0f
                );
            }
        });

        // ---- lightning ----
        // 命中点召唤闪电。在下雨时对命中点半径内的所有生物额外触发一次闪电伤害。
        REGISTRY.put("lightning", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BOTH; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                if (!(world instanceof ServerLevel serverLevel)) return;
                Vec3 pos = hit.getLocation();

                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
                bolt.setPos(pos.x, pos.y, pos.z);
                serverLevel.addFreshEntity(bolt);

                // 下雨时：chain 到附近 4 格内的生物
                if (world.isRaining()) {
                    int chainRadius = arrow.getMaterialParam(); // arrow_effect_param = 4
                    if (chainRadius <= 0) chainRadius = 4;
                    AABB box = new AABB(
                        pos.x - chainRadius, pos.y - chainRadius, pos.z - chainRadius,
                        pos.x + chainRadius, pos.y + chainRadius, pos.z + chainRadius
                    );
                    List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != arrow.getOwner() && e.isAlive());
                    for (LivingEntity target : nearby) {
                        LightningBolt chainBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
                        chainBolt.setPos(target.getX(), target.getY(), target.getZ());
                        chainBolt.setVisualOnly(false);
                        serverLevel.addFreshEntity(chainBolt);
                    }
                }
            }
        });

        // ---- homing ----
        // tick 时追踪最近敌对生物，不在此处 apply，由 FusedArrowEntity.tick() 处理。
        // 注册一个空 apply 防止 NPE；trigger 设为 BOTH 但实际不会被调用到。
        REGISTRY.put("homing", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BOTH; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                // homing 在 tick() 里处理，命中时无需额外操作
            }
        });

        // ---- scatter ----
        // 命中时在命中点附近生成 3 支普通箭，±15° 散射。
        REGISTRY.put("scatter", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BOTH; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                if (!(world instanceof ServerLevel)) return;
                Vec3 pos = hit.getLocation();
                Vec3 dir = arrow.getDeltaMovement().normalize();
                Entity owner = arrow.getOwner();

                for (int i = 0; i < 3; i++) {
                    // 在原方向基础上随机偏转 ±15°
                    double angle = Math.toRadians((i - 1) * 15.0);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    // 绕 Y 轴旋转 dir
                    Vec3 spread = new Vec3(
                        dir.x * cos - dir.z * sin,
                        dir.y,
                        dir.x * sin + dir.z * cos
                    ).normalize().scale(1.5);

                    Arrow scatterArrow = new Arrow(world,
                        pos.x, pos.y, pos.z,
                        net.minecraft.world.item.Items.ARROW.getDefaultInstance(),
                        net.minecraft.world.item.Items.BOW.getDefaultInstance()
                    );
                    scatterArrow.setDeltaMovement(spread);
                    if (owner instanceof LivingEntity livingOwner) {
                        scatterArrow.setOwner(livingOwner);
                    }
                    world.addFreshEntity(scatterArrow);
                }
            }
        });

        // ---- web ----
        // 命中方块时在命中点放置蜘蛛网，300 tick 后自动消除。
        REGISTRY.put("web", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BLOCK; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                if (!(hit instanceof BlockHitResult blockHit)) return;
                if (!(world instanceof ServerLevel)) return;

                // 放在命中点正上方一格（箭射中地面时才有意义）
                BlockPos pos = blockHit.getBlockPos().above();
                BlockState existing = world.getBlockState(pos);
                if (!existing.isAir()) return; // 已有方块则不覆盖

                world.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                WebArrowEffect.webSpawnTick.put(
                    new WebArrowEffect.DimBlockPos(world.dimension(), pos),
                    (long) world.getServer().getTickCount()
                );

                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    net.minecraft.sounds.SoundEvents.WOOL_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0f, 0.8f
                );
            }
        });

        // ---- bounce ----
        // 命中方块时反弹，最多 3 次，每次速度×0.6。由 FusedArrowEntity.onHitBlock 专门处理。
        // 此处注册仅为占位，实际逻辑在 FusedArrowEntity 里。
        REGISTRY.put("bounce", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BLOCK; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                // bounce 逻辑由 FusedArrowEntity.onHitBlock 直接处理，此处不重复
            }
        });

        // ---- nova ----
        // 命中时：power=2 无地形破坏爆炸 + 对 4 格内所有生物施加 Wither I 3 秒。
        REGISTRY.put("nova", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.BOTH; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                Vec3 pos = hit.getLocation();

                // 不破坏地形的爆炸
                world.explode(arrow, pos.x, pos.y, pos.z, 2.0f, Level.ExplosionInteraction.NONE);

                // Wither I 3秒 = 60 ticks，amplifier=0（Level I）
                AABB box = new AABB(
                    pos.x - 4, pos.y - 4, pos.z - 4,
                    pos.x + 4, pos.y + 4, pos.z + 4
                );
                List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != arrow.getOwner() && e.isAlive());
                for (LivingEntity target : nearby) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
            }
        });

        // ---- wither ----
        // 命中生物施加 Wither II 5 秒 = 100 ticks，amplifier=1（Level II）。
        REGISTRY.put("wither", new ArrowEffectFunction() {
            @Override
            public TriggerType trigger() { return TriggerType.ENTITY; }

            @Override
            public void apply(FusedArrowEntity arrow, HitResult hit, Level world) {
                if (!(hit instanceof EntityHitResult entityHit)) return;
                Entity target = entityHit.getEntity();
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                }
            }
        });

        // 注册蜘蛛网清理 tick 事件
        WebArrowEffect.registerCleanup();
    }

    /** 通过 key 获取特效函数，找不到返回 null。 */
    public static ArrowEffectFunction get(String key) {
        if (key == null) return null;
        return REGISTRY.get(key);
    }

    private ArrowEffectRegistry() {}
}
