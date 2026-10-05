package com.shadowhound;

import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ShadowHoundMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.HOUND.get(), HoundRenderer::new);
    }

    public static class HoundRenderer extends MobRenderer<HoundEntity, WolfModel<HoundEntity>> {
        // Texturas do proprio Minecraft. Para um visual proprio, troque por
        // assets/shadowhound/textures/entity/ e use new ResourceLocation(ShadowHoundMod.MODID, "...")
        private static final ResourceLocation CALM = new ResourceLocation("textures/entity/wolf/wolf.png");
        private static final ResourceLocation ANGRY = new ResourceLocation("textures/entity/wolf/wolf_angry.png");

        public HoundRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WolfModel<>(ctx.bakeLayer(ModelLayers.WOLF)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(HoundEntity e) {
            return e.getStage() >= 2 ? ANGRY : CALM;
        }

        @Override
        protected float getBob(HoundEntity e, float partialTicks) {
            return e.getTailAngle();
        }
    }
}
