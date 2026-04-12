package net.milazy.fusemod.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;

/**
 * 注册自定义 Data Component 类型。
 * Mojang mappings：DataComponentType 在 net.minecraft.core.component，
 * 注册用 BuiltInRegistries.DATA_COMPONENT_TYPE。
 */
public final class FuseComponents {

    public static final DataComponentType<FuseData> FUSE_DATA = DataComponentType.<FuseData>builder()
        .persistent(FuseData.CODEC)
        .networkSynchronized(FuseData.STREAM_CODEC)
        .build();

    public static void register() {
        Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath("fusemod", "fuse_data"),
            FUSE_DATA
        );
    }

    private FuseComponents() {}
}
