// src/main/java/com/example/addon/modules/AutoRegear.java
package com.example.addon.modules;

import com.example.addon.utils.rotation.Rotation;
import net.minecraft.item.ItemStack;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoRegear extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTake = settings.createGroup("Take");
    private final SettingGroup sgPlace = settings.createGroup("Place");

    // --- Internal State ---
    private final List<BlockPos> openList = Collections.synchronizedList(new ArrayList<>());
    private final int[] stealCountList = new int[15];
    private BlockPos placePos = null;
    private BlockPos openPos = null;
    private boolean opend = false;
    private boolean placeKeyPressedLastTick = false;
    private int ticksWaited = 0;
    private boolean waitingForPlaceOrOpen = false;
    private boolean waitingForOpenGui = false;
    private boolean inventoryScreenOpened = false;

    // --- General Settings ---
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("自动转向")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("在取物或超时后自动关闭模块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> disableTime = sgGeneral.add(new IntSetting.Builder()
        .name("disable-time")
        .description("在未执行任何操作前等待多长时间（以tick为单位）。")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Keybind> placeKeybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("place-keybind")
        .description("触发放发潜影盒的按键。")
        .defaultValue(Keybind.fromKey(-1))
        .build()
    );

    // --- Place Settings ---
    private final Setting<Boolean> place = sgPlace.add(new BoolSetting.Builder()
        .name("place")
        .description("自动在附近放置一个潜影盒。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("放置尝试之间的延迟（以tick为单位）。")
        .defaultValue(0)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> packetPlace = sgPlace.add(new BoolSetting.Builder()
        .name("packet-place")
        .description("使用数据包进行方块放置。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgPlace.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("放置时使用库存交换方法而非切换槽位。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preferOpen = sgPlace.add(new BoolSetting.Builder()
        .name("prefer-open")
        .description("优先打开现有的潜影盒而非放置新的。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> open = sgPlace.add(new BoolSetting.Builder()
        .name("open")
        .description("放置或找到潜影盒后将其打开。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxRange = sgPlace.add(new DoubleSetting.Builder()
        .name("max-range")
        .description("放置和打开潜影盒的最大范围。")
        .defaultValue(4.0)
        .min(0.0)
        .max(6.0)
        .sliderMax(6.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Double> minRange = sgPlace.add(new DoubleSetting.Builder()
        .name("min-range")
        .description("玩家与放置潜影盒的最小距离。")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .sliderMax(3.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> mine = sgPlace.add(new BoolSetting.Builder()
        .name("mine")
        .description("在取物后或打开失败后挖掘潜影盒。")
        .defaultValue(true)
        .build()
    );

    // --- Take Settings ---
    private final Setting<Boolean> take = sgTake.add(new BoolSetting.Builder()
        .name("take")
        .description("从打开的潜影盒中取物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartTake = sgTake.add(new BoolSetting.Builder()
        .name("smart-take")
        .description("仅根据设定的阈值取需要的物品。")
        .defaultValue(true)
        .visible(take::get)
        .build()
    );

    private final Setting<Integer> crystalThreshold = sgTake.add(new IntSetting.Builder()
        .name("crystal-threshold")
        .description("目标末影水晶数量。")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> expThreshold = sgTake.add(new IntSetting.Builder()
        .name("exp-threshold")
        .description("目标经验瓶数量。")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> totemThreshold = sgTake.add(new IntSetting.Builder()
        .name("totem-threshold")
        .description("目标不死图腾数量。")
        .defaultValue(6)
        .min(0)
        .sliderMax(36)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> gappleThreshold = sgTake.add(new IntSetting.Builder()
        .name("gapple-threshold")
        .description("目标附魔金苹果数量。")
        .defaultValue(128)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> obsidianThreshold = sgTake.add(new IntSetting.Builder()
        .name("obsidian-threshold")
        .description("目标黑曜石数量。")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> webThreshold = sgTake.add(new IntSetting.Builder()
        .name("web-threshold")
        .description("目标蜘蛛网数量。")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> glowstoneThreshold = sgTake.add(new IntSetting.Builder()
        .name("glowstone-threshold")
        .description("目标萤石块数量。")
        .defaultValue(128)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> anchorThreshold = sgTake.add(new IntSetting.Builder()
        .name("anchor-threshold")
        .description("目标重生锚数量。")
        .defaultValue(128)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> pearlThreshold = sgTake.add(new IntSetting.Builder()
        .name("pearl-threshold")
        .description("目标末影珍珠数量。")
        .defaultValue(16)
        .min(0)
        .sliderMax(64)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> pistonThreshold = sgTake.add(new IntSetting.Builder()
        .name("piston-threshold")
        .description("目标活塞和粘性活塞数量。")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> redstoneThreshold = sgTake.add(new IntSetting.Builder()
        .name("redstone-threshold")
        .description("目标红石块数量。")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> bedThreshold = sgTake.add(new IntSetting.Builder()
        .name("bed-threshold")
        .description("目标床数量。")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> speedPotionThreshold = sgTake.add(new IntSetting.Builder()
        .name("speed-potion-threshold")
        .description("目标速度药水数量。")
        .defaultValue(1)
        .min(0)
        .sliderMax(8)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> resistancePotionThreshold = sgTake.add(new IntSetting.Builder()
        .name("resistance-potion-threshold")
        .description("目标抗性药水数量。")
        .defaultValue(1)
        .min(0)
        .sliderMax(8)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> strengthPotionThreshold = sgTake.add(new IntSetting.Builder()
        .name("strength-potion-threshold")
        .description("目标力量药水数量。")
        .defaultValue(1)
        .min(0)
        .sliderMax(8)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );


    public AutoRegear() {
        super(Categories.Combat, "auto-regear", "自动放置和打开潜影盒以取回装备。");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (mine.get() && placePos != null) {
            // Attempt to mine the placed shulker box when disabling
            // (Logic remains unchanged, actual mining requires more complex simulation)
        }
        resetState();
    }

    private void resetState() {
        opend = false;
        placePos = null;
        openPos = null;
        openList.clear();
        ticksWaited = 0;
        waitingForPlaceOrOpen = false;
        waitingForOpenGui = false;
        inventoryScreenOpened = false;
        // 初始化 Rotation 的原始角度
        if (mc.player != null) {
            Rotation.rotationYaw = mc.player.getYaw();
            Rotation.rotationPitch = mc.player.getPitch();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (waitingForOpenGui) {
            if (inventoryScreenOpened) {
                inventoryScreenOpened = false;
                waitingForOpenGui = false;
                opend = true;
            }
            return;
        }

        if (!waitingForPlaceOrOpen) {
            doPlace();
        }

        // Update timeout counter
        if (!opend && ticksWaited++ > disableTime.get() && autoDisable.get()) {
            toggle();
            return;
        }
    }

    private void doPlace() {
        waitingForPlaceOrOpen = true;

        FindItemResult shulkerResult = InvUtils.find(itemStack -> itemStack.getItem() instanceof BlockItem && ((BlockItem) itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock);

        if (!shulkerResult.found()) {
            waitingForPlaceOrOpen = false;
            return;
        }

        BlockPos placePos = null;
        boolean foundToOpen = false;

        int rangeInt = maxRange.get().intValue();
        BlockPos playerPos = mc.player.getBlockPos();

        outerLoop:
        for (int x = -rangeInt; x <= rangeInt; x++) {
            for (int y = -rangeInt; y <= rangeInt; y++) {
                for (int z = -rangeInt; z <= rangeInt; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (pos.getSquaredDistance(playerPos) > (double) (rangeInt * rangeInt)) {
                        continue;
                    }

                    if (preferOpen.get() && mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                        if (openList.contains(pos)) continue;
                        if (canOpenShulker(pos)) {
                            openPos = pos;
                            if (rotate.get()) {
                                // 使用 Rotation 进行转向
                                Vec3d targetLookPos = Vec3d.ofCenter(openPos);
                                Rotation.snapAt(targetLookPos);
                            }
                            BlockHitResult hitResult = new BlockHitResult(
                                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                Direction.UP,
                                pos,
                                false
                            );
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                            waitingForOpenGui = true;
                            foundToOpen = true;
                            x = rangeInt + 1;
                            y = rangeInt + 1;
                            z = rangeInt + 1;
                            break outerLoop;
                        }
                        continue;
                    }

                    if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) < minRange.get()) continue;
                    if (!BlockUtils.canPlace(pos, true)) continue;
                    if (BlockUtils.getPlaceSide(pos) == null) continue;
                    if (canOpenShulkerAfterPlace(pos, BlockUtils.getPlaceSide(pos))) {
                        placePos = pos;
                        x = rangeInt + 1;
                        y = rangeInt + 1;
                        z = rangeInt + 1;
                        break outerLoop;
                    }
                }
            }
        }

        if (foundToOpen) {
            return;
        } else if (placePos != null) {
            this.placePos = placePos;

            Direction placeSide = BlockUtils.getPlaceSide(this.placePos);
            if (placeSide == null) {
                waitingForPlaceOrOpen = false;
                return;
            }

            if (rotate.get()) {
                // 使用 Rotation 进行转向
                Vec3d targetLookPos = Vec3d.ofCenter(this.placePos.offset(placeSide.getOpposite()));
                Rotation.snapAt(targetLookPos);
            }

            Vec3d hitPos = Vec3d.ofCenter(this.placePos.offset(placeSide.getOpposite()));
            BlockHitResult hitResult = new BlockHitResult(hitPos, placeSide, this.placePos, false);

            FindItemResult shulkerResultForPlacement = InvUtils.find(itemStack -> itemStack.getItem() instanceof BlockItem && ((BlockItem) itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock);
            if (shulkerResultForPlacement.found()) {
                int prevSlot = mc.player.getInventory().selectedSlot;

                if (inventorySwap.get()) {
                    InvUtils.swap(shulkerResultForPlacement.slot(), true);
                } else {
                    mc.player.getInventory().selectedSlot = shulkerResultForPlacement.slot();
                }

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

                if (inventorySwap.get()) {
                    InvUtils.swap(prevSlot, true);
                } else {
                    mc.player.getInventory().selectedSlot = prevSlot;
                }
            }

            waitingForPlaceOrOpen = false;
            ticksWaited = 0;

        } else {
            waitingForPlaceOrOpen = false;
        }
    }

    private void updateStealCounts() {
        stealCountList[0] = (int) (crystalThreshold.get() - mc.player.getInventory().count(Items.END_CRYSTAL));
        stealCountList[1] = (int) (expThreshold.get() - mc.player.getInventory().count(Items.EXPERIENCE_BOTTLE));
        stealCountList[2] = (int) (totemThreshold.get() - mc.player.getInventory().count(Items.TOTEM_OF_UNDYING));
        stealCountList[3] = (int) (gappleThreshold.get() - mc.player.getInventory().count(Items.ENCHANTED_GOLDEN_APPLE));
        stealCountList[4] = (int) (obsidianThreshold.get() - mc.player.getInventory().count(Blocks.OBSIDIAN.asItem()));
        stealCountList[5] = (int) (webThreshold.get() - mc.player.getInventory().count(Blocks.COBWEB.asItem()));
        stealCountList[6] = (int) (glowstoneThreshold.get() - mc.player.getInventory().count(Blocks.GLOWSTONE.asItem()));
        stealCountList[7] = (int) (anchorThreshold.get() - mc.player.getInventory().count(Blocks.RESPAWN_ANCHOR.asItem()));
        stealCountList[8] = (int) (pearlThreshold.get() - mc.player.getInventory().count(Items.ENDER_PEARL));
        stealCountList[9] = (int) (pistonThreshold.get() - (mc.player.getInventory().count(Blocks.PISTON.asItem()) + mc.player.getInventory().count(Blocks.STICKY_PISTON.asItem())));
        stealCountList[10] = (int) (redstoneThreshold.get() - mc.player.getInventory().count(Blocks.REDSTONE_BLOCK.asItem()));
        stealCountList[11] = (int) (bedThreshold.get() - (int) mc.player.getInventory().main.stream()
            .filter(stack -> stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BedBlock)
            .mapToInt(ItemStack::getCount)
            .sum()
        );
        stealCountList[12] = (int) (speedPotionThreshold.get() - getPotionCount(StatusEffects.SPEED));
        stealCountList[13] = (int) (resistancePotionThreshold.get() - getPotionCount(StatusEffects.RESISTANCE));
        stealCountList[14] = (int) (strengthPotionThreshold.get() - getPotionCount(StatusEffects.STRENGTH));
    }

    private int getPotionCount(RegistryEntry<StatusEffect> effectEntry) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.contains(DataComponentTypes.POTION_CONTENTS)) {
                var potionComponent = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (potionComponent != null) {
                    for (StatusEffectInstance instance : potionComponent.getEffects()) {
                        if (instance.getEffectType() == effectEntry) {
                            count += stack.getCount();
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }


    private boolean canOpenShulkerAfterPlace(BlockPos pos, Direction placeSide) {
        Direction facingDir = placeSide.getOpposite();
        BlockPos front = pos.offset(facingDir);
        return mc.world.getBlockState(front).getCollisionShape(mc.world, front).isEmpty();
    }

    private boolean canOpenShulker(BlockPos pos) {
        if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
            return false;
        }
        Direction facingDir = mc.world.getBlockState(pos).get(ShulkerBoxBlock.FACING);
        BlockPos front = pos.offset(facingDir);
        return mc.world.getBlockState(front).getCollisionShape(mc.world, front).isEmpty();
    }

    private boolean needSteal(net.minecraft.item.ItemStack stack) {
        if (!smartTake.get()) return true;

        if (stack.getItem() == Items.END_CRYSTAL && stealCountList[0] > 0) {
            stealCountList[0] = Math.max(0, stealCountList[0] - stack.getCount());
            return true;
        }
        if (stack.getItem() == Items.EXPERIENCE_BOTTLE && stealCountList[1] > 0) {
            stealCountList[1] = Math.max(0, stealCountList[1] - stack.getCount());
            return true;
        }
        if (stack.getItem() == Items.TOTEM_OF_UNDYING && stealCountList[2] > 0) {
            stealCountList[2] = Math.max(0, stealCountList[2] - stack.getCount());
            return true;
        }
        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && stealCountList[3] > 0) {
            stealCountList[3] = Math.max(0, stealCountList[3] - stack.getCount());
            return true;
        }
        if (stack.getItem().equals(Blocks.OBSIDIAN.asItem()) && stealCountList[4] > 0) {
            stealCountList[4] = Math.max(0, stealCountList[4] - stack.getCount());
            return true;
        }
        if (stack.getItem().equals(Blocks.COBWEB.asItem()) && stealCountList[5] > 0) {
            stealCountList[5] = Math.max(0, stealCountList[5] - stack.getCount());
            return true;
        }
        if (stack.getItem().equals(Blocks.GLOWSTONE.asItem()) && stealCountList[6] > 0) {
            stealCountList[6] = Math.max(0, stealCountList[6] - stack.getCount());
            return true;
        }
        if (stack.getItem().equals(Blocks.RESPAWN_ANCHOR.asItem()) && stealCountList[7] > 0) {
            stealCountList[7] = Math.max(0, stealCountList[7] - stack.getCount());
            return true;
        }
        if (stack.getItem() == Items.ENDER_PEARL && stealCountList[8] > 0) {
            stealCountList[8] = Math.max(0, stealCountList[8] - stack.getCount());
            return true;
        }
        if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof PistonBlock) {
            if (stealCountList[9] > 0) {
                stealCountList[9] = Math.max(0, stealCountList[9] - stack.getCount());
                return true;
            }
        }
        if (stack.getItem().equals(Blocks.REDSTONE_BLOCK.asItem()) && stealCountList[10] > 0) {
            stealCountList[10] = Math.max(0, stealCountList[10] - stack.getCount());
            return true;
        }
        if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof BedBlock) {
            if (stealCountList[11] > 0) {
                stealCountList[11] = Math.max(0, stealCountList[11] - stack.getCount());
                return true;
            }
        }
        if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potionContents != null) {
                for (StatusEffectInstance effect : potionContents.getEffects()) {
                    if (effect.getEffectType() == StatusEffects.SPEED) {
                        if (stealCountList[12] > 0) {
                            stealCountList[12] = Math.max(0, stealCountList[12] - stack.getCount());
                            return true;
                        }
                    } else if (effect.getEffectType() == StatusEffects.RESISTANCE) {
                        if (stealCountList[13] > 0) {
                            stealCountList[13] = Math.max(0, stealCountList[13] - stack.getCount());
                            return true;
                        }
                    } else if (effect.getEffectType() == StatusEffects.STRENGTH) {
                        if (stealCountList[14] > 0) {
                            stealCountList[14] = Math.max(0, stealCountList[14] - stack.getCount());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private int ticksPassed() {
        return ticksWaited++;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (take.get() && (event.screen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen)) {
            event.cancel();
            inventoryScreenOpened = true;
        }
    }
}
