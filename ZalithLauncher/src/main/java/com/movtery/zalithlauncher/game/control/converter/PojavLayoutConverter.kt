/*
 * Zalith Launcher 2 / Jxejdx Fork
 *
 * Конвертер раскладок из формата PojavLauncher в формат ZalithLauncher.
 *
 * Поддерживаемые форматы:
 *  - PojavLauncher (кнопки с полями: name, keycode/keycodes, x, y, width, height, visibility, toggled, passThrough)
 *  - Mojo Launcher (идентичная структура, кнопки в массиве "button")
 */

package com.movtery.zalithlauncher.game.control.converter

import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.ControlLayer
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.utils.randomUUID
import com.movtery.layer_controller.utils.getAButtonUUID
import com.movtery.zalithlauncher.game.keycodes.ControlEventKeycode
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Конвертирует JSON-строку из PojavLauncher / Mojo Launcher в ControlLayout ZalithLauncher.
 *
 * @throws IllegalArgumentException если JSON не распознан как поддерживаемый формат
 */
fun convertPojavLayoutFromString(jsonString: String): ControlLayout {
    val root = JSONObject(jsonString)

    // Определяем массив кнопок — PojavLauncher использует "mControlDataList" или "button"
    val buttons: JSONArray = when {
        root.has("mControlDataList") -> root.getJSONArray("mControlDataList")
        root.has("button")           -> root.getJSONArray("button")
        // Если корневой объект является массивом — некоторые версии хранят кнопки напрямую
        else -> throw IllegalArgumentException(
            "Неизвестный формат раскладки: не найдены ключи 'mControlDataList' или 'button'"
        )
    }

    val normalButtons = mutableListOf<NormalData>()

    for (i in 0 until buttons.length()) {
        val btn = buttons.optJSONObject(i) ?: continue

        val buttonData = convertPojavButton(btn) ?: continue
        normalButtons.add(buttonData)
    }

    // Все кнопки помещаем в один слой
    val layer = ControlLayer(
        name = "Imported",
        uuid = randomUUID(),
        hide = false,
        hideWhenMouse = true,
        hideWhenGamepad = true,
        visibilityType = VisibilityType.ALWAYS,
        normalButtons = normalButtons
    )

    // Метаданные — берём имя из JSON если есть
    val layoutName = root.optString("name", "Imported Layout").ifBlank { "Imported Layout" }

    return ControlLayout(
        editorVersion = EDITOR_VERSION,
        info = ControlLayout.Info(
            name = TranslatableString(default = layoutName),
            author = TranslatableString(default = "Imported from PojavLauncher/Mojo"),
            description = TranslatableString(default = "Автоматически конвертирована раскладка"),
            versionCode = 1,
            versionName = "1.0"
        ),
        layers = listOf(layer),
        styles = emptyList()
    )
}

/**
 * Конвертирует одну кнопку из формата PojavLauncher в NormalData ZalithLauncher.
 *
 * PojavLauncher хранит позицию в диапазоне 0..1 (float) или 0..1000 (int).
 * ZalithLauncher хранит позицию в диапазоне 0..10000.
 *
 * PojavLauncher хранит размер в dp или в процентах 0..1.
 * ZalithLauncher хранит размер в dp (absolute) или в процентах 100..10000.
 */
