package com.hollingsworth.arsnouveau.client.waila;

import com.hollingsworth.arsnouveau.common.block.tile.GhostWeaveTile;
import com.hollingsworth.arsnouveau.common.block.tile.MirrorWeaveTile;
import com.hollingsworth.arsnouveau.common.potions.ModPotions;
import net.minecraft.world.entity.player.Player;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class WailaArsNouveauPlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> {
            if (accessor instanceof BlockAccessor target) {
                Player player = accessor.getPlayer();
                if (player.isCreative() || player.isSpectator() || player.hasEffect(ModPotions.MAGIC_FIND_EFFECT.get()))
                    return accessor;

                if (target.getBlockEntity() instanceof GhostWeaveTile tile && tile.isInvisible()) {
                    return null;
                }

                if (target.getBlockEntity() instanceof MirrorWeaveTile tile) {
                    return registration.blockAccessor().from(target).blockState(tile.mimicState).build();
                }
            }
            return accessor;
        });
    }
}
