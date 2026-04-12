package net.milazy.fusemod.handler;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.component.FuseData;
import net.milazy.fusemod.data.FuseMaterialBonus;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 融合/解除融合交互逻辑。
 *
 * 操作方式：
 *   融合：主手持武器 + 副手持可融合材料 → 右键（不下蹲）
 *   解除：主手持已融合武器 → 下蹲 + 右键
 *
 * 使用副手方案而非 UseEntityCallback（右键掉落物），原因：
 *   - ItemEntity hitbox 极小，玩家接近时自动拾取，根本没机会右键
 *   - 副手方案更直观，也更接近 TotK 的"手持材料靠近武器融合"体验
 */
public final class FuseHandler {

    static final Identifier FUSE_MODIFIER_ID = Identifier.fromNamespaceAndPath("fusemod", "fuse_attack_bonus");

    public static void registerEvents() {
        registerFuseAndUnfuseEvent();
        registerAttackEffect();
    }

    // =========================================================================
    // 融合 / 解除融合：统一入口，均需下蹲 + 右键
    //
    // 为什么两个操作都要求下蹲：
    //   UseItemCallback 在 Fabric 中独立于 UseEntityCallback / UseBlockCallback
    //   触发，不受它们的返回值影响。若不下蹲，普通右键（如右键村民、右键容器）
    //   也会意外触发融合。下蹲 + 右键点击方块/实体时，UseBlockCallback /
    //   UseEntityCallback 会先消费交互，UseItemCallback 仍会触发但此时
    //   玩家已处于交互状态，副手材料不会改变，是可接受的边界情况。
    //
    // 区分融合 vs 解除融合：
    //   武器已有 FUSE_DATA → 解除融合
    //   武器无 FUSE_DATA + 副手有可融合材料 → 融合
    // =========================================================================
    private static void registerFuseAndUnfuseEvent() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            ItemStack weapon = player.getMainHandItem();
            if (!isWeapon(weapon)) return InteractionResult.PASS;

            FuseData existingFuse = weapon.get(FuseComponents.FUSE_DATA);

            // --- 解除融合路径 ---
            if (existingFuse != null) {
                removeFuse(weapon, existingFuse, player, world);
                return InteractionResult.SUCCESS;
            }

            // --- 融合路径 ---
            ItemStack offHand = player.getOffhandItem();
            if (offHand.isEmpty()) return InteractionResult.PASS;

            String materialId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
            FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(materialId);

            if (bonus == null) {
                player.displayClientMessage(
                    Component.translatable("fusemod.fuse.invalid_material"), true);
                return InteractionResult.FAIL;
            }

            String materialName = new ItemStack(offHand.getItem()).getHoverName().getString();

            applyFuse(weapon, Identifier.parse(materialId), bonus);
            offHand.shrink(1);

            player.displayClientMessage(
                Component.translatable("fusemod.fuse.success",
                    materialName,
                    weapon.getHoverName().getString(),
                    String.format("+%.0f", bonus.attackBonus())
                ),
                true
            );
            return InteractionResult.SUCCESS;
        });
    }

    // =========================================================================
    // 战斗效果：命中时触发火焰/击退
    // =========================================================================
    private static void registerAttackEffect() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            FuseData fuseData = player.getMainHandItem().get(FuseComponents.FUSE_DATA);
            if (fuseData == null) return InteractionResult.PASS;

            FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(fuseData.materialId().toString());
            if (bonus == null) return InteractionResult.PASS;

            if (bonus.fireTicks() > 0) {
                entity.igniteForTicks(bonus.fireTicks());
            }

            if (bonus.knockback() > 0 && entity instanceof LivingEntity target) {
                applyExtraKnockback(player, target, bonus.knockback());
            }

            return InteractionResult.PASS;
        });
    }

    // =========================================================================
    // 写入融合数据
    // =========================================================================
    private static void applyFuse(ItemStack weapon, Identifier materialId, FuseMaterialBonus bonus) {
        weapon.set(FuseComponents.FUSE_DATA, new FuseData(materialId));

        if (bonus.attackBonus() > 0) {
            ItemAttributeModifiers existing = weapon.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY
            );

            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            existing.modifiers().forEach(entry ->
                builder.add(entry.attribute(), entry.modifier(), entry.slot())
            );
            builder.add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(
                    FUSE_MODIFIER_ID,
                    bonus.attackBonus(),
                    AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
            );

            weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }
    }

    // =========================================================================
    // 移除融合数据
    // =========================================================================
    private static void removeFuse(ItemStack weapon, FuseData fuseData, Player player, Level world) {
        weapon.remove(FuseComponents.FUSE_DATA);

        ItemAttributeModifiers existing = weapon.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        existing.modifiers().forEach(entry -> {
            if (!entry.modifier().id().equals(FUSE_MODIFIER_ID)) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        });
        weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        Item materialItem = BuiltInRegistries.ITEM.getValue(fuseData.materialId());
        if (materialItem != null && materialItem != Items.AIR) {
            ItemStack returnStack = new ItemStack(materialItem, 1);
            if (!player.getInventory().add(returnStack)) {
                world.addFreshEntity(new ItemEntity(
                    world, player.getX(), player.getY(), player.getZ(), returnStack
                ));
            }
        }

        player.displayClientMessage(Component.translatable("fusemod.unfuse.success"), true);
    }

    // =========================================================================
    // 额外击退
    // =========================================================================
    private static void applyExtraKnockback(Player player, LivingEntity target, int level) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return;
        double strength = level * 0.4;
        target.addDeltaMovement(new net.minecraft.world.phys.Vec3(
            (dx / dist) * strength, 0.1, (dz / dist) * strength
        ));
        target.hurtMarked = true;
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS)
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem;
    }

    private FuseHandler() {}
}
