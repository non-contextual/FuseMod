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
 *   融合：主手持可融合装备 + 副手持可融合材料 → 下蹲 + 右键
 *   解除：主手持已融合装备 → 下蹲 + 右键
 *
 * Phase A 支持的装备类型（均通过 ItemTag 判断，MC 1.21.11 已移除 SwordItem/DiggerItem 等类）：
 *   武器类：ItemTags.SWORDS, ItemTags.AXES, TridentItem → 攻击力加成
 *   挖掘工具：ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES → 挖掘效率加成
 *   盾牌：ShieldItem → 接受融合，显示 tooltip，Phase B 实现实际效果
 */
public final class FuseHandler {

    /** 攻击力 modifier 唯一 ID */
    static final Identifier FUSE_MODIFIER_ID = Identifier.fromNamespaceAndPath("fusemod", "fuse_attack_bonus");

    /** 挖掘效率 modifier 唯一 ID（对应 Attributes.MINING_EFFICIENCY） */
    static final Identifier FUSE_MINING_MODIFIER_ID = Identifier.fromNamespaceAndPath("fusemod", "fuse_mining_bonus");

    public static void registerEvents() {
        registerFuseAndUnfuseEvent();
        registerAttackEffect();
    }

    // =========================================================================
    // 融合 / 解除融合：下蹲 + 右键
    // =========================================================================
    private static void registerFuseAndUnfuseEvent() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            ItemStack equipment = player.getMainHandItem();
            if (!isFuseableEquipment(equipment)) return InteractionResult.PASS;

            FuseData existingFuse = equipment.get(FuseComponents.FUSE_DATA);

            // --- 解除融合 ---
            if (existingFuse != null) {
                removeFuse(equipment, existingFuse, player, world);
                return InteractionResult.SUCCESS;
            }

            // --- 融合 ---
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
            applyFuse(equipment, Identifier.parse(materialId), bonus);
            offHand.shrink(1);

            player.displayClientMessage(
                Component.translatable("fusemod.fuse.success",
                    materialName,
                    equipment.getHoverName().getString(),
                    buildBonusInfo(equipment, bonus)
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
    private static void applyFuse(ItemStack equipment, Identifier materialId, FuseMaterialBonus bonus) {
        equipment.set(FuseComponents.FUSE_DATA, new FuseData(materialId));

        if (isWeaponLike(equipment)) {
            // 武器：攻击力加成
            if (bonus.attackBonus() > 0) {
                appendAttributeModifier(equipment, Attributes.ATTACK_DAMAGE,
                    FUSE_MODIFIER_ID, bonus.attackBonus());
            }
        } else if (isToolLike(equipment)) {
            // 挖掘工具（非斧）：挖掘效率加成
            // Attributes.MINING_EFFICIENCY 在 MC 1.21.11 中对应 minecraft:mining_efficiency
            if (bonus.miningSpeedBonus() > 0) {
                appendAttributeModifier(equipment, Attributes.MINING_EFFICIENCY,
                    FUSE_MINING_MODIFIER_ID, bonus.miningSpeedBonus());
            }
        }
        // 盾牌：仅设置 FUSE_DATA，Phase B 实现实际效果
    }

    /**
     * 追加 attribute modifier 到 ItemStack 的 ATTRIBUTE_MODIFIERS DataComponent。
     * 保留所有现有 modifier（vanilla 基础值、其他 mod 注入的值），追加而非替换。
     */
    private static void appendAttributeModifier(
            ItemStack stack,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            Identifier modifierId,
            double value) {
        ItemAttributeModifiers existing = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        existing.modifiers().forEach(entry ->
            builder.add(entry.attribute(), entry.modifier(), entry.slot())
        );
        builder.add(
            attribute,
            new AttributeModifier(modifierId, value, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        );
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    // =========================================================================
    // 移除融合数据
    // =========================================================================
    private static void removeFuse(ItemStack equipment, FuseData fuseData, Player player, Level world) {
        equipment.remove(FuseComponents.FUSE_DATA);

        // 移除融合注入的所有 modifier（攻击力 + 挖掘效率），保留其余
        ItemAttributeModifiers existing = equipment.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        existing.modifiers().forEach(entry -> {
            Identifier id = entry.modifier().id();
            if (!id.equals(FUSE_MODIFIER_ID) && !id.equals(FUSE_MINING_MODIFIER_ID)) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        });
        equipment.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        // 归还材料：背包满则掉落脚边，不取消解融
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

    // =========================================================================
    // 辅助判断（均基于 ItemTag，兼容 MC 1.21.11 移除具体 Item 子类的变更）
    // =========================================================================

    /**
     * 是否为可融合的装备类型（Phase A：武器 + 挖掘工具 + 盾牌）。
     */
    public static boolean isFuseableEquipment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return isWeaponLike(stack)
            || isToolLike(stack)
            || stack.getItem() instanceof ShieldItem;
    }

    /**
     * 武器类：剑、斧、三叉戟 → 攻击力加成分支。
     * 注：AxeItem 仍存在于 1.21.11，用 instanceof 判断；
     * 剑和三叉戟使用 Tag 和 instanceof 双保险。
     */
    private static boolean isWeaponLike(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
            || stack.is(ItemTags.AXES)
            || stack.getItem() instanceof TridentItem;
    }

    /**
     * 挖掘工具（非斧，非武器）：镐、锹、锄 → 挖掘效率加成分支。
     * 用 Tag 判断，不依赖已移除的 DiggerItem 类。
     */
    private static boolean isToolLike(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES)
            || stack.is(ItemTags.SHOVELS)
            || stack.is(ItemTags.HOES);
    }

    /** 生成 actionbar 显示的加成描述，随装备类型变化 */
    private static String buildBonusInfo(ItemStack equipment, FuseMaterialBonus bonus) {
        if (isWeaponLike(equipment)) {
            return bonus.attackBonus() > 0
                ? String.format("+%.0f atk", bonus.attackBonus()) : "fused";
        }
        if (isToolLike(equipment)) {
            return bonus.miningSpeedBonus() > 0
                ? String.format("+%.1f spd", bonus.miningSpeedBonus()) : "fused";
        }
        return "cosmetic";  // 盾牌
    }

    private FuseHandler() {}
}
