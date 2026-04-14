package net.milazy.fusemod.data;

/**
 * 对应 fuse_materials.json 里一条材料的加成数据。
 * 设计参考 TotK 的 fuse_attack_power 系统：每种材料有固定的平坦加值，
 * 不是百分比——这保证了低级武器融合后也有意义，高级武器不会失控暴涨。
 *
 * Phase B 新增 arrowEffect + arrowEffectParam，用于箭矢融合效果分发。
 */
public record FuseMaterialBonus(
    double attackBonus,      // 叠加到武器基础攻击力的绝对值（对应 TotK fuse_attack_power）
    int fireTicks,           // 命中时点燃目标的持续 tick（20=1秒），0 表示无火焰
    int knockback,           // 额外击退等级
    double miningSpeedBonus, // 叠加到挖掘工具采掘效率的绝对值（仅对非斧 DiggerItem 生效）
    String arrowEffect,      // 箭矢特效 key，例如 "teleport"/"homing"/"web"；null = 无特效
    int arrowEffectParam     // 特效附加参数，例如 homing 追踪半径（方块数），0 = 不使用
) {
    /** 无加成的默认值，用于找不到材料时的安全回退 */
    public static final FuseMaterialBonus EMPTY = new FuseMaterialBonus(0, 0, 0, 0.0, null, 0);

    public boolean hasAnyBonus() {
        return attackBonus > 0 || fireTicks > 0 || knockback > 0 || miningSpeedBonus > 0;
    }
}
