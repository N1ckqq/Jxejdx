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

import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.ControlLayer
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.data.NormalData
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

// EDITOR_VERSION = 11 — используем напрямую, чтобы не зависеть от internal
private const val CURRENT_EDITOR_VERSION = 11

private fun newUUID(): String = UUID.randomUUID().toString().replace("-", "").take(18)
private fun newLayerUUID(): String = UUID.randomUUID().toString().replace("-", "").take(12)

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
        uuid = newLayerUUID(),
        hide = false,
        hideWhenMouse = true,
        hideWhenGamepad = true,
        visibilityType = VisibilityType.ALWAYS,
        normalButtons = normalButtons
    )

    val layoutName = root.optString("name", "Imported Layout").ifBlank { "Imported Layout" }

    return ControlLayout(
        editorVersion = CURRENT_EDITOR_VERSION,
        info = ControlLayout.Info(
            name = createTranslatable(default = layoutName),
            author = createTranslatable(default = "Imported from PojavLauncher/Mojo"),
            description = createTranslatable(default = "Автоматически конвертирована раскладка"),
            versionCode = 1,
            versionName = "1.0"
        ),
        layers = listOf(layer),
        styles = emptyList()
    )
}

/**
 * Конвертирует одну кнопку из формата PojavLauncher в NormalData.
 */
