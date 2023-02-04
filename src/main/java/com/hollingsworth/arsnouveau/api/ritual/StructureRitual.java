package com.hollingsworth.arsnouveau.api.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

public abstract class StructureRitual extends AbstractRitual {
    public ResourceLocation structure;
    public BlockPos offset;
    public List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
    public int index;
    public int sourceRequired;
    public boolean hasConsumed;
    public ResourceKey<Biome> biome;

    public StructureRitual(ResourceLocation structure, BlockPos offset, int sourceRequired, ResourceKey<Biome> biome){
        this.structure = structure;
        this.offset = offset;
        this.sourceRequired = sourceRequired;
        this.hasConsumed = sourceRequired == 0;
        this.biome = biome;
    }

    @Override
    public void onStart() {
        super.onStart();
        if(getWorld().isClientSide)
            return;
        setup();
    }

    public void setup(){
        if(getWorld().isClientSide)
            return;
        StructureTemplateManager manager = getWorld().getServer().getStructureManager();
        StructureTemplate structureTemplate = manager.getOrCreate(structure);
        List<StructureTemplate.StructureBlockInfo> infoList = structureTemplate.palettes.get(0).blocks();
        blocks = new ArrayList<>(infoList.stream().filter(b -> !b.state.isAir()).toList());
        blocks.sort(new StructureComparator(getPos(), offset));
    }

    @Override
    protected void tick() {
        if(getWorld().isClientSide)
            return;
        if(!hasConsumed){
            setNeedsSource(true);
            return;
        }
        int placeCount = 0;
        while(placeCount < 5){
            if (index >= blocks.size()) {
                setFinished();
                return;
            }
            StructureTemplate.StructureBlockInfo blockInfo = blocks.get(index);
            BlockPos translatedPos = getPos().offset(blockInfo.pos.getX(), blockInfo.pos.getY(), blockInfo.pos.getZ()).offset(offset);
            if (getWorld().getBlockState(translatedPos).getMaterial().isReplaceable()) {
                getWorld().setBlock(translatedPos, blockInfo.state, 2);
                BlockEntity blockentity1 = getWorld().getBlockEntity(translatedPos);
                if (blockentity1 != null) {
                    if (blockentity1 instanceof RandomizableContainerBlockEntity) {
                        blockInfo.nbt.putLong("LootTableSeed", getWorld().random.nextLong());
                    }

                    blockentity1.load(blockInfo.nbt);
                }
                getWorld().playSound(null, translatedPos, blockInfo.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                placeCount++;
                if(biome != null){
                    RitualUtil.changeBiome(getWorld(), translatedPos, biome);
                }
            }
            index++;
        }
    }

    @Override
    public void setNeedsSource(boolean needMana) {
        super.setNeedsSource(needMana);
        if(!needMana){
            hasConsumed = true;
        }
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        index = tag.getInt("index");
        hasConsumed = tag.getBoolean("hasConsumed");
        setup();
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.putInt("index", index);
        tag.putBoolean("hasConsumed", hasConsumed);
    }

    @Override
    public int getSourceCost() {
        return sourceRequired;
    }

    @Override
    public abstract ResourceLocation getRegistryName();
}
