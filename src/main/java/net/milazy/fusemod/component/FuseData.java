package net.milazy.fusemod.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * 存在 ItemStack 上的融合数据 Data Component。
 * 1.21.11：ResourceLocation 更名为 Identifier（net.minecraft.resources.Identifier）。
 */
public record FuseData(Identifier materialId) {

    public static final Codec<FuseData> CODEC = Identifier.CODEC.xmap(
        FuseData::new,
        FuseData::materialId
    );

    public static final StreamCodec<ByteBuf, FuseData> STREAM_CODEC =
        Identifier.STREAM_CODEC.map(FuseData::new, FuseData::materialId);
}
