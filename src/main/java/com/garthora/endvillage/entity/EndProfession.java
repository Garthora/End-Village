package com.garthora.endvillage.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;

/** What a Shulker Villager does for a living. Each job has its own shell color. */
public enum EndProfession {
    CHORUS_FARMER("chorus_farmer", DyeColor.LIME),
    PURPUR_MASON("purpur_mason", DyeColor.LIGHT_GRAY),
    VOID_LIBRARIAN("void_librarian", DyeColor.WHITE),
    SHELL_KEEPER("shell_keeper", DyeColor.BLACK),
    STAR_CARTOGRAPHER("star_cartographer", DyeColor.LIGHT_BLUE),
    DRAGON_CLERIC("dragon_cleric", DyeColor.YELLOW);

    private final String id;
    private final DyeColor color;

    EndProfession(String id, DyeColor color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public DyeColor color() {
        return color;
    }

    @Nullable
    public static EndProfession byId(String id) {
        for (EndProfession p : values()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    @Nullable
    public static EndProfession byColor(@Nullable DyeColor color) {
        for (EndProfession p : values()) {
            if (p.color == color) return p;
        }
        return null;
    }

    public static EndProfession random(RandomSource random) {
        EndProfession[] all = values();
        return all[random.nextInt(all.length)];
    }
}
