package net.milazy.fusemod.arrow;

import java.util.Map;

/**
 * 融合箭的箭头颜色映射。
 * 每种有特效的材料对应一个颜色（argb 格式，高 8 位为 alpha，通常 0xFF）。
 * 无特效的材料回退到默认黄色。
 */
public final class ArrowColorMap {

    /** 默认颜色：金黄色，表示"已融合但无特效" */
    private static final int DEFAULT_COLOR = 0xFFD4A017;

    private static final Map<String, Integer> COLORS = Map.of(
        "minecraft:ender_pearl",          0xFF9B59B6, // 紫色（末影传送）
        "minecraft:lightning_rod",        0xFFFFD700, // 亮黄色（闪电）
        "minecraft:echo_shard",           0xFF00CED1, // 青色（追踪）
        "minecraft:prismarine_shard",     0xFF1ABC9C, // 绿松石（散射）
        "minecraft:string",               0xFFE0E0E0, // 浅灰色（蜘蛛网）
        "minecraft:slime_ball",           0xFF2ECC71, // 绿色（弹跳）
        "minecraft:nether_star",          0xFFFFFFFF, // 白色（星爆）
        "minecraft:wither_skeleton_skull",0xFF2C3E50  // 暗黑色（凋零）
    );

    /**
     * 返回给定材料 ID 对应的颜色（ARGB）。
     * getColor() 返回值直接用于 Arrow 实体的箭头染色渲染。
     */
    public static int getColor(String materialId) {
        if (materialId == null || materialId.isEmpty()) return DEFAULT_COLOR;
        return COLORS.getOrDefault(materialId, DEFAULT_COLOR);
    }

    private ArrowColorMap() {}
}
