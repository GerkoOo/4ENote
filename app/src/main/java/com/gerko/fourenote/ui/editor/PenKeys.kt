package com.gerko.fourenote.ui.editor

import android.view.KeyEvent

/**
 * Pen buttons that arrive as key presses instead of motion-event buttons. Some styluses send
 * Android's stylus key codes; the Xiaomi Smart Pen is a Bluetooth "keyboard" sending page and
 * arrow keys. MainActivity hands every key to [dispatch]; it only takes them while a note is open.
 */
object PenKeys {
    /** Called on release with the button (BUTTON_STYLUS_*), whether it was a click, and what was received. */
    var listener: ((button: Int, click: Boolean, description: String) -> Unit)? = null

    /** The buttons held right now, so a stroke drawn while holding one uses its tool. */
    var held = 0
        private set

    // Set when the pen wrote while a button was held, so releasing it isn't also a click.
    private var usedWhileHeld = false

    fun markUsed() {
        if (held != 0) usedWhileHeld = true
    }

    /**
     * Forgets held buttons when the app loses focus (a notification, a dialog, the screen going
     * off): their release goes elsewhere, and the pen would otherwise keep that button's tool.
     */
    fun releaseAll() {
        held = 0
        usedWhileHeld = false
    }

    fun dispatch(event: KeyEvent): Boolean {
        val listener = listener ?: return false
        val button = buttonFor(event) ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                if (held == 0) usedWhileHeld = false
                held = held or button
            }
            KeyEvent.ACTION_UP -> {
                held = held and button.inv()
                val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
                listener(button, !usedWhileHeld, "Key $name from ${event.device?.name ?: "unknown device"}")
            }
        }
        return true
    }

    private fun buttonFor(event: KeyEvent): Int? = when (event.keyCode) {
        KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY -> STYLUS_PRIMARY
        KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY,
        KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY,
        KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL -> STYLUS_SECONDARY
        // Only from a pen, so a real keyboard's arrow keys keep working.
        else -> if (event.device?.name?.contains("pen", ignoreCase = true) != true) null else when (event.keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> STYLUS_PRIMARY
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_META_LEFT -> STYLUS_SECONDARY
            else -> null
        }
    }

    // MotionEvent.BUTTON_STYLUS_PRIMARY and BUTTON_STYLUS_SECONDARY.
    const val STYLUS_PRIMARY = 0x20
    const val STYLUS_SECONDARY = 0x40
}
