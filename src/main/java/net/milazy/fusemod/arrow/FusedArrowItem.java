package net.milazy.fusemod.arrow;

import net.milazy.fusemod.FuseModMain;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.component.FuseData;
import net.milazy.fusemod.data.FuseMaterialBonus;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 融合箭物品。
 *
 * 继承 ArrowItem（使得弓/弩能自动识别为箭矢）。
 * ItemStack 上携带 FUSE_DATA DataComponent，内含材料 ID。
 * 被弓发射时，createArrow() 返回 FusedArrowEntity 并注入材料 ID。
 */
public class FusedArrowItem extends ArrowItem {

    public FusedArrowItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 弓/弩调用此方法创建飞行实体。
     * arrowStack = 这个 FusedArrowItem 的 ItemStack（含 FUSE_DATA）。
     * weaponStack = 弓/弩 ItemStack（含附魔）。
     */
    @Override
    public AbstractArrow createArrow(Level world, ItemStack arrowStack, LivingEntity shooter, ItemStack weaponStack) {
        FusedArrowEntity entity = new FusedArrowEntity(FuseModMain.FUSED_ARROW_ENTITY_TYPE, world);

        // 从 DataComponent 读取材料 ID 并注入实体
        FuseData data = arrowStack.get(FuseComponents.FUSE_DATA);
        if (data != null) {
            entity.setMaterialId(data.materialId().toString());
        }

        return entity;
    }

    /**
     * 创建一个携带指定材料的 FusedArrowItem ItemStack（stack size = 1）。
     * 由 FuseArrowHandler 的 Trigger B（下蹲+右键合成）调用。
     *
     * 同时把材料颜色存入 CustomModelData.colors[0]，供 items/fused_arrow.json 的
     * minecraft:custom_model_data tint 读取，实现物品栏里的彩色箭头图标。
     */
    public static ItemStack make(String materialId) {
        ItemStack stack = new ItemStack(FuseModMain.FUSED_ARROW_ITEM, 1);
        stack.set(FuseComponents.FUSE_DATA,
            new FuseData(net.minecraft.resources.Identifier.parse(materialId)));

        // 颜色存入 CustomModelData.colors[0]（移除 alpha 位，只保留 RGB，MC tint 不需要 alpha）
        int color = ArrowColorMap.getColor(materialId) & 0x00FFFFFF;
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new CustomModelData(List.of(), List.of(), List.of(), List.of(color)));

        return stack;
    }

    /**
     * 获取此 ItemStack 对应的材料加成（可能返回 null）。
     */
    public static FuseMaterialBonus getBonus(ItemStack stack) {
        FuseData data = stack.get(FuseComponents.FUSE_DATA);
        if (data == null) return null;
        return FuseMaterialRegistry.getBonus(data.materialId().toString());
    }
}
