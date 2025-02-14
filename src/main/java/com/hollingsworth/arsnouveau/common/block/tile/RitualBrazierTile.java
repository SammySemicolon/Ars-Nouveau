package com.hollingsworth.arsnouveau.common.block.tile;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.entity.IDispellable;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.item.inv.IInvProvider;
import com.hollingsworth.arsnouveau.api.item.inv.InventoryManager;
import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.hollingsworth.arsnouveau.api.spell.ILightable;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.client.particle.GlowParticleData;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.block.ITickable;
import com.hollingsworth.arsnouveau.common.block.RitualBrazierBlock;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import com.hollingsworth.arsnouveau.setup.BlockRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

import java.util.List;

public class RitualBrazierTile extends ModdedTile implements ITooltipProvider, IAnimatable, ILightable, ITickable, IInvProvider, IDispellable, IWandable {
    public AbstractRitual ritual;
    AnimationFactory manager = GeckoLibUtil.createFactory(this);
    public boolean isDecorative;
    public ParticleColor color = ParticleColor.defaultParticleColor();
    public boolean isOff;
    public BlockPos relayPos;


    public RitualBrazierTile(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
        super(tileEntityTypeIn, pos, state);
    }

    public RitualBrazierTile(BlockPos p, BlockState s) {
        super(BlockRegistry.RITUAL_TILE.get(), p, s);
    }

    @Override
    public void onFinishedConnectionFirst(@Nullable BlockPos storedPos, @Nullable LivingEntity storedEntity, Player playerEntity) {
        // check if position is a BrazierRelayTile
        if(storedPos != null && level.getBlockEntity(storedPos) instanceof BrazierRelayTile relayTile){
            if(BlockUtil.distanceFrom(getBlockPos(), storedPos) > 16){
                PortUtil.sendMessage(playerEntity, Component.translatable("ars_nouveau.connections.fail"));
                return;
            }
            relayPos = storedPos.immutable();
            PortUtil.sendMessage(playerEntity, Component.translatable("ars_nouveau.brazier_relay.connected"));
            updateBlock();
        }
    }

    @Override
    public void onWanded(Player playerEntity) {
        if(relayPos != null){
            relayPos = null;
            PortUtil.sendMessage(playerEntity, Component.translatable("ars_nouveau.connections.cleared"));
            updateBlock();
        }
    }

    public void makeParticle(ParticleColor centerColor, ParticleColor outerColor, int intensity) {
        Level world = getLevel();
        BlockPos pos = getBlockPos();
        double xzOffset = 0.25;
        for (int i = 0; i < intensity; i++) {
            world.addParticle(
                    GlowParticleData.createData(centerColor),
                    pos.getX() + 0.5 + ParticleUtil.inRange(-xzOffset / 2, xzOffset / 2), pos.getY() + 1 + ParticleUtil.inRange(-0.05, 0.2), pos.getZ() + 0.5 + ParticleUtil.inRange(-xzOffset / 2, xzOffset / 2),
                    0, ParticleUtil.inRange(0.0, 0.05f), 0);
        }
        for (int i = 0; i < intensity; i++) {
            world.addParticle(
                    GlowParticleData.createData(outerColor),
                    pos.getX() + 0.5 + ParticleUtil.inRange(-xzOffset, xzOffset), pos.getY() + 1 + ParticleUtil.inRange(0, 0.7), pos.getZ() + 0.5 + ParticleUtil.inRange(-xzOffset, xzOffset),
                    0, ParticleUtil.inRange(0.0, 0.05f), 0);
        }
        if(relayPos != null && level.getBlockEntity(relayPos) instanceof BrazierRelayTile relayTile){
            relayTile.makeParticle(centerColor, outerColor, intensity);
        }
    }

    @Override
    public void tick() {
        if (isDecorative && level.isClientSide) {
            makeParticle(color.nextColor(level.random), color.nextColor(level.random), 10);
            return;
        }


        if (level.isClientSide && ritual != null) {
            makeParticle(ritual.getCenterColor(), ritual.getOuterColor(), ritual.getParticleIntensity());
        }
        if (isOff)
            return;
        if (ritual != null) {

            if (ritual.getContext().isDone) {
                ritual.onEnd();
                ritual = null;
                getLevel().playSound(null, getBlockPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0f, 1.0f);
                getLevel().setBlock(getBlockPos(), getLevel().getBlockState(getBlockPos()).setValue(RitualBrazierBlock.LIT, false), 3);
                updateBlock();
                return;
            }
            if (!ritual.isRunning() && !level.isClientSide && level.getGameTime() % 5 == 0) {
                level.getEntitiesOfClass(ItemEntity.class, new AABB(getBlockPos()).inflate(1)).forEach(i -> {
                    tryBurnStack(i.getItem());
                });
            }
            if (ritual.consumesSource() && ritual.needsSourceNow()) {
                int cost = ritual.getSourceCost();
                if (SourceUtil.takeSourceWithParticles(getBlockPos(), getLevel(), 6, cost) != null) {
                    ritual.setNeedsSource(false);
                    updateBlock();
                } else {
                    return;
                }
            }
            if(this.relayPos != null && level.isLoaded(this.relayPos) && level.getBlockEntity(this.relayPos) instanceof BrazierRelayTile relayTile){
                ritual.tryTick(relayTile);
                relayTile.ticksToLightOff = 2;
                relayTile.isDecorative = false;
            }else{
                ritual.tryTick(this);
            }
        }
    }