private fun convertPojavButton(btn: JSONObject): NormalData? {
    // Имя кнопки
    val name = btn.optString("name", "Button").ifBlank { "Button" }

    // Позиция: PojavLauncher хранит x/y как float 0.0..1.0 (доля экрана)
    // или как int (пиксели — тогда нормализовать нельзя без размера экрана, используем как есть)
    val xRaw = btn.opt("x") ?: btn.opt("mX") ?: 0.5
    val yRaw = btn.opt("y") ?: btn.opt("mY") ?: 0.5

    val xNorm = normalizePosition(xRaw)
    val yNorm = normalizePosition(yRaw)

    val position = ButtonPosition(
        x = (xNorm * 10000).roundToInt().coerceIn(0, 10000),
        y = (yNorm * 10000).roundToInt().coerceIn(0, 10000)
    )

    // Размер: PojavLauncher хранит width/height как float 0..1 (доля экрана)
    val wRaw = btn.opt("width") ?: btn.opt("mWidth") ?: 0.1
    val hRaw = btn.opt("height") ?: btn.opt("mHeight") ?: 0.1

    val wNorm = normalizePosition(wRaw)
    val hNorm = normalizePosition(hRaw)

    // Конвертируем в percentage (100 = 1%, 10000 = 100%)
    val wPercent = (wNorm * 10000).roundToInt().coerceIn(100, 10000)
    val hPercent = (hNorm * 10000).roundToInt().coerceIn(100, 10000)

    val buttonSize = ButtonSize(
        type = ButtonSize.Type.Percentage,
        widthDp = 50f,
        heightDp = 50f,
        widthPercentage = wPercent,
        heightPercentage = hPercent,
        widthReference = ButtonSize.Reference.ScreenWidth,
        heightReference = ButtonSize.Reference.ScreenHeight
    )

    // Видимость
    val visibility = when (btn.optString("visibility", "").lowercase()) {
        "in_game" -> VisibilityType.IN_GAME
        "in_menu" -> VisibilityType.IN_MENU
        else -> VisibilityType.ALWAYS
    }

    // Клавиши: PojavLauncher использует "keycodes" (массив) или "keycode" (строка/число)
    val clickEvents = mutableListOf<ClickEvent>()

    val keycodesArray: JSONArray? = btn.optJSONArray("keycodes") ?: btn.optJSONArray("mKeycodes")
    if (keycodesArray != null) {
        for (k in 0 until keycodesArray.length()) {
            val keyRaw = keycodesArray.opt(k) ?: continue
            val keyStr = convertKeycode(keyRaw)
            if (keyStr != null) {
                clickEvents.add(ClickEvent(type = ClickEvent.Type.Key, key = keyStr))
            }
        }
    } else {
        val keyRaw = btn.opt("keycode") ?: btn.opt("mKeycode")
        if (keyRaw != null) {
            val keyStr = convertKeycode(keyRaw)
            if (keyStr != null) {
                clickEvents.add(ClickEvent(type = ClickEvent.Type.Key, key = keyStr))
            }
        }
    }

    // Специальные кнопки PojavLauncher по имени
    if (clickEvents.isEmpty()) {
        val specialKey = convertSpecialButtonName(name)
        if (specialKey != null) {
            clickEvents.add(ClickEvent(type = ClickEvent.Type.Key, key = specialKey))
        }
    }

    if (clickEvents.isEmpty()) {
        lWarning("PojavConverter: кнопка '$name' не имеет клавиш, пропускается")
        // Не пропускаем — создаём кнопку без события (пользователь сам настроит)
    }

    val isToggleable = btn.optBoolean("toggled", false) ||
            btn.optBoolean("mToggled", false) ||
            btn.optBoolean("toggle", false)

    val isPenetrable = btn.optBoolean("passThrough", false) ||
            btn.optBoolean("mPassThrough", false)

    val isSwipple = btn.optBoolean("passThrough", false) ||
            btn.optBoolean("swipeable", false)

    return NormalData(
        text = TranslatableString(default = name),
        uuid = getAButtonUUID(),
        position = position,
        buttonSize = buttonSize,
        visibilityType = visibility,
        _clickEvents = clickEvents,
        isSwipple = isSwipple,
        isPenetrable = isPenetrable,
        isToggleable = isToggleable
    )
}

/**
 * Нормализует позицию/размер в диапазон 0..1.
 * PojavLauncher использует либо float 0..1, либо int (пиксели).
 * Если значение > 1 — считаем что это проценты/пиксели относительно 100 или 1000.
 */
private fun normalizePosition(raw: Any): Float {
    return when (raw) {
        is Double -> if (raw > 1.0) (raw / 100.0).toFloat().coerceIn(0f, 1f) else raw.toFloat().coerceIn(0f, 1f)
        is Float  -> if (raw > 1f) (raw / 100f).coerceIn(0f, 1f) else raw.coerceIn(0f, 1f)
        is Int    -> if (raw > 1) (raw / 100f).coerceIn(0f, 1f) else raw.toFloat().coerceIn(0f, 1f)
        is Long   -> if (raw > 1L) (raw / 100f).coerceIn(0f, 1f) else raw.toFloat().coerceIn(0f, 1f)
        is String -> raw.toFloatOrNull()?.let { normalizePosition(it) } ?: 0f
        else      -> 0f
    }
}

/**
 * Конвертирует код клавиши из формата PojavLauncher в строку GLFW.
 * PojavLauncher хранит либо числовой GLFW-код, либо строку вида "GLFW_KEY_W".
 */
private fun convertKeycode(raw: Any): String? {
    return when (raw) {
        is String -> {
            when {
                // Уже в формате GLFW — проверяем что он валиден
                raw.startsWith("GLFW_") -> {
                    if (ControlEventKeycode.getKeycodeFromEvent(raw) != null) raw else null
                }
                // Числовая строка
                else -> raw.toIntOrNull()?.let { convertGlfwInt(it) }
            }
        }
        is Int    -> convertGlfwInt(raw)
        is Long   -> convertGlfwInt(raw.toInt())
        is Double -> convertGlfwInt(raw.toInt())
        else      -> null
    }
}

/**
 * Конвертирует числовой GLFW-код в строковое имя.
 * Числа совпадают с ASCII для печатаемых символов.
 */
