package com.rustscape.client.ui.components

import androidx.compose.runtime.*

/**
 * Keyboard modifier state for detecting Shift, Ctrl, Alt key states
 *
 * Used for:
 * - Shift-click to quick drop items
 * - Ctrl-click for alternative actions
 * - Alt-click for use item with
 */
class KeyboardModifierState {
    /**
     * Whether the Shift key is currently held down
     */
    var isShiftHeld by mutableStateOf(false)
        internal set

    /**
     * Whether the Ctrl key is currently held down
     */
    var isCtrlHeld by mutableStateOf(false)
        internal set

    /**
     * Whether the Alt key is currently held down
     */
    var isAltHeld by mutableStateOf(false)
        internal set

    /**
     * Update modifier state from a key event
     */
    fun updateFromKeyCode(keyCode: Int, isPressed: Boolean) {
        when (keyCode) {
            KEY_SHIFT_LEFT, KEY_SHIFT_RIGHT -> isShiftHeld = isPressed
            KEY_CTRL_LEFT, KEY_CTRL_RIGHT -> isCtrlHeld = isPressed
            KEY_ALT_LEFT, KEY_ALT_RIGHT -> isAltHeld = isPressed
        }
    }

    /**
     * Reset all modifiers (e.g., when window loses focus)
     */
    fun reset() {
        isShiftHeld = false
        isCtrlHeld = false
        isAltHeld = false
    }

    companion object {
        // Common key codes (these match most platforms)
        const val KEY_SHIFT_LEFT = 16
        const val KEY_SHIFT_RIGHT = 16
        const val KEY_CTRL_LEFT = 17
        const val KEY_CTRL_RIGHT = 17
        const val KEY_ALT_LEFT = 18
        const val KEY_ALT_RIGHT = 18
    }
}

/**
 * Shift-click action configuration
 * Determines what happens when shift-clicking an item
 */
enum class ShiftClickAction {
    /**
     * Shift-click drops the item
     */
    DROP,

    /**
     * Shift-click uses the item
     */
    USE,

    /**
     * Shift-click does nothing special
     */
    NONE,

    /**
     * Shift-click deposits to bank (when bank is open)
     */
    DEPOSIT,

    /**
     * Shift-click withdraws from bank
     */
    WITHDRAW
}

/**
 * Shift-click settings for the player
 */
class ShiftClickSettings {
    /**
     * Default action for shift-clicking inventory items
     */
    var defaultInventoryAction by mutableStateOf(ShiftClickAction.DROP)

    /**
     * Default action for shift-clicking bank items
     */
    var defaultBankAction by mutableStateOf(ShiftClickAction.WITHDRAW)

    /**
     * Whether shift-click dropping is enabled
     */
    var enableShiftDrop by mutableStateOf(true)

    /**
     * Per-item shift-click action overrides
     * Key: Item ID, Value: Action to perform
     */
    private val itemOverrides = mutableStateMapOf<Int, ShiftClickAction>()

    /**
     * Set a specific shift-click action for an item
     */
    fun setItemAction(itemId: Int, action: ShiftClickAction) {
        itemOverrides[itemId] = action
    }

    /**
     * Get the shift-click action for an item
     */
    fun getItemAction(itemId: Int, context: ShiftClickContext = ShiftClickContext.INVENTORY): ShiftClickAction {
        // Check for item-specific override first
        itemOverrides[itemId]?.let { return it }

        // Return default based on context
        return when (context) {
            ShiftClickContext.INVENTORY -> if (enableShiftDrop) defaultInventoryAction else ShiftClickAction.NONE
            ShiftClickContext.BANK -> defaultBankAction
            ShiftClickContext.EQUIPMENT -> ShiftClickAction.NONE
        }
    }

    /**
     * Clear item-specific override
     */
    fun clearItemAction(itemId: Int) {
        itemOverrides.remove(itemId)
    }

    /**
     * Clear all item-specific overrides
     */
    fun clearAllOverrides() {
        itemOverrides.clear()
    }
}

/**
 * Context for shift-click actions
 */
enum class ShiftClickContext {
    INVENTORY,
    BANK,
    EQUIPMENT
}

/**
 * Remember keyboard modifier state
 */
@Composable
fun rememberKeyboardModifierState(): KeyboardModifierState {
    return remember { KeyboardModifierState() }
}

/**
 * Remember shift-click settings
 */
@Composable
fun rememberShiftClickSettings(): ShiftClickSettings {
    return remember { ShiftClickSettings() }
}

/**
 * CompositionLocal for keyboard modifier state
 */
val LocalKeyboardModifiers = staticCompositionLocalOf<KeyboardModifierState?> { null }

/**
 * CompositionLocal for shift-click settings
 */
val LocalShiftClickSettings = staticCompositionLocalOf<ShiftClickSettings?> { null }

/**
 * Click event with modifier information
 */
data class ModifiedClickEvent(
    val slot: Int,
    val itemId: Int,
    val isShiftHeld: Boolean = false,
    val isCtrlHeld: Boolean = false,
    val isAltHeld: Boolean = false
) {
    val hasModifiers: Boolean
        get() = isShiftHeld || isCtrlHeld || isAltHeld
}

/**
 * Helper function to determine the click action based on modifiers
 */
fun getClickAction(
    modifiers: KeyboardModifierState?,
    settings: ShiftClickSettings?,
    itemId: Int,
    context: ShiftClickContext = ShiftClickContext.INVENTORY
): ShiftClickAction {
    if (modifiers == null || settings == null) {
        return ShiftClickAction.NONE
    }

    return when {
        modifiers.isShiftHeld -> settings.getItemAction(itemId, context)
        modifiers.isCtrlHeld -> ShiftClickAction.USE
        modifiers.isAltHeld -> ShiftClickAction.NONE
        else -> ShiftClickAction.NONE
    }
}