private fun convertPojavButton(btn: JSONObject): NormalData? {
    val name = btn.optString("name", "Button").ifBlank { "Button" }

    val xRaw = btn.opt("x") ?: btn.opt("mX") ?: 0.5
    val yRaw = btn.opt("y") ?: btn.opt("mY") ?: 0.5

    val xNorm = normalizePosition(xRaw)
    val yNorm = normalizePosition(yRaw)

    val wRaw = btn.opt("width") ?: btn.opt("mWidth") ?: 0.1
    val hRaw = btn.opt("height") ?: btn.opt("mHeight") ?: 0.1

    val wNorm = normalizePosition(wRaw)
    val hNorm = normalizePosition(hRaw)

    // PojavLauncher: x/y — левый верхний угол кнопки.
    // ZalithLauncher: position — центр кнопки.
    // Сдвигаем на половину размера, чтобы кнопки встали на правильные места.
    // Размеры у PojavLauncher: width — % от ширины экрана, height — % от высоты.
    // Для центра: cx = x + width/2, cy = y + height/2
    val cxNorm = (xNorm + wNorm / 2f).coerceIn(0f, 1f)
    val cyNorm = (yNorm + hNorm / 2f).coerceIn(0f, 1f)

    val position = ButtonPosition(
        x = (cxNorm * 10000).roundToInt().coerceIn(0, 10000),
        y = (cyNorm * 10000).roundToInt().coerceIn(0, 10000)
    )

    // ButtonSize: percentage 100 = 1%, 10000 = 100%
    val wPercent = (wNorm * 10000).roundToInt().coerceIn(100, 10000)
    val hPercent = (hNorm * 10000).roundToInt().coerceIn(100, 10000)

    val buttonSize = ButtonSize(
        type = ButtonSize.Type.Percentage,
        widthDp = 50f,
        heightDp = 50f,
        widthPercentage = wPercent,
        heightPercentage = hPercent,
        widthReference = ButtonSize.Reference.ScreenWidth,   // ширина относительно ширины экрана
        heightReference = ButtonSize.Reference.ScreenHeight  // высота относительно высоты экрана
    )

    val visibility = when (btn.optString("visibility", "").lowercase()) {
        "in_game" -> VisibilityType.IN_GAME
        "in_menu" -> VisibilityType.IN_MENU
        else -> VisibilityType.ALWAYS
    }

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

    if (clickEvents.isEmpty()) {
        val specialKey = convertSpecialButtonName(name)
        if (specialKey != null) {
            clickEvents.add(ClickEvent(type = ClickEvent.Type.Key, key = specialKey))
        }
    }

    if (clickEvents.isEmpty()) {
        lWarning("PojavConverter: кнопка '$name' без клавиш")
    }

    val isToggleable = btn.optBoolean("toggled", false) ||
            btn.optBoolean("mToggled", false) ||
            btn.optBoolean("toggle", false)

    val isPenetrable = btn.optBoolean("passThrough", false) ||
            btn.optBoolean("mPassThrough", false)

    val isSwipple = btn.optBoolean("swipeable", false)

    return NormalData(
        text = createTranslatable(default = name),
        uuid = newUUID(),
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
 * Нормализует значение позиции/размера в диапазон 0..1.
 *
 * PojavLauncher хранит x/y/width/height как проценты от экрана: 0.0 .. 100.0
 * Пример: x=85.5 означает 85.5% ширины экрана.
 *
 * Некоторые старые версии хранят как доли 0.0..1.0 — определяем по значению.
 */
private fun normalizePosition(raw: Any): Float {
    val v = when (raw) {
        is Double -> raw.toFloat()
        is Float  -> raw
        is Int    -> raw.toFloat()
        is Long   -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: 0f
        else      -> 0f
    }
    // Если значение > 1 — это проценты (0..100), делим на 100
    // Если значение <= 1 — уже доля (0..1), оставляем как есть
    return if (v > 1f) (v / 100f).coerceIn(0f, 1f) else v.coerceIn(0f, 1f)
}

/**
 * Конвертирует код клавиши: число или строка GLFW → строка GLFW.
 */
private fun convertKeycode(raw: Any): String? {
    return when (raw) {
        is String -> {
            when {
                raw.startsWith("GLFW_") -> raw
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
 * Числовой GLFW-код → строка вида "GLFW_KEY_*".
 */
private fun convertGlfwInt(code: Int): String? = when (code) {
    -1, 0     -> null
    32        -> "GLFW_KEY_SPACE"
    39        -> "GLFW_KEY_APOSTROPHE"
    44        -> "GLFW_KEY_COMMA"
    45        -> "GLFW_KEY_MINUS"
    46        -> "GLFW_KEY_PERIOD"
    47        -> "GLFW_KEY_SLASH"
    48        -> "GLFW_KEY_0"
    49        -> "GLFW_KEY_1"
    50        -> "GLFW_KEY_2"
    51        -> "GLFW_KEY_3"
    52        -> "GLFW_KEY_4"
    53        -> "GLFW_KEY_5"
    54        -> "GLFW_KEY_6"
    55        -> "GLFW_KEY_7"
    56        -> "GLFW_KEY_8"
    57        -> "GLFW_KEY_9"
    59        -> "GLFW_KEY_SEMICOLON"
    61        -> "GLFW_KEY_EQUAL"
    65        -> "GLFW_KEY_A"
    66        -> "GLFW_KEY_B"
    67        -> "GLFW_KEY_C"
    68        -> "GLFW_KEY_D"
    69        -> "GLFW_KEY_E"
    70        -> "GLFW_KEY_F"
    71        -> "GLFW_KEY_G"
    72        -> "GLFW_KEY_H"
    73        -> "GLFW_KEY_I"
    74        -> "GLFW_KEY_J"
    75        -> "GLFW_KEY_K"
    76        -> "GLFW_KEY_L"
    77        -> "GLFW_KEY_M"
    78        -> "GLFW_KEY_N"
    79        -> "GLFW_KEY_O"
    80        -> "GLFW_KEY_P"
    81        -> "GLFW_KEY_Q"
    82        -> "GLFW_KEY_R"
    83        -> "GLFW_KEY_S"
    84        -> "GLFW_KEY_T"
    85        -> "GLFW_KEY_U"
    86        -> "GLFW_KEY_V"
    87        -> "GLFW_KEY_W"
    88        -> "GLFW_KEY_X"
    89        -> "GLFW_KEY_Y"
    90        -> "GLFW_KEY_Z"
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
    -100      -> "GLFW_MOUSE_BUTTON_LEFT"
    -101      -> "GLFW_MOUSE_BUTTON_RIGHT"
    -102      -> "GLFW_MOUSE_BUTTON_MIDDLE"
    else      -> null
}

/**
 * Специальные имена кнопок PojavLauncher → клавиши GLFW.
 */
private fun convertSpecialButtonName(name: String): String? = when (name.lowercase().trim()) {
    "chat", "t"               -> "GLFW_KEY_T"
    "inventory", "e"          -> "GLFW_KEY_E"
    "drop", "q"               -> "GLFW_KEY_Q"
    "sprint", "sprint_toggle" -> "GLFW_KEY_LEFT_CONTROL"
    "sneak", "shift"          -> "GLFW_KEY_LEFT_SHIFT"
    "jump", "space"           -> "GLFW_KEY_SPACE"
    "attack", "lmb"           -> "GLFW_MOUSE_BUTTON_LEFT"
    "use", "rmb"              -> "GLFW_MOUSE_BUTTON_RIGHT"
    "pick", "mmb"             -> "GLFW_MOUSE_BUTTON_MIDDLE"
    "forward", "w"            -> "GLFW_KEY_W"
    "back", "s"               -> "GLFW_KEY_S"
    "left", "a"               -> "GLFW_KEY_A"
    "right", "d"              -> "GLFW_KEY_D"
    "esc", "escape", "menu"   -> "GLFW_KEY_ESCAPE"
    "enter"                   -> "GLFW_KEY_ENTER"
    "tab"                     -> "GLFW_KEY_TAB"
    "swap", "f"               -> "GLFW_KEY_F"
    "advancements"            -> "GLFW_KEY_L"
    else                      -> null
}
