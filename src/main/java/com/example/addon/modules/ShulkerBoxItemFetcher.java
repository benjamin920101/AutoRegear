package com.example.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.BetterBlockPos;
import com.example.addon.AutoRegearAddon;
import com.example.addon.utils.ItemStackData;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.NopPathManager;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.OptionalInt;

public class ShulkerBoxItemFetcher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSettings = settings.createGroup("Settings");
    private final SettingGroup sgTake = settings.createGroup("Smart Take");
    private final IBaritone baritone;

    private final Setting<Boolean> take = sgTake.add(new BoolSetting.Builder()
        .name("take")
        .description("Enable smart item taking with thresholds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartTake = sgTake.add(new BoolSetting.Builder()
        .name("smart-take")
        .description("Use smart taking with item-specific thresholds.")
        .defaultValue(true)
        .visible(() -> take.get())
        .build()
    );

    private final Setting<Integer> crystalThreshold = sgTake.add(new IntSetting.Builder()
        .name("crystal-threshold")
        .description("Target end crystal count.")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> expThreshold = sgTake.add(new IntSetting.Builder()
        .name("exp-threshold")
        .description("Target XP bottle count.")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> totemThreshold = sgTake.add(new IntSetting.Builder()
        .name("totem-threshold")
        .description("Target totem count.")
        .defaultValue(6)
        .min(0)
        .sliderMax(36)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> goldenAppleThreshold = sgTake.add(new IntSetting.Builder()
        .name("golden-apple-threshold")
        .description("Target golden apple count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> splashPotionThreshold = sgTake.add(new IntSetting.Builder()
        .name("splash-potion-threshold")
        .description("Target splash potion count.")
        .defaultValue(1)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> fireworkRocketThreshold = sgTake.add(new IntSetting.Builder()
        .name("firework-rocket-threshold")
        .description("Target firework rocket count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> obsidianThreshold = sgTake.add(new IntSetting.Builder()
        .name("obsidian-threshold")
        .description("Target obsidian count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> webThreshold = sgTake.add(new IntSetting.Builder()
        .name("web-threshold")
        .description("Target cobweb count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> glowstoneThreshold = sgTake.add(new IntSetting.Builder()
        .name("glowstone-threshold")
        .description("Target glowstone count.")
        .defaultValue(128)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> anchorThreshold = sgTake.add(new IntSetting.Builder()
        .name("anchor-threshold")
        .description("Target respawn anchor count.")
        .defaultValue(128)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> pearlThreshold = sgTake.add(new IntSetting.Builder()
        .name("pearl-threshold")
        .description("Target ender pearl count.")
        .defaultValue(16)
        .min(0)
        .sliderMax(64)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> pistonThreshold = sgTake.add(new IntSetting.Builder()
        .name("piston-threshold")
        .description("Target piston and sticky piston count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> redstoneThreshold = sgTake.add(new IntSetting.Builder()
        .name("redstone-threshold")
        .description("Target redstone block count.")
        .defaultValue(64)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> bedThreshold = sgTake.add(new IntSetting.Builder()
        .name("bed-threshold")
        .description("Target bed count.")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .visible(() -> take.get() && smartTake.get())
        .build()
    );

    private final Setting<Integer> delay = sgSettings.add(new IntSetting.Builder()
        .name("Delay")
        .description("Delay between operations (ticks).")
        .defaultValue(2)
        .min(1)
        .max(20)
        .build()
    );

    private final Setting<Boolean> autoClose = sgSettings.add(new BoolSetting.Builder()
        .name("Auto Close")
        .description("Automatically close the module when finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logActions = sgSettings.add(new BoolSetting.Builder()
        .name("Log Actions")
        .description("Log actions to the chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugMode = sgSettings.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Enable verbose debug logging.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoRotate = sgSettings.add(new BoolSetting.Builder()
        .name("Auto Rotate")
        .description("Automatically rotate to look at the block when interacting.")
        .defaultValue(true)
        .build()
    );

    private enum State {
        SEARCHING_SHULKER, PLACING_SHULKER, OPENING_SHULKER, EXTRACTING_ITEMS,
        CLOSING_CONTAINER, BREAKING_SHULKER, PICKING_UP_SHULKER, FINISHED
    }

    private State currentState = State.SEARCHING_SHULKER;
    private int tickCounter = 0;
    private int shulkerSlot = -1;
    private BlockPos shulkerPos = null;
    private ItemStack shulkerBoxItem = null;
    private boolean isProcessing = false;
    private int stateTimeout = 0;
    private static final int MAX_STATE_TIMEOUT = 100;
    private boolean hasRotated = false;

    public ShulkerBoxItemFetcher() {
        super(AutoRegearAddon.CATEGORY, "shulker-box-item-fetcher", "Automatically fetch items from shulker boxes. Places, opens, extracts, breaks, and picks up the shulker box.");
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            if (logActions.get()) error("Player or world is null!");
            toggle();
            return;
        }

        if (PathManagers.get() instanceof NopPathManager) {
            info("Baritone pathfinding is required");
            toggle();
            return;
        }


        currentState = State.SEARCHING_SHULKER;
        tickCounter = 0;
        shulkerSlot = -1;
        shulkerPos = null;
        shulkerBoxItem = null;
        isProcessing = false;
        stateTimeout = 0;
        hasRotated = false;
        currentTargetItem = null;

        if (logActions.get()) {
            info("Starting smart item fetch from shulker box");
        }
    }

    @Override
    public void onDeactivate() {
        if (logActions.get()) {
            info("Shulker Box Item Fetcher deactivated");
        }
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        if (isProcessing) return;

        stateTimeout++;
        if (stateTimeout > MAX_STATE_TIMEOUT) {
            if (logActions.get()) error("State timeout: " + currentState.name() + " (" + stateTimeout + " ticks)");
            changeState(State.FINISHED);
            return;
        }

        if (debugMode.get()) info("State: " + currentState.name() + " (Timeout: " + stateTimeout + ")");

        switch (currentState) {
            case SEARCHING_SHULKER -> searchForShulkerBox();
            case PLACING_SHULKER -> placeShulkerBox();
            case OPENING_SHULKER -> openShulkerBox();
            case EXTRACTING_ITEMS -> extractItems();
            case CLOSING_CONTAINER -> closeContainer();
            case BREAKING_SHULKER -> breakShulkerBox();
            case PICKING_UP_SHULKER -> pickUpShulkerBox();
            case FINISHED -> finishProcess();
        }
    }

    private void searchForShulkerBox() {
        if (logActions.get()) info("Searching for shulker box in inventory...");

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block instanceof ShulkerBoxBlock) {
                    if (debugMode.get()) debugItemStack(stack, i);

                    Item neededItem = findNeededItemInShulker(stack);
                    if (neededItem != null) {
                        shulkerSlot = i;
                        shulkerBoxItem = stack.copy();
                        currentTargetItem = neededItem;
                        changeState(State.PLACING_SHULKER);
                        if (logActions.get()) info("Found shulker box with " + neededItem.getName().getString() + " at slot: " + i);
                        return;
                    }
                }
            }
        }

        error("No shulker box containing needed items found");
        changeState(State.FINISHED);
    }

    private Item currentTargetItem = null;

    private Item findNeededItemInShulker(ItemStack shulkerBox) {
        ContainerComponent container = shulkerBox.get(DataComponentTypes.CONTAINER);
        if (container == null) return null;

        Item neededItem = null;
        int highestPriority = -1;

        for (ItemStack stack : container.stream().toList()) {
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                int threshold = getTargetThreshold(item);

                if (threshold == Integer.MAX_VALUE) {
                    if (debugMode.get()) info("[DEBUG] Skipping " + item.getName().getString() + " (no threshold)");
                    continue;
                }

                int currentCount = countTargetItemInInventory(item);
                int neededCount = Math.max(0, threshold - currentCount);

                if (debugMode.get()) {
                    info("[DEBUG] " + item.getName().getString() + " | Have: " + currentCount + " | Threshold: " + threshold + " | Need: " + neededCount);
                }

                if (neededCount > 0) {
                    int priority = threshold;
                    if (neededItem == null || priority < highestPriority) {
                        neededItem = item;
                        highestPriority = priority;
                        if (debugMode.get()) info("[DEBUG] Found needed item: " + item.getName().getString() + " (priority: " + priority + ")");
                    }
                }
            }
        }

        if (neededItem != null && debugMode.get()) info("[DEBUG] Selected: " + neededItem.getName().getString());

        return neededItem;
    }

    private void placeShulkerBox() {
        if (logActions.get()) info("Placing shulker box...");

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos placePos = findSuitablePlacePosition(playerPos);

        if (placePos == null) {
            error("Could not find suitable position to place shulker box");
            changeState(State.FINISHED);
            return;
        }

        if (shulkerSlot >= 9) {
            moveItemToHotbar(shulkerSlot);
            return;
        }

        mc.player.getInventory().selectedSlot = shulkerSlot;
        lookAtBlock(placePos);
        BlockUtils.place(placePos, new FindItemResult(shulkerSlot, 1), true, 10);

        shulkerPos = placePos;
        changeState(State.OPENING_SHULKER);

        if (logActions.get()) info("Shulker box placed at: " + placePos.toShortString());
    }

    private BlockPos findSuitablePlacePosition(BlockPos playerPos) {
        Direction playerFacing = mc.player.getHorizontalFacing();

        BlockPos facingPos = playerPos.offset(playerFacing);
        if (isValidPlacePosition(facingPos)) {
            if (debugMode.get()) info("Found suitable position (facing): " + facingPos.toShortString());
            return facingPos;
        }

        Direction[] adjacentDirections = {
            playerFacing.rotateYClockwise(),
            playerFacing.rotateYCounterclockwise(),
            playerFacing.getOpposite()
        };

        for (Direction dir : adjacentDirections) {
            BlockPos testPos = playerPos.offset(dir);
            if (isValidPlacePosition(testPos)) {
                if (debugMode.get()) info("Found suitable position (adjacent): " + testPos.toShortString());
                return testPos;
            }
        }

        for (int y = 1; y >= -1; y -= 2) {
            BlockPos testPos = playerPos.add(0, y, 0);
            if (isValidPlacePosition(testPos)) {
                if (debugMode.get()) info("Found suitable position (vertical): " + testPos.toShortString());
                return testPos;
            }
        }

        for (int distance = 1; distance <= 3; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    if ((Math.abs(x) == 1 && z == 0) || (x == 0 && Math.abs(z) == 1) || (x == 0 && z == 0)) continue;

                    if (Math.abs(x) == distance || Math.abs(z) == distance) {
                        for (int y = -1; y <= 1; y++) {
                            BlockPos testPos = playerPos.add(x, y, z);
                            if (isValidPlacePosition(testPos)) {
                                if (debugMode.get()) {
                                    double dist = Math.sqrt(x * x + y * y + z * z);
                                    info("Found suitable position (diagonal): " + testPos.toShortString() + " (dist: " + String.format("%.1f", dist) + ")");
                                }
                                return testPos;
                            }
                        }
                    }
                }
            }
        }

        if (logActions.get()) error("No suitable placement position found within 3 blocks");
        return null;
    }

    private boolean isValidPlacePosition(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() &&
            BlockUtils.canPlace(pos) &&
            !mc.world.getBlockState(pos.down()).isAir();
    }

    private void moveItemToHotbar(int sourceSlot) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, sourceSlot, i, SlotActionType.SWAP, mc.player);
                shulkerSlot = i;
                return;
            }
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, sourceSlot, 0, SlotActionType.SWAP, mc.player);
        shulkerSlot = 0;
    }

    private void openShulkerBox() {
        if (logActions.get()) info("Opening shulker box...");

        if (shulkerPos == null) {
            changeState(State.FINISHED);
            return;
        }

        if (!hasRotated) {
            lookAtBlock(shulkerPos);
            hasRotated = true;
            if (logActions.get()) info("Looking at shulker box: " + shulkerPos.toShortString());
            return;
        }

        if (stateTimeout < 3) return;

        Block block = mc.world.getBlockState(shulkerPos).getBlock();
        if (!(block instanceof ShulkerBoxBlock)) {
            warning("Target position is not a shulker box: " + shulkerPos.toShortString());
            return;
        }

        Vec3d hitVec = Vec3d.ofCenter(shulkerPos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, shulkerPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        changeState(State.EXTRACTING_ITEMS);
        if (logActions.get()) info("Shulker box opened");
    }

    private void extractItems() {
        if (logActions.get()) info("Extracting items...");

        if (mc.currentScreen == null) {
            if (logActions.get()) info("Waiting for container screen to open...");
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> containerScreen)) {
            if (logActions.get()) info("Waiting for container screen... (" + mc.currentScreen.getClass().getSimpleName() + ")");
            return;
        }

        var screenHandler = containerScreen.getScreenHandler();
        if (logActions.get()) info("Container screen opened: " + containerScreen.getClass().getSimpleName());

        Item itemToExtract = currentTargetItem;
        if (itemToExtract == null) {
            error("No target item selected");
            changeState(State.CLOSING_CONTAINER);
            return;
        }

        int targetThreshold = getTargetThreshold(itemToExtract);
        int maxStack = itemToExtract.getMaxCount();
        int currentCount = countTargetItemInInventory(itemToExtract);
        int neededCount = Math.max(0, targetThreshold - currentCount);

        if (logActions.get() && take.get() && smartTake.get()) {
            info("Target: " + itemToExtract.getName().getString() + " | Have: " + currentCount + " | Need: " + neededCount + " | Threshold: " + targetThreshold);
        }

        int emptySlots = countEmptyInventorySlots();
        if (emptySlots <= 1) {
            if (logActions.get()) info("Not enough inventory space");
            changeState(State.CLOSING_CONTAINER);
            return;
        }

        int canTakeCount = emptySlots * maxStack;
        int maxTakeCount = Math.min(canTakeCount, neededCount);

        if (take.get() && smartTake.get() && neededCount <= 0) {
            if (logActions.get()) info("Already have enough items");
            changeState(State.CLOSING_CONTAINER);
            return;
        }

        boolean foundItems = false;
        int containerSlots = screenHandler.slots.size() - 36;
        int totalExtracted = 0;

        if (logActions.get()) info("Container slots: " + containerSlots);

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (logActions.get() && !stack.isEmpty()) {
                info("Slot " + i + ": " + stack.getItem().getName().getString() + " x" + stack.getCount());
            }
            if (debugMode.get() && !stack.isEmpty()) debugItemStack(stack, i);

            if (!stack.isEmpty() && stack.getItem() == itemToExtract) {
                int wouldBeTotal = totalExtracted + stack.getCount();

                if (take.get() && smartTake.get() && totalExtracted >= maxTakeCount) {
                    if (logActions.get()) info("Reached target threshold");
                    break;
                }

                if (take.get() && smartTake.get() && wouldBeTotal > maxTakeCount) {
                    if (logActions.get()) info("Skipping slot " + i + ": " + stack.getItem().getName().getString() + " x" + stack.getCount() + " (would exceed threshold: " + wouldBeTotal + " > " + maxTakeCount + ")");
                    continue;
                }

                mc.interactionManager.clickSlot(screenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                foundItems = true;
                totalExtracted += stack.getCount();

                if (logActions.get()) info("Extracted " + stack.getCount() + " " + stack.getName().getString() + " (Total: " + totalExtracted + "/" + maxTakeCount + ")");

                emptySlots = countEmptyInventorySlots();
                if (emptySlots <= 1) {
                    if (logActions.get()) info("Not enough inventory space");
                    break;
                }

                currentCount = countTargetItemInInventory(itemToExtract);
                neededCount = Math.max(0, targetThreshold - currentCount);
                if (take.get() && smartTake.get() && neededCount <= 0) {
                    if (logActions.get()) info("Reached target threshold for " + itemToExtract.getName().getString());
                    break;
                }
            }
        }

        if (!foundItems) {
            if (logActions.get()) info("No more target items in shulker box");
        }

        if (take.get() && smartTake.get() && shulkerBoxItem != null) {
            Item nextNeededItem = findNeededItemInShulker(shulkerBoxItem);
            if (nextNeededItem != null && nextNeededItem != currentTargetItem) {
                if (logActions.get()) info("Found another needed item: " + nextNeededItem.getName().getString() + ", continuing extraction");
                currentTargetItem = nextNeededItem;
                foundItems = true;
            } else {
                if (logActions.get()) info("No more needed items in this shulker box");
                changeState(State.CLOSING_CONTAINER);
            }
        } else {
            changeState(State.CLOSING_CONTAINER);
        }
    }

    private int getTargetThreshold(Item item) {
        if (!take.get() || !smartTake.get()) return Integer.MAX_VALUE;

        if (item == Items.END_CRYSTAL) return crystalThreshold.get();
        if (item == Items.EXPERIENCE_BOTTLE) return expThreshold.get();
        if (item == Items.TOTEM_OF_UNDYING) return totemThreshold.get();
        if (item == Items.GOLDEN_APPLE) return goldenAppleThreshold.get();
        if (item == Items.SPLASH_POTION) return splashPotionThreshold.get();
        if (item == Items.FIREWORK_ROCKET) return fireworkRocketThreshold.get();
        if (item == Items.OBSIDIAN) return obsidianThreshold.get();
        if (item == Items.COBWEB) return webThreshold.get();
        if (item == Items.GLOWSTONE) return glowstoneThreshold.get();
        if (item == Items.RESPAWN_ANCHOR) return anchorThreshold.get();
        if (item == Items.ENDER_PEARL) return pearlThreshold.get();
        if (item == Items.PISTON || item == Items.STICKY_PISTON) return pistonThreshold.get();
        if (item == Items.REDSTONE_BLOCK) return redstoneThreshold.get();
        if (item == Items.RED_BED) return bedThreshold.get();

        return Integer.MAX_VALUE;
    }

    private int countTargetItemInInventory(Item item) {
        int count = 0;
        if (mc.player == null) return 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private void debugItemStack(ItemStack stack, int slot) {
        if (stack.isEmpty()) return;

        info("[DEBUG] Slot " + slot + ": " + stack.getItem().getName().getString() + " x" + stack.getCount() + " | MaxCount: " + stack.getMaxCount());

        // 偵測玩家物品欄 NBT 數據
        var inventory = mc.player.getInventory();
        var result = new ArrayList<ItemStackData>();
        int selectedSlot = inventory.selectedSlot;
        for (int i = 0; i < inventory.size(); i++) {
            var itemStack = inventory.getStack(i);
            if (itemStack.getCount() > 0) {
                result.add(ItemStackData.of(itemStack, OptionalInt.of(i), i == selectedSlot));
            }
        }
        info("[DEBUG] Player Inventory: " + result.size() + " items");
        for (ItemStackData data : result) {
            info("[DEBUG]   " + data);
        }

        if (stack.contains(DataComponentTypes.DAMAGE)) {
            info("[DEBUG] Damage: " + stack.get(DataComponentTypes.DAMAGE));
        }

        var components = stack.getComponents();
        if (!components.isEmpty()) info("[DEBUG] Components count: " + components.size());

        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            info("[DEBUG] CustomName: " + stack.get(DataComponentTypes.CUSTOM_NAME));
        }
        if (stack.contains(DataComponentTypes.LORE)) {
            info("[DEBUG] Lore: " + stack.get(DataComponentTypes.LORE));
        }
        if (stack.contains(DataComponentTypes.ENCHANTMENTS)) {
            info("[DEBUG] Enchantments: " + stack.get(DataComponentTypes.ENCHANTMENTS));
        }
        if (stack.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                int containerSize = container.stream().mapToInt(ItemStack::getCount).sum();
                info("[DEBUG] Container items (total count): " + containerSize);
                container.stream().forEach((itemStack) -> {
                    if (!itemStack.isEmpty()) {
                        info("[DEBUG]   - " + itemStack.getItem().getName().getString() + " x" + itemStack.getCount());
                    }
                });
            }
        }
        if (stack.contains(DataComponentTypes.POTION_CONTENTS)) {
            info("[DEBUG] Potion: " + stack.get(DataComponentTypes.POTION_CONTENTS));
        }
        if (stack.contains(DataComponentTypes.FOOD)) {
            info("[DEBUG] Food: " + stack.get(DataComponentTypes.FOOD));
        }
        if (stack.contains(DataComponentTypes.UNBREAKABLE)) {
            info("[DEBUG] Unbreakable: " + stack.get(DataComponentTypes.UNBREAKABLE));
        }
        if (stack.contains(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP)) {
            info("[DEBUG] Hide tooltip: " + stack.get(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP));
        }
    }

    private int countEmptyInventorySlots() {
        int count = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    private void closeContainer() {
        if (logActions.get()) info("Closing container...");

        if (mc.currentScreen != null) {
            mc.currentScreen.close();
        }

        changeState(State.BREAKING_SHULKER);
    }

    private void breakShulkerBox() {
        if (logActions.get()) info("Breaking shulker box...");

        if (shulkerPos == null) {
            changeState(State.FINISHED);
            return;
        }

        if (mc.world.getBlockState(shulkerPos).isAir()) {
            if (logActions.get()) info("Shulker box no longer exists");
            changeState(State.PICKING_UP_SHULKER);
            baritone.getPathingBehavior().cancelEverything();
            return;
        }

        lookAtBlock(shulkerPos);

        BetterBlockPos betterBlockPos = new BetterBlockPos(shulkerPos.getX(), shulkerPos.getY(), shulkerPos.getZ());
        baritone.getSelectionManager().addSelection(betterBlockPos, betterBlockPos);
        baritone.getBuilderProcess().clearArea(betterBlockPos, betterBlockPos);

        changeState(State.PICKING_UP_SHULKER);
        if (logActions.get()) info("Shulker box mining completed");
    }

    private void pickUpShulkerBox() {
        if (logActions.get()) info("Waiting to pick up shulker box...");

        if (stateTimeout > 20) {
            changeState(State.FINISHED);
            if (logActions.get()) info("Pickup wait completed");
        }
    }

    private void finishProcess() {
        if (logActions.get()) {
            info("Item extraction completed!");
        }

        if (autoClose.get()) {
            toggle();
        } else {
            resetState();
        }
    }

    private void resetState() {
        currentState = State.SEARCHING_SHULKER;
        tickCounter = 0;
        shulkerSlot = -1;
        shulkerPos = null;
        shulkerBoxItem = null;
        isProcessing = false;
        stateTimeout = 0;
        hasRotated = false;
        currentTargetItem = null;

        baritone.getSelectionManager().removeAllSelections();
        baritone.getPathingBehavior().cancelEverything();
    }

    private void changeState(State newState) {
        if (currentState != newState) {
            if (debugMode.get()) info("State change: " + currentState.name() + " -> " + newState.name());
            currentState = newState;
            stateTimeout = 0;
            hasRotated = false;
        }
    }

    private void lookAtBlock(BlockPos pos) {
        if (mc.player == null || !autoRotate.get()) return;

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d direction = blockCenter.subtract(playerPos).normalize();

        float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * 180.0 / Math.PI);

        Rotations.rotate(yaw, pitch);
        if (debugMode.get()) {
            info("Looking at block: " + pos.toShortString() + " (yaw: " + String.format("%.1f", yaw) + ", pitch: " + String.format("%.1f", pitch) + ")");
        }
    }
}
