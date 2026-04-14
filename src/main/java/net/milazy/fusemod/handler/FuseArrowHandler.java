package net.milazy.fusemod.handler;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.milazy.fusemod.FuseModMain;
import net.milazy.fusemod.arrow.FusedArrowItem;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 箭矢融合：Trigger B（手动预合成）
 *
 * 操作：主手持普通箭 + 副手持可融合材料 → 下蹲 + 右键
 * 结果：消耗 1 箭 + 1 材料，向背包添加 1 枚 FusedArrowItem
 *
 * Trigger A（实时弓发射）在 mixin.BowItemMixin / CrossbowItemMixin 实现。
 */
public final class FuseArrowHandler {

    public static void registerEvents() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            ItemStack mainHand = player.getMainHandItem();
            // 主手必须是普通箭（不是 FusedArrowItem，避免重复合成）
            if (!(mainHand.getItem() instanceof ArrowItem)
                    || mainHand.getItem() instanceof FusedArrowItem) {
                return InteractionResult.PASS;
            }

            ItemStack offHand = player.getOffhandItem();
            if (offHand.isEmpty()) return InteractionResult.PASS;

            // 副手必须是可融合材料
            String materialId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
            if (!FuseMaterialRegistry.isFuseable(materialId)) return InteractionResult.PASS;

            // 消耗 1 箭 + 1 材料
            mainHand.shrink(1);
            offHand.shrink(1);

            // 生成 FusedArrowItem 并放入背包
            ItemStack fusedArrow = FusedArrowItem.make(materialId);
            if (!player.getInventory().add(fusedArrow)) {
                world.addFreshEntity(new ItemEntity(
                    world, player.getX(), player.getY(), player.getZ(), fusedArrow
                ));
            }

            String materialName = new ItemStack(BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(materialId)
            )).getHoverName().getString();

            player.displayClientMessage(
                Component.translatable("fusemod.arrow.fused", materialName)
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA),
                true
            );
            return InteractionResult.SUCCESS;
        });
    }

    private FuseArrowHandler() {}
}