private fun convertGlfwInt(code: Int): String? {
    return when (code) {
        -1, 0     -> null
        32        -> "GLFW_KEY_SPACE"
        39        -> "GLFW_KEY_APOSTROPHE"
        44        -> "GLFW_KEY_COMMA"
        45        -> "GLFW_KEY_MINUS"
        46        -> "GLFW_KEY_PERIOD"
        47        -> "GLFW_KEY_SLASH"
        in 48..57 -> "GLFW_KEY_${code - 48}"
        59        -> "GLFW_KEY_SEMICOLON"
        61        -> "GLFW_KEY_EQUAL"
        in 65..90 -> "GLFW_KEY_${'A' + (code - 65)}"
        91        -> "GLFW_KEY_LEFT_BRACKET"
        92        -> "GLFW_KEY_BACKSLASH"
        93        -> "GLFW_KEY_RIGHT_BRACKET"
        96        -> "GLFW_KEY_GRAVE_ACCENT"
        256       -> "GLFW_KEY_ESCAPE"
        257       -> "GLFW_KEY_ENTER"
        258       -> "GLFW_KEY_TAB"
        259       -> "GLFW_KEY_BACKSPACE"
        260       -> "GLFW_KEY_INSERT"
        261       -> "GLFW_KEY_DELETE"
        262       -> "GLFW_KEY_RIGHT"
        263       -> "GLFW_KEY_LEFT"
        264       -> "GLFW_KEY_DOWN"
        265       -> "GLFW_KEY_UP"
        266       -> "GLFW_KEY_PAGE_UP"
        267       -> "GLFW_KEY_PAGE_DOWN"
        268       -> "GLFW_KEY_HOME"
        269       -> "GLFW_KEY_END"
        280       -> "GLFW_KEY_CAPS_LOCK"
        290       -> "GLFW_KEY_F1"
        291       -> "GLFW_KEY_F2"
        292       -> "GLFW_KEY_F3"
        293       -> "GLFW_KEY_F4"
        294       -> "GLFW_KEY_F5"
        295       -> "GLFW_KEY_F6"
        296       -> "GLFW_KEY_F7"
        297       -> "GLFW_KEY_F8"
        298       -> "GLFW_KEY_F9"
        299       -> "GLFW_KEY_F10"
        300       -> "GLFW_KEY_F11"
        301       -> "GLFW_KEY_F12"
        340       -> "GLFW_KEY_LEFT_SHIFT"
        341       -> "GLFW_KEY_LEFT_CONTROL"
        342       -> "GLFW_KEY_LEFT_ALT"
        344       -> "GLFW_KEY_RIGHT_SHIFT"
        345       -> "GLFW_KEY_RIGHT_CONTROL"
        346       -> "GLFW_KEY_RIGHT_ALT"
        // Mouse
        -100      -> "GLFW_MOUSE_BUTTON_LEFT"
        -101      -> "GLFW_MOUSE_BUTTON_RIGHT"
        -102      -> "GLFW_MOUSE_BUTTON_MIDDLE"
        else -> {
            lWarning("PojavConverter: неизвестный keycode=$code, игнорируется")
            null
        }
    }
}

/**
 * Конвертирует специальные имена кнопок PojavLauncher в клавиши GLFW.
 */
private fun convertSpecialButtonName(name: String): String? {
    return when (name.lowercase().trim()) {
        "chat", "t"          -> "GLFW_KEY_T"
        "inventory", "e"     -> "GLFW_KEY_E"
        "drop", "q"          -> "GLFW_KEY_Q"
        "sprint", "sprint_toggle" -> "GLFW_KEY_LEFT_CONTROL"
        "sneak", "shift"     -> "GLFW_KEY_LEFT_SHIFT"
        "jump", "space"      -> "GLFW_KEY_SPACE"
        "attack", "lmb"      -> "GLFW_MOUSE_BUTTON_LEFT"
        "use", "rmb"         -> "GLFW_MOUSE_BUTTON_RIGHT"
        "pick", "mmb"        -> "GLFW_MOUSE_BUTTON_MIDDLE"
        "forward", "w"       -> "GLFW_KEY_W"
        "back", "s"          -> "GLFW_KEY_S"
        "left", "a"          -> "GLFW_KEY_A"
        "right", "d"         -> "GLFW_KEY_D"
        "esc", "escape", "menu" -> "GLFW_KEY_ESCAPE"
        "enter"              -> "GLFW_KEY_ENTER"
        "tab"                -> "GLFW_KEY_TAB"
        "swap", "f"          -> "GLFW_KEY_F"
        "advancements"       -> "GLFW_KEY_L"
        "playerlist"         -> "GLFW_KEY_TAB"
        else -> null
    }
}
