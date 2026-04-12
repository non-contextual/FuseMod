package net.milazy.fusemod;

import net.fabricmc.api.ModInitializer;
import net.milazy.fusemod.component.FuseComponents;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.milazy.fusemod.handler.FuseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FuseMod 服务端（通用）入口。
 *
 * 初始化顺序：
 *   1. 注册自定义 Data Component 类型（必须在任何 ItemStack 操作前）
 *   2. 加载材料加成数据表（纯内存，不涉及注册表）
 *   3. 注册交互事件（Fabric event hooks）
 */
public class FuseModMain implements ModInitializer {

    public static final String MOD_ID = "fusemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[FuseMod] 初始化中...");

        // 步骤 1：注册 DataComponentType
        // 必须在这里做，Fabric 要求组件类型在 onInitialize 阶段注册
        FuseComponents.register();

        // 步骤 2：加载 fuse_materials.json 数据表
        FuseMaterialRegistry.load();

        // 步骤 3：注册融合/解除融合事件
        FuseHandler.registerEvents();

        LOGGER.info("[FuseMod] 初始化完成");
    }
}
