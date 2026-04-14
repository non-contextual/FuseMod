package net.milazy.fusemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.milazy.fusemod.arrow.FusedArrowItem;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.component.FuseData;
import net.milazy.fusemod.data.FuseMaterialBonus;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.milazy.fusemod.handler.FuseHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

/**
 * FuseMod 客户端入口。
 *
 * 负责纯客户端表现层：Tooltip 显示。
 * 游戏逻辑（属性修改、融合判断）全在服务端的 FuseHandler 里。
 *
 * Tooltip 规则：
 *   - 可融合但未融合：显示"副手持材料，下蹲+右键融合"提示（深灰色）
 *   - 已融合：显示材料名称 + 对应加成（金色/黄色），以及解除融合提示
 *   - FusedArrowItem：显示材料名 + 特效描述
 */
public class FuseModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FuseModMain.LOGGER.info("[FuseMod] 客户端初始化中...");

        // 注册融合箭实体渲染器
        // FusedArrowEntity extends Arrow，复用 TippableArrowRenderer（支持 getColor() tint）
        // 需要强转泛型：EntityType<FusedArrowEntity> → EntityType<? extends Arrow>
        @SuppressWarnings("unchecked")
        EntityType<? extends Arrow> fusedArrowType =
            (EntityType<? extends Arrow>) (EntityType<?>) FuseModMain.FUSED_ARROW_ENTITY_TYPE;
        EntityRendererRegistry.register(fusedArrowType, TippableArrowRenderer::new);

        registerTooltips();
        FuseModMain.LOGGER.info("[FuseMod] 客户端初始化完成");
    }

    private void registerTooltips() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {

            // ---- FusedArrowItem tooltip ----
            if (stack.getItem() instanceof FusedArrowItem) {
                FuseData data = stack.get(FuseComponents.FUSE_DATA);
                if (data == null) return;

                Item materialItem = BuiltInRegistries.ITEM.getValue(data.materialId());
                if (materialItem == null) return;
                String materialName = new ItemStack(materialItem).getHoverName().getString();

                FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(data.materialId().toString());

                // 金色：材料名
                lines.add(
                    Component.literal("Fused Arrow: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(materialName)
                            .withStyle(ChatFormatting.YELLOW))
                );

                // 黄色：特效描述，或"暂无特效"
                String effectKey = (bonus != null) ? bonus.arrowEffect() : null;
                if (effectKey != null) {
                    lines.add(
                        Component.translatable("fusemod.arrow.tooltip.effect." + effectKey)
                            .withStyle(ChatFormatting.YELLOW)
                    );
                } else {
                    lines.add(
                        Component.translatable("fusemod.arrow.tooltip.no_effect")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    );
                }

                lines.add(
                    Component.translatable("fusemod.arrow.tooltip.fires_from_bow")
                        .withStyle(ChatFormatting.DARK_GRAY)
                );
                return;
            }

            // ---- 普通箭矢：显示合成提示 ----
            if (stack.getItem() instanceof ArrowItem) {
                lines.add(
                    Component.translatable("fusemod.arrow.tooltip.how_to_make")
                        .withStyle(ChatFormatting.DARK_GRAY)
                );
                return;
            }

            FuseData fuseData = stack.get(FuseComponents.FUSE_DATA);

            // 未融合的可融合装备：显示操作提示
            if (fuseData == null) {
                if (FuseHandler.isFuseableEquipment(stack)) {
                    lines.add(
                        Component.translatable("fusemod.tooltip.how_to_fuse")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    );
                }
                return;
            }

            // 已融合：显示材料名称 + 加成
            Item materialItem = BuiltInRegistries.ITEM.getValue(fuseData.materialId());
            if (materialItem == null) return;
            String materialName = new ItemStack(materialItem).getHoverName().getString();

            FuseMaterialBonus bonus = FuseMaterialRegistry.getBonus(fuseData.materialId().toString());
            String bonusText = buildTooltipBonus(stack, bonus);

            // 金色 "Fused: " + 黄色 材料名+加成，对应 TotK 融合武器视觉风格
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

    /**
     * 根据装备类型和材料加成生成 tooltip 的加成文字。
     *   武器："+X atk"（有火焰/击退时追加）
     *   挖掘工具："+X.X spd"
     *   盾牌："(effect v1.2.0)"
     */
    private static String buildTooltipBonus(ItemStack stack, FuseMaterialBonus bonus) {
        if (bonus == null) return "";

        // 武器类（剑/斧/三叉戟）
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.getItem() instanceof TridentItem) {
            StringBuilder sb = new StringBuilder();
            if (bonus.attackBonus() > 0)  sb.append(String.format(" +%.0f atk", bonus.attackBonus()));
            if (bonus.fireTicks() > 0)    sb.append(String.format(" %ds fire", bonus.fireTicks() / 20));
            if (bonus.knockback() > 0)    sb.append(String.format(" kb+%d", bonus.knockback()));
            return sb.isEmpty() ? "" : sb.toString();
        }

        // 挖掘工具（镐/锹/锄）
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            if (bonus.miningSpeedBonus() > 0) {
                return String.format(" +%.1f spd", bonus.miningSpeedBonus());
            }
            return "";
        }

        // 盾牌
        if (stack.getItem() instanceof ShieldItem) {
            return "";
        }

        return "";
    }
}
