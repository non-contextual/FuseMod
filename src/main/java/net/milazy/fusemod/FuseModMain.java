package net.milazy.fusemod;

import net.fabricmc.api.ModInitializer;
import net.milazy.fusemod.arrow.ArrowEffectRegistry;
import net.milazy.fusemod.arrow.FusedArrowEntity;
import net.milazy.fusemod.arrow.FusedArrowItem;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.milazy.fusemod.handler.FuseArrowHandler;
import net.milazy.fusemod.handler.FuseHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FuseMod 服务端（通用）入口。
 *
 * 初始化顺序：
 *   1. 注册自定义 Data Component 类型（必须在任何 ItemStack 操作前）
 *   2. 注册 FusedArrowItem + FusedArrowEntity
 *   3. 加载材料加成数据表（纯内存，不涉及注册表）
 *   4. 初始化箭矢特效注册表
 *   5. 注册交互事件（Fabric event hooks）
 */
public class FuseModMain implements ModInitializer {

    public static final String MOD_ID = "fusemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 融合箭实体类型，供 FusedArrowEntity / FusedArrowItem / Mixin 使用 */
    public static EntityType<FusedArrowEntity> FUSED_ARROW_ENTITY_TYPE;

    /** 融合箭物品，供 FuseArrowHandler / FusedArrowItem 使用 */
    public static FusedArrowItem FUSED_ARROW_ITEM;

    @Override
    public void onInitialize() {
        LOGGER.info("[FuseMod] 初始化中...");

        // 步骤 1：注册 DataComponentType
        FuseComponents.register();

        // 步骤 2：注册融合箭实体类型
        ResourceKey<EntityType<?>> entityKey = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, "fused_arrow")
        );
        FUSED_ARROW_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            entityKey,
            EntityType.Builder.<FusedArrowEntity>of(FusedArrowEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(4)
                .build(entityKey)
        );

        // 步骤 3：注册融合箭物品
        // MC 1.21.11 要求 Item.Properties 在构造前通过 setId() 绑定 ResourceKey，否则报 NPE
        ResourceKey<Item> itemKey = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(MOD_ID, "fused_arrow")
        );
        FUSED_ARROW_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            itemKey,
            new FusedArrowItem(new Item.Properties().stacksTo(64).setId(itemKey))
        );

        // 步骤 4：加载 fuse_materials.json 数据表
        FuseMaterialRegistry.load();

        // 步骤 5：初始化箭矢特效注册表（包含 Web 蜘蛛网清理 tick 事件注册）
        ArrowEffectRegistry.init();

        // 步骤 6：注册 Phase A 融合/解除融合事件
        FuseHandler.registerEvents();

        // 步骤 7：注册 Phase B 箭矢融合事件（Trigger B：下蹲+右键合成）
        FuseArrowHandler.registerEvents();

        LOGGER.info("[FuseMod] 初始化完成");
    }
}
