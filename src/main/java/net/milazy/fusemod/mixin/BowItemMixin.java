package net.milazy.fusemod.mixin;

import net.milazy.fusemod.FuseModMain;
import net.milazy.fusemod.arrow.FusedArrowEntity;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trigger A（弓）：在 BowItem.releaseUsing() 开头注入。
 *
 * 如果射手是玩家且副手持有可融合材料，则拦截原版发射逻辑，
 * 自行创建 FusedArrowEntity 并发射，同时消耗副手材料。
 *
 * 不拦截的条件（fall-through to vanilla）：
 *   - 副手为空 / 副手物品不在 fuse_materials.json 内
 *   - 玩家没有任何普通箭（创意模式或 infinity 除外）
 *   - 蓄力时间 < 3 tick（防止意外触发）
 */
@Mixin(BowItem.class)
public class BowItemMixin {

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fusemod$onBowRelease(
            ItemStack bowStack,
            Level world,
            LivingEntity user,
            int remainingUseTicks,
            CallbackInfoReturnable<Boolean> cir) {

        if (world.isClientSide()) return;
        if (!(user instanceof Player player)) return;

        // 副手必须有可融合材料
        ItemStack offHand = player.getOffhandItem();
        if (offHand.isEmpty()) return;
        String materialId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
        if (!FuseMaterialRegistry.isFuseable(materialId)) return;

        // 计算蓄力
        int useTicks = user.getUseItemRemainingTicks();
        // 原版弓：getUseDuration() = 72000，remainingUseTicks 是剩余 tick
        // 实际已拉弓时间 = getUseDuration - remainingUseTicks
        // 这里 user.getUseItemRemainingTicks() 就是 remainingUseTicks（同参数）
        int chargeTime = ((BowItem)(Object)this).getUseDuration(bowStack, user) - remainingUseTicks;
        if (chargeTime < 3) return; // 拉弓不足 3 tick，不触发

        float power = BowItem.getPowerForTime(chargeTime);
        if (power < 0.1f) return;

        // 玩家必须有至少 1 支普通箭（创意模式跳过检查）
        boolean hasArrow = player.isCreative()
            || player.getInventory().contains(new ItemStack(Items.ARROW));
        if (!hasArrow) return;

        // 消耗 1 支普通箭（非创意模式）
        if (!player.isCreative()) {
            player.getInventory().clearOrCountMatchingItems(
                stack -> stack.is(Items.ARROW), 1, null
            );
        }

        // 消耗 1 副手材料
        offHand.shrink(1);

        // 创建并发射 FusedArrowEntity
        FusedArrowEntity arrow = new FusedArrowEntity(FuseModMain.FUSED_ARROW_ENTITY_TYPE, world);
        arrow.setMaterialId(materialId);
        arrow.setOwner(player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        if (player.isCreative()) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        // 设置飞行速度（与原版弓一致：power * 3）
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, power * 3.0f, 1.0f);

        // 暴击检测：满弓发射
        if (power == 1.0f) {
            arrow.setCritArrow(true);
        }

        world.addFreshEntity(arrow);

        // 损耗弓（非创意模式）
        if (!player.isCreative()) {
            bowStack.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        }

        String materialName = new ItemStack(BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.Identifier.parse(materialId)
        )).getHoverName().getString();
        player.displayClientMessage(
            Component.translatable("fusemod.arrow.fused", materialName)
                .withStyle(ChatFormatting.DARK_AQUA),
            true
        );

        cir.setReturnValue(true); // 取消原版弓发射
    }
}
