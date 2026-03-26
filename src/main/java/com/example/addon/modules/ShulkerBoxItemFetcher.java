package com.example.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.BetterBlockPos;
import com.example.addon.AutoRegearAddon;
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

public class ShulkerBoxItemFetcher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSettings = settings.createGroup("Settings");
    private final IBaritone baritone;

    // Settings
    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("Target Item")
        .description("The item to fetch from the shulker box.")
        .defaultValue(Items.COBBLESTONE)
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

    // Internal state
    private enum State {
        SEARCHING_SHULKER,
        PLACING_SHULKER,
        OPENING_SHULKER,
        EXTRACTING_ITEMS,
        CLOSING_CONTAINER,
        BREAKING_SHULKER,
        PICKING_UP_SHULKER,
        FINISHED
    }

    private State currentState = State.SEARCHING_SHULKER;
    private int tickCounter = 0;
    private int shulkerSlot = -1;
    private BlockPos shulkerPos = null;
    private ItemStack shulkerBoxItem = null;
    private boolean isProcessing = false;
    private int stateTimeout = 0;
    private static final int MAX_STATE_TIMEOUT = 100; // 5 seconds at 20 TPS
    private boolean hasRotated = false; // Track if we've rotated to look at the shulker box

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

        if (logActions.get()) {
            info("Starting to fetch " + targetItem.get().getName().getString() + " from shulker box");
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

        // Check for state timeout
        stateTimeout++;
        if (stateTimeout > MAX_STATE_TIMEOUT) {
            if (logActions.get()) {
                error("State timeout: " + currentState.name() + " (waited " + stateTimeout + " ticks)");
            }
            changeState(State.FINISHED);
            return;
        }

        if (debugMode.get()) {
            info("Current state: " + currentState.name() + " (Tick: " + tickCounter + ", Timeout: " + stateTimeout + ")");
        }

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

        // Search for shulker box in inventory
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block instanceof ShulkerBoxBlock) {
                    // Check if this shulker box contains the target item
                    if (containsTargetItem(stack)) {
                        shulkerSlot = i;
                        shulkerBoxItem = stack.copy();
                        changeState(State.PLACING_SHULKER);
                        if (logActions.get()) {
                            info("Found shulker box with target item at slot: " + i);
                        }
                        return;
                    }
                }
            }
        }

        // No suitable shulker box found

        error("No shulker box containing " + targetItem.get().getName().getString() + " found");

        changeState(State.FINISHED);
    }

    private boolean containsTargetItem(ItemStack shulkerBox) {
        // Check container component for contained items (1.21+ data components)
        ContainerComponent container = shulkerBox.get(DataComponentTypes.CONTAINER);
        if (container == null) return false;

        // Check each item in the container
        for (ItemStack stack : container.stream().toList()) {
            if (!stack.isEmpty() && stack.getItem() == targetItem.get()) {
                return true;
            }
        }
        return false;
    }

    private void placeShulkerBox() {
        if (logActions.get()) info("Placing shulker box...");

        // Find a suitable position to place the shulker box
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos placePos = findSuitablePlacePosition(playerPos);

        if (placePos == null) {
            error("Could not find suitable position to place shulker box");
            changeState(State.FINISHED);
            return;
        }

        // Switch to shulker box in hotbar
        if (shulkerSlot >= 9) {
            // Move shulker box to hotbar
            moveItemToHotbar(shulkerSlot);
            return;
        }

        // Select the shulker box
        mc.player.getInventory().selectedSlot = shulkerSlot;

        // Look at the placement position
        lookAtBlock(placePos);

        // Place the shulker box
        BlockUtils.place(placePos, new FindItemResult(shulkerSlot, 1), true, 10);

        shulkerPos = placePos;
        changeState(State.OPENING_SHULKER);

        if (logActions.get()) {
            info("Shulker box placed at: " + placePos.toShortString());
        }
    }

    private BlockPos findSuitablePlacePosition(BlockPos playerPos) {
        // Get player's facing direction
        Direction playerFacing = mc.player.getHorizontalFacing();

        // Priority order: facing direction first, then adjacent sides, then diagonals
        // 1. First try the direction player is facing
        BlockPos facingPos = playerPos.offset(playerFacing);
        if (isValidPlacePosition(facingPos)) {
            if (debugMode.get()) {
                info("Found suitable position (facing direction): " + facingPos.toShortString());
            }
            return facingPos;
        }

        // 2. Try adjacent horizontal directions (not diagonal)
        Direction[] adjacentDirections = {
            playerFacing.rotateYClockwise(),
            playerFacing.rotateYCounterclockwise(),
            playerFacing.getOpposite()
        };

        for (Direction dir : adjacentDirections) {
            BlockPos testPos = playerPos.offset(dir);
            if (isValidPlacePosition(testPos)) {
                if (debugMode.get()) {
                    info("Found suitable position (adjacent): " + testPos.toShortString());
                }
                return testPos;
            }
        }

        // 3. Try above and below current position
        for (int y = 1; y >= -1; y -= 2) { // +1 then -1
            BlockPos testPos = playerPos.add(0, y, 0);
            if (isValidPlacePosition(testPos)) {
                if (debugMode.get()) {
                    info("Found suitable position (vertical): " + testPos.toShortString());
                }
                return testPos;
            }
        }

        // 4. Finally try diagonal positions if no direct adjacent positions work
        for (int distance = 1; distance <= 3; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    // Skip positions we already checked (direct adjacent)
                    if ((Math.abs(x) == 1 && z == 0) || (x == 0 && Math.abs(z) == 1) || (x == 0 && z == 0)) {
                        continue;
                    }

                    // Only check positions at the current distance boundary
                    if (Math.abs(x) == distance || Math.abs(z) == distance) {
                        for (int y = -1; y <= 1; y++) {
                            BlockPos testPos = playerPos.add(x, y, z);
                            if (isValidPlacePosition(testPos)) {
                                if (debugMode.get()) {
                                    double dist = Math.sqrt(x * x + y * y + z * z);
                                    info("Found suitable position (diagonal): " + testPos.toShortString() + " (distance: " + String.format("%.1f", dist) + ")");
                                }
                                return testPos;
                            }
                        }
                    }
                }
            }
        }

        if (logActions.get()) {
            error("No suitable placement position found within 3 blocks");
        }
        return null;
    }

    private boolean isValidPlacePosition(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() &&
            BlockUtils.canPlace(pos) &&
            !mc.world.getBlockState(pos.down()).isAir();
    }

    private void moveItemToHotbar(int sourceSlot) {
        // Find empty hotbar slot
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                // Move item to hotbar
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    sourceSlot,
                    i,
                    SlotActionType.SWAP,
                    mc.player
                );
                shulkerSlot = i;
                return;
            }
        }

        // If no empty slot, swap with first hotbar slot
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            sourceSlot,
            0,
            SlotActionType.SWAP,
            mc.player
        );
        shulkerSlot = 0;
    }

    private void openShulkerBox() {
        if (logActions.get()) info("Opening shulker box...");

        if (shulkerPos == null) {
            changeState(State.FINISHED);
            return;
        }

        // First, look at the shulker box
        if (!hasRotated) {
            lookAtBlock(shulkerPos);
            hasRotated = true;
            if (logActions.get()) info("Looking at shulker box: " + shulkerPos.toShortString());
            return; // Wait one tick for rotation to complete
        }

        // Wait a few ticks after rotation to ensure it's complete
        if (stateTimeout < 3) {
            return; // Wait 3 ticks after rotation
        }

        // Now right-click on the shulker box to open it


        // Ensure the target position is actually a shulker box
        Block block = mc.world.getBlockState(shulkerPos).getBlock();
        if (!(block instanceof ShulkerBoxBlock)) {
            warning("Target position is not a shulker box: " + shulkerPos.toShortString());
            return;
        }

        // Calculate click position
        Vec3d hitVec = Vec3d.ofCenter(shulkerPos);
        Direction side = Direction.UP; // Default to clicking from top

        // Create block hit result
        BlockHitResult hitResult = new BlockHitResult(hitVec, side, shulkerPos, false);

        // Right-click the shulker box
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        changeState(State.EXTRACTING_ITEMS);
        if (logActions.get()) info("Shulker box opened");
    }

    private void extractItems() {
        if (logActions.get()) info("Extracting items...");

        // Check if container screen is open
        if (mc.currentScreen == null) {
            if (logActions.get()) info("Waiting for container screen to open... (screen is null)");
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> containerScreen)) {
            if (logActions.get())
                info("Waiting for container screen to open... (current screen: " + mc.currentScreen.getClass().getSimpleName() + ")");
            return;
        }

        var screenHandler = containerScreen.getScreenHandler();

        if (logActions.get()) info("Container screen opened: " + containerScreen.getClass().getSimpleName());

        // Count empty slots in player inventory (excluding hotbar slot 0 which we want to keep)
        int emptySlots = countEmptyInventorySlots();
        if (emptySlots <= 1) {
            if (logActions.get()) info("Not enough inventory space, keeping only one empty slot");
            changeState(State.CLOSING_CONTAINER);
            return;
        }

        // Extract target items from container
        boolean foundItems = false;
        int containerSlots = screenHandler.slots.size() - 36; // Container slots only (excluding player inventory)

        if (logActions.get()) {
            info("Container slots: " + containerSlots + ", Total slots: " + screenHandler.slots.size());
        }

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (logActions.get() && !stack.isEmpty()) {
                info("Slot " + i + ": " + stack.getItem().getName().getString() + " x" + stack.getCount());
            }

            if (!stack.isEmpty() && stack.getItem() == targetItem.get()) {
                // Click to take the item
                mc.interactionManager.clickSlot(
                    screenHandler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                foundItems = true;

                if (logActions.get()) {
                    info("Extracted " + stack.getCount() + " " + stack.getName().getString());
                }

                // Check if we have enough space for more items
                emptySlots = countEmptyInventorySlots();
                if (emptySlots <= 1) {
                    if (logActions.get()) info("Not enough inventory space, stopping extraction");
                    break;
                }
            }
        }

        if (!foundItems) {
            if (logActions.get()) info("No more target items in shulker box");
            changeState(State.CLOSING_CONTAINER);
        }
    }

    private int countEmptyInventorySlots() {
        int count = 0;
        for (int i = 0; i < 36; i++) { // Main inventory slots only
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                count++;
            }
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

        // Check if the shulker box still exists
        if (mc.world.getBlockState(shulkerPos).isAir()) {
            if (logActions.get()) info("Shulker box no longer exists");
            changeState(State.PICKING_UP_SHULKER);
            baritone.getPathingBehavior().cancelEverything();
            return;
        }
        // Look at the shulker box before breaking
        lookAtBlock(shulkerPos);

        // 使用新的 BetterBlockPos 实例而不是 from() 方法来避免键值冲突
        // 问题：BetterBlockPos.from() 可能创建与现有 BlockPos 键冲突的对象
        // 解决：直接使用 BlockPos 坐标创建新的 BetterBlockPos 实例
        BetterBlockPos betterBlockPos = new BetterBlockPos(shulkerPos.getX(), shulkerPos.getY(), shulkerPos.getZ());
        baritone.getSelectionManager().addSelection(betterBlockPos, betterBlockPos);
        baritone.getBuilderProcess().clearArea(betterBlockPos, betterBlockPos);

        changeState(State.PICKING_UP_SHULKER);
        if (logActions.get()) info("Shulker box mining completed");

        // Use the correct packet method to break the block
//        if (stateTimeout == 0) {
//            // Send start destroy packet
//            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
//                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
//                shulkerPos,
//                Direction.UP
//            ));
//            if (debugMode.get()) info("Sending start mining packet: " + shulkerPos.toShortString());
//        } else if (stateTimeout >= 10) { // Wait a few ticks then send stop packet
//            // Send stop destroy packet
//            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
//                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
//                shulkerPos,
//                Direction.UP
//            ));
//            if (debugMode.get()) info("Sending stop mining packet");
//
//        }
        // Wait between start and stop packets
    }

    private void pickUpShulkerBox() {
        if (logActions.get()) info("Waiting to pick up shulker box...");

        // Wait a few ticks for the item to drop and be picked up automatically
        // Use stateTimeout to wait for a reasonable amount of time
        if (stateTimeout > 20) { // Wait 1 second (20 ticks)
            changeState(State.FINISHED);
            if (logActions.get()) info("Pickup wait completed");
        }
        // Otherwise stay in this state and wait
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

        baritone.getSelectionManager().removeAllSelections();
        baritone.getPathingBehavior().cancelEverything();

    }

    private void changeState(State newState) {
        if (currentState != newState) {
            if (debugMode.get()) {
                info("State change: " + currentState.name() + " -> " + newState.name());
            }
            currentState = newState;
            stateTimeout = 0; // Reset timeout when changing state
            hasRotated = false; // Reset rotation flag when changing state
        }
    }

    private void lookAtBlock(BlockPos pos) {
        if (mc.player == null || !autoRotate.get()) return;

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d direction = blockCenter.subtract(playerPos).normalize();

        float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * 180.0 / Math.PI);

//        mc.player.setYaw(yaw);
//        mc.player.setPitch(pitch);
        Rotations.rotate(yaw, pitch);
        if (debugMode.get()) {
            info("Looking at block: " + pos.toShortString() + " (yaw: " + String.format("%.1f", yaw) + ", pitch: " + String.format("%.1f", pitch) + ")");
        }
    }
}
