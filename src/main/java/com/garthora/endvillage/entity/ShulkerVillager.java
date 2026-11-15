package com.garthora.endvillage.entity;

import com.garthora.endvillage.EndVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A peaceful shulker that lives in End Villages and trades like a villager.
 * Its shell color shows its job.
 */
public class ShulkerVillager extends Shulker implements Merchant {
    private static final long RESTOCK_TICKS = 12000L; // half a Minecraft day
    private static final double TELEPORT_RANGE = 4.0;  // never jumps further than this

    @Nullable private EndProfession profession;
    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;
    private long lastRestock = -1L;
    private boolean triedMap;
    private int idlePeek;
    private int peekTimer;
    private int teleportTimer = 200;

    public ShulkerVillager(EntityType<? extends ShulkerVillager> type, Level level) {
        super(type, level);
    }

    // ---- behaviour -------------------------------------------------------

    @Override
    protected void registerGoals() {
        // No attacking: just look at nearby players.
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.02F, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (tradingPlayer != null && (!tradingPlayer.isAlive()
                || tradingPlayer.distanceToSqr(this) > 64.0
                || !(tradingPlayer.containerMenu instanceof MerchantMenu))) {
            setTradingPlayer(null);
        }

        ensureOffers();

        // Wander like an enderman: every 10-30 seconds, blink to a nearby spot.
        // Stays put while trading or when a player is standing close by.
        if (tradingPlayer == null && --teleportTimer <= 0) {
            teleportTimer = 200 + random.nextInt(400);
            if (level().getNearestPlayer(this, 4.0) == null) {
                blinkNearby();
            }
        }

        if (tradingPlayer == null && this.tickCount % 100 == 0) {
            restockIfDue();
        }

        // Open fully while trading, peek at nearby players, otherwise peek now and then.
        int target;
        if (tradingPlayer != null) {
            target = 100;
        } else if (level().getNearestPlayer(this, 5.0) != null) {
            target = 40;
        } else {
            if (--peekTimer <= 0) {
                idlePeek = random.nextInt(3) == 0 ? 30 : 0;
                peekTimer = 60 + random.nextInt(100);
            }
            target = idlePeek;
        }
        if (this.entityData.get(DATA_PEEK_ID) != (byte) target) {
            this.entityData.set(DATA_PEEK_ID, (byte) target);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.getItem() instanceof SpawnEggItem
                || !this.isAlive() || this.tradingPlayer != null) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide) {
            ensureOffers();
            if (!triedMap && getProfession() == EndProfession.STAR_CARTOGRAPHER && level() instanceof ServerLevel sl) {
                triedMap = true;
                MerchantOffer map = EndTrades.endCityMap(sl, this.blockPosition());
                if (map != null) offers.add(map);
            }
            restockIfDue();
            if (offers.isEmpty()) return InteractionResult.CONSUME;
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Teleports to a random free spot with solid ground, at most 4 blocks away. */
    private boolean blinkNearby() {
        BlockPos here = this.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos to = here.offset(random.nextInt(9) - 4, random.nextInt(5) - 2, random.nextInt(9) - 4);
            if (to.equals(here) || Math.sqrt(to.distSqr(here)) > TELEPORT_RANGE) continue;
            BlockPos below = to.below();
            if (!level().getBlockState(below).isFaceSturdy(level(), below, Direction.UP)) continue;
            if (!level().getFluidState(to).isEmpty()) continue;
            if (!level().noCollision(this, this.getType().getAABB(to.getX() + 0.5, to.getY(), to.getZ() + 0.5))) continue;

            Vec3 from = this.position();
            this.entityData.set(DATA_ATTACH_FACE_ID, Direction.DOWN);
            this.entityData.set(DATA_PEEK_ID, (byte) 0);
            this.setPos(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
            level().gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(this));
            level().broadcastEntityEvent(this, (byte) 46); // purple portal particles, like an enderman
            this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    private void ensureOffers() {
        if (offers == null && !level().isClientSide) {
            EndProfession p = getProfession();
            if (p == null) {
                p = EndProfession.random(random);
                setProfession(p);
            }
            offers = EndTrades.create(p, random);
            lastRestock = level().getGameTime();
        }
    }

    private void restockIfDue() {
        if (offers == null) return;
        long now = level().getGameTime();
        if (lastRestock < 0 || now - lastRestock >= RESTOCK_TICKS || now < lastRestock) {
            for (MerchantOffer offer : offers) offer.resetUses();
            lastRestock = now;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Stray shulker bullets would otherwise clone this into a hostile shulker.
        Entity direct = source.getDirectEntity();
        if (direct instanceof ShulkerBullet) return false;
        if (tradingPlayer != null && source.getEntity() == tradingPlayer) setTradingPlayer(null);
        Vec3 before = this.position();
        Direction faceBefore = this.getAttachFace();
        boolean result = super.hurt(source, amount);
        keepJumpShort(before, faceBefore);
        return result;
    }

    @Override
    public void tick() {
        Vec3 before = this.position();
        Direction faceBefore = this.getAttachFace();
        super.tick();
        if (!level().isClientSide) keepJumpShort(before, faceBefore);
    }

    /** Vanilla shulkers can jump up to 8 blocks; swap any long jump for a short one. */
    private void keepJumpShort(Vec3 before, Direction faceBefore) {
        if (level().isClientSide || !this.isAlive()) return;
        if (this.position().distanceTo(before) <= TELEPORT_RANGE + 0.01) return;
        Vec3 landed = this.position();
        Direction faceLanded = this.getAttachFace();
        this.entityData.set(DATA_ATTACH_FACE_ID, faceBefore);
        this.setPos(before.x, before.y, before.z);
        if (!blinkNearby()) {
            // Nowhere close to go (e.g. its floor was broken): keep the vanilla spot.
            this.entityData.set(DATA_ATTACH_FACE_ID, faceLanded);
            this.setPos(landed.x, landed.y, landed.z);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ---- job / looks ---------------------------------------------------

    @Nullable
    public EndProfession getProfession() {
        if (profession != null) return profession;
        return EndProfession.byColor(getColor()); // the client only knows the color
    }

    public void setProfession(EndProfession p) {
        this.profession = p;
        this.setVariant(Optional.of(p.color()));
    }

    @Override
    protected Component getTypeName() {
        EndProfession p = getProfession();
        if (p == null) return super.getTypeName();
        return Component.translatable("entity." + EndVillageMod.MODID + "." + p.id());
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (profession == null) setProfession(EndProfession.random(random));
        else setProfession(profession);
        setPersistenceRequired();
        return result;
    }

    // ---- saving --------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (profession != null) tag.putString("Profession", profession.id());
        if (offers != null && !offers.isEmpty()) tag.put("Offers", offers.createTag());
        tag.putLong("LastRestock", lastRestock);
        tag.putBoolean("TriedMap", triedMap);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Profession", Tag.TAG_STRING)) {
            EndProfession p = EndProfession.byId(tag.getString("Profession"));
            if (p != null) setProfession(p);
        }
        if (tag.contains("Offers", Tag.TAG_COMPOUND)) offers = new MerchantOffers(tag.getCompound("Offers"));
        if (tag.contains("LastRestock")) lastRestock = tag.getLong("LastRestock");
        triedMap = tag.getBoolean("TriedMap");
    }

    // ---- Merchant ------------------------------------------------------

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        ensureOffers();
        if (offers == null) offers = new MerchantOffers();
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        this.offers = newOffers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        if (offer.shouldRewardExp() && level() instanceof ServerLevel sl) {
            int xp = 3 + this.random.nextInt(4);
            sl.addFreshEntity(new ExperienceOrb(sl, getX(), getY() + 0.5, getZ(), xp));
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!level().isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.playSound(SoundEvents.SHULKER_AMBIENT, getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.SHULKER_OPEN;
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }
}
