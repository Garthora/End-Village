package com.garthora.endvillage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.world.entity.monster.Shulker;

/** Looks like a shulker in its job color, and shows its job name when you look at it. */
public class ShulkerVillagerRenderer extends ShulkerRenderer {
    public ShulkerVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean shouldShowName(Shulker entity) {
        if (super.shouldShowName(entity)) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.crosshairPickEntity == entity && mc.player != null && mc.player.distanceToSqr(entity) < 64.0;
    }
}
