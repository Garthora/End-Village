package com.garthora.endvillage.entity;

import com.garthora.endvillage.EndVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The trades each kind of Shulker Villager offers. Emeralds are the money, same as the Overworld. */
public final class EndTrades {
    public static final TagKey<Structure> ON_END_CITY_MAPS =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation(EndVillageMod.MODID, "on_end_city_maps"));

    private EndTrades() {}

    public static MerchantOffers create(EndProfession profession, RandomSource random) {
        MerchantOffers offers = new MerchantOffers();
        switch (profession) {
            case CHORUS_FARMER -> {
                offers.add(buy(new ItemStack(Items.CHORUS_FRUIT, 24), 1, 16));
                offers.add(buy(new ItemStack(Items.POPPED_CHORUS_FRUIT, 12), 1, 16));
                offers.add(sell(1, new ItemStack(Items.CHORUS_FRUIT, 8), 16));
                offers.add(sell(2, new ItemStack(Items.CHORUS_FLOWER, 1), 8));
                offers.add(sell(3, new ItemStack(Items.END_ROD, 4), 12));
            }
            case PURPUR_MASON -> {
                offers.add(buy(new ItemStack(Items.END_STONE, 32), 1, 16));
                offers.add(sell(1, new ItemStack(Items.PURPUR_BLOCK, 8), 16));
                offers.add(sell(1, new ItemStack(Items.PURPUR_PILLAR, 6), 16));
                offers.add(sell(1, new ItemStack(Items.END_STONE_BRICKS, 8), 16));
                offers.add(sell(2, new ItemStack(Items.PURPLE_STAINED_GLASS, 8), 12));
            }
            case VOID_LIBRARIAN -> {
                offers.add(buy(new ItemStack(Items.PAPER, 24), 1, 16));
                offers.add(sell(1, new ItemStack(Items.BOOKSHELF, 1), 12));
                offers.add(sell(1, new ItemStack(Items.EXPERIENCE_BOTTLE, 2), 12));
                for (MerchantOffer book : books(random, 2)) offers.add(book);
            }
            case SHELL_KEEPER -> {
                offers.add(buy(new ItemStack(Items.IRON_INGOT, 6), 1, 16));
                offers.add(buy(new ItemStack(Items.DIAMOND, 1), 3, 12));
                offers.add(sell(14, new ItemStack(Items.SHULKER_SHELL, 1), 4));
                offers.add(sell(30, new ItemStack(Items.SHULKER_BOX, 1), 2));
            }
            case STAR_CARTOGRAPHER -> {
                offers.add(buy(new ItemStack(Items.GLASS_PANE, 12), 1, 16));
                offers.add(sell(1, new ItemStack(Items.MAP, 1), 12));
                offers.add(sell(3, new ItemStack(Items.COMPASS, 1), 8));
                offers.add(sell(6, new ItemStack(Items.SPYGLASS, 1), 4));
                // The End City map is added when someone first trades (it has to search the world).
            }
            case DRAGON_CLERIC -> {
                offers.add(buy(new ItemStack(Items.ENDER_PEARL, 3), 1, 16));
                offers.add(sell(2, new ItemStack(Items.ENDER_PEARL, 1), 12));
                offers.add(sell(1, new ItemStack(Items.EXPERIENCE_BOTTLE, 1), 12));
                offers.add(sell(3, new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.DRAGON_BREATH, 1), 8));
                offers.add(sell(5, new ItemStack(Items.ENDER_EYE, 1), 8));
            }
        }
        return offers;
    }

    /** A map to the nearest unexplored End City, or null if none is close enough. */
    @Nullable
    public static MerchantOffer endCityMap(ServerLevel level, BlockPos from) {
        BlockPos target = level.findNearestMapStructure(ON_END_CITY_MAPS, from, 64, true);
        if (target == null) return null;
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecoration.Type.TARGET_X);
        map.setHoverName(Component.translatable("filled_map.endvillage.end_city"));
        return new MerchantOffer(new ItemStack(Items.EMERALD, 12), new ItemStack(Items.COMPASS), map, 1, 5, 0.2F);
    }

    private static List<MerchantOffer> books(RandomSource random, int count) {
        List<EnchantmentInstance> pool = new ArrayList<>(List.of(
                new EnchantmentInstance(Enchantments.FALL_PROTECTION, 4),
                new EnchantmentInstance(Enchantments.BLOCK_EFFICIENCY, 4),
                new EnchantmentInstance(Enchantments.ALL_DAMAGE_PROTECTION, 3),
                new EnchantmentInstance(Enchantments.UNBREAKING, 3),
                new EnchantmentInstance(Enchantments.SILK_TOUCH, 1),
                new EnchantmentInstance(Enchantments.SHARPNESS, 4),
                new EnchantmentInstance(Enchantments.POWER_ARROWS, 4),
                new EnchantmentInstance(Enchantments.MENDING, 1)));
        List<MerchantOffer> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            EnchantmentInstance inst = pool.remove(random.nextInt(pool.size()));
            Enchantment ench = inst.enchantment;
            int cost = Math.min(64, 6 + inst.level * 4 + random.nextInt(6) + (ench.isTreasureOnly() ? 10 : 0));
            out.add(new MerchantOffer(new ItemStack(Items.EMERALD, cost), new ItemStack(Items.BOOK),
                    EnchantedBookItem.createForEnchantment(inst), 3, 10, 0.2F));
        }
        return out;
    }

    private static MerchantOffer sell(int emeralds, ItemStack result, int uses) {
        return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), result, uses, 2, 0.05F);
    }

    private static MerchantOffer sell(int emeralds, ItemStack extra, ItemStack result, int uses) {
        return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), extra, result, uses, 2, 0.05F);
    }

    private static MerchantOffer buy(ItemStack cost, int emeralds, int uses) {
        return new MerchantOffer(cost, new ItemStack(Items.EMERALD, emeralds), uses, 2, 0.05F);
    }
}
