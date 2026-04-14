package net.milazy.fusemod.mixin;

import net.milazy.fusemod.FuseModMain;
import net.milazy.fusemod.arrow.FusedArrowEntity;
import net.milazy.fusemod.data.FuseMaterialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trigger A（弩）：在 CrossbowItem.performShooting() 开头注入。
 *
 * 逻辑与 BowItemMixin 类似，但弩是在 performShooting() 阶段发射。
 * 弩已经装填完成时（持有者 releaseUsing），检查副手材料并发射融合箭。
 */
@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {

    @Inject(method = "performShooting", at = @At("HEAD"), cancellable = true)
    private void fusemod$onCrossbowShoot(
            Level world,
            LivingEntity shooter,
            InteractionHand hand,
            ItemStack crossbowStack,
            float velocity,
            float inaccuracy,
            LivingEntity target,
            CallbackInfo ci) {

        if (world.isClientSide()) return;
        if (!(shooter instanceof Player player)) return;

        // 副手必须有可融合材料（弩是主手，副手是材料）
        if (hand != InteractionHand.MAIN_HAND) return;
        ItemStack offHand = player.getOffhandItem();
        if (offHand.isEmpty()) return;
        String materialId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
        if (!FuseMaterialRegistry.isFuseable(materialId)) return;

        // 弩已装填检查（弩只有在已装填时才执行 performShooting）
        // 消耗副手材料
        offHand.shrink(1);

        // 创建并发射 FusedArrowEntity（弩速度固定 3.15f，精准度 1.0f 比弓更直）
        FusedArrowEntity arrow = new FusedArrowEntity(FuseModMain.FUSED_ARROW_ENTITY_TYPE, world);
        arrow.setMaterialId(materialId);
        arrow.setOwner(player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        if (player.isCreative()) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, velocity, inaccuracy);
        arrow.setCritArrow(true); // 弩箭全程暴击

        world.addFreshEntity(arrow);

        // 清除弩的已装填状态。
        // 原版 performShooting() 在方法体最开头做这件事，我们 cancel 后必须手动补上，
        // 否则弩仍显示 charged，且其中装填的普通箭不会被消耗（相当于白得一支融合箭）。
        crossbowStack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

        // 损耗弩（非创意模式）
        if (!player.isCreative()) {
            crossbowStack.hurtAndBreak(1, player, hand);
        }

        String materialName = new ItemStack(BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.Identifier.parse(materialId)
        )).getHoverName().getString();
        player.displayClientMessage(
            Component.translatable("fusemod.arrow.fused", materialName)
                .withStyle(ChatFormatting.DARK_AQUA),
            true
        );

        ci.cancel(); // 取消原版弩发射（避免同时发射两支箭）
    }
}