    public boolean tryBurnStack(ItemStack stack){
        if(ritual != null && !ritual.isRunning() && !level.isClientSide && ritual.canConsumeItem(stack)) {
            ritual.onItemConsumed(stack);
            ParticleUtil.spawnPoof((ServerLevel) level, getBlockPos());
            level.playSound(null, getBlockPos(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.3f, 1.0f);
            return true;
        }
        return false;
    }

    public boolean isRitualDone() {
        return ritual != null && ritual.getContext().isDone;
    }

    public boolean canRitualStart() {
        return ritual.canStart();
    }

    public void startRitual() {
        if (ritual == null || !ritual.canStart() || ritual.isRunning())
            return;
        getLevel().playSound(null, getBlockPos(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.NEUTRAL, 1.0f, 1.0f);
        ritual.onStart();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String ritualIDString = tag.getString("ritualID");
        if (!ritualIDString.isEmpty()) {
            ResourceLocation ritualID = new ResourceLocation(ritualIDString);
            ritual = ArsNouveauAPI.getInstance().getRitual(ritualID);
            if (ritual != null) {
                ritual.tile = this;
                ritual.read(tag);
            }
        } else {
            ritual = null;
        }
        color = ParticleColor.deserialize(tag.getCompound("color"));
        isDecorative = tag.getBoolean("decorative");
        isOff = tag.getBoolean("off");

        if(tag.contains("relayPos")){
            this.relayPos = BlockPos.of(tag.getLong("relayPos"));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        if (ritual != null) {
            tag.putString("ritualID", ritual.getRegistryName().toString());
            ritual.write(tag);
        } else {
            tag.remove("ritualID");
        }
        tag.put("color", color.serialize());
        tag.putBoolean("decorative", isDecorative);
        tag.putBoolean("off", isOff);
        // store the relay position
        if(this.relayPos != null){
            tag.putLong("relayPos", this.relayPos.asLong());
        }
    }

    public boolean canTakeAnotherRitual() {
        return this.ritual == null || this.ritual.isRunning();
    }

    public void setRitual(ResourceLocation selectedRitual) {
        this.ritual = ArsNouveauAPI.getInstance().getRitual(selectedRitual);
        if (ritual != null) {
            this.ritual.tile = this;
            Level world = getLevel();
            BlockState state = world.getBlockState(getBlockPos());
            world.setBlock(getBlockPos(), state.setValue(RitualBrazierBlock.LIT, true), 3);
        }
        this.isDecorative = false;
        level.playSound(null, getBlockPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0f, 1.0f);
        updateBlock();
    }

    @Override
    public void getTooltip(List<Component> tooltips) {
        if (ritual != null) {
            tooltips.add(Component.literal(ritual.getName()));
            if (isOff) {
                tooltips.add(Component.translatable("ars_nouveau.tooltip.turned_off").withStyle(ChatFormatting.GOLD));
                return;
            }
            if (!ritual.isRunning()) {
                if (!ritual.canStart()) {
                    tooltips.add(Component.translatable("ars_nouveau.tooltip.conditions_unmet").withStyle(ChatFormatting.GOLD));
                } else
                    tooltips.add(Component.translatable("ars_nouveau.tooltip.waiting").withStyle(ChatFormatting.GOLD));
            } else {

                tooltips.add(Component.translatable("ars_nouveau.tooltip.running"));
            }
            if (!ritual.getConsumedItems().isEmpty()) {
                tooltips.add(Component.translatable("ars_nouveau.tooltip.consumed"));
                for (String i : ritual.getFormattedConsumedItems()) {
                    tooltips.add(Component.literal( i));
                }
            }
            if (ritual.needsSourceNow())
                tooltips.add(Component.translatable("ars_nouveau.wixie.need_mana").withStyle(ChatFormatting.GOLD));
        }
    }


    @Override
    public void registerControllers(AnimationData animationData) {
    }

    @Override
    public AnimationFactory getFactory() {
        return manager;
    }

    @Override
    public void onLight(HitResult rayTraceResult, Level world, LivingEntity shooter, SpellStats stats, SpellContext spellContext) {
        this.color = spellContext.getColors();
        this.isDecorative = true;
        BlockState state = world.getBlockState(getBlockPos());
        world.setBlock(getBlockPos(), state.setValue(RitualBrazierBlock.LIT, true), 3);
        updateBlock();
    }

    @Override
    public boolean onDispel(@Nullable LivingEntity caster) {
        if(!isDecorative)
            return false;
        isDecorative = false;
        level.setBlock(getBlockPos(), level.getBlockState(getBlockPos()).setValue(RitualBrazierBlock.LIT, false), 3);
        updateBlock();
        return true;
    }

    @Override
    public InventoryManager getInventoryManager() {
        return InventoryManager.fromTile(this);
    }
}
