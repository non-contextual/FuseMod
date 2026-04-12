package net.milazy.fusemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.component.FuseData;
import net.milazy.fusemod.data.FuseMaterialBonus;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/**
 * FuseMod 客户端入口。
 *
 * 负责纯客户端的表现层：Tooltip 显示。
 * 游戏逻辑（属性修改、融合判断）全在服务端的 FuseHandler 里。
 */
public class FuseModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FuseModMain.LOGGER.info("[FuseMod] 客户端初始化中...");
        registerTooltips();
        FuseModMain.LOGGER.info("[FuseMod] 客户端初始化完成");
    }

    private void registerTooltips() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            FuseData fuseData = stack.get(FuseComponents.FUSE_DATA);

            // 未融合的武器：始终显示"副手+右键融合"提示
            if (fuseData == null) {
                if (isWeapon(stack)) {
                    lines.add(
                        Component.translatable("fusemod.tooltip.how_to_fuse")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    );
                }
                return;
            }

            // 已融合：显示材料名称和攻击加成
            var materialItem = BuiltInRegistries.ITEM.getValue(fuseData.materialId());
            if (materialItem == null) return;
            String materialName = new ItemStack(materialItem).getHoverName().getString();

            FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(fuseData.materialId().toString());
            String bonusText = (bonus != null && bonus.attackBonus() > 0)
                ? String.format(" (+%.0f atk)", bonus.attackBonus()) : "";

            // 金色+黄色对应 TotK 融合武器的视觉风格
            lines.add(
                Component.literal("Fused: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(materialName + bonusText)
                        .withStyle(ChatFormatting.YELLOW))
            );

            lines.add(
                Component.translatable("fusemod.tooltip.hint")
                    .withStyle(ChatFormatting.DARK_GRAY)
            );
        });
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS)
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem;
    }
}
