/*
 * Zalith Launcher 2 / Jxejdx Fork
 *
 * Конвертер раскладок из формата PojavLauncher (новый формат с dynamicX/dynamicY) в ZalithLauncher.
 *
 * Особенности формата PojavLauncher:
 *  - width/height — размер кнопки в dp
 *  - dynamicX/dynamicY — строки-выражения с переменными:
 *      ${screen_width}, ${screen_height}, ${margin}, ${right}, ${bottom},
 *      ${width}, ${height}, ${preferred_scale}, px(N)
 *  - Парсер вычисляет позицию левого верхнего угла на условном экране 1000×562 dp,
 *    затем конвертирует в % для ZalithLauncher (0..10000)
 */

package com.movtery.zalithlauncher.game.control.converter

import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.VisibilityType
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

// EDITOR_VERSION = 11
private const val CURRENT_EDITOR_VERSION = 11

// Референсный экран 16:9, 1000 dp ширина
private const val REF_W = 1000f
private const val REF_H = 562f
private const val MARGIN = 4f   // стандартный margin PojavLauncher

private fun newUUID(): String = UUID.randomUUID().toString().replace("-", "").take(18)
private fun newLayerUUID(): String = UUID.randomUUID().toString().replace("-", "").take(12)

/**
 * Конвертирует JSON-строку из PojavLauncher в ControlLayout ZalithLauncher.
 */
fun convertPojavLayoutFromString(jsonString: String): ControlLayout {
    val root = JSONObject(jsonString)

    val buttons: JSONArray = when {
        root.has("mControlDataList") -> root.getJSONArray("mControlDataList")
        root.has("button")           -> root.getJSONArray("button")
        else -> throw IllegalArgumentException(
            "Неизвестный формат: не найдены ключи 'mControlDataList' или 'button'"
        )
    }

    val normalButtons = mutableListOf<NormalData>()
    for (i in 0 until buttons.length()) {
        val btn = buttons.optJSONObject(i) ?: continue
        convertPojavButton(btn)?.let { normalButtons.add(it) }
    }

    // Кнопки из mDrawerDataList тоже добавляем (суб-кнопки drawer)
    val drawers = root.optJSONArray("mDrawerDataList")
    if (drawers != null) {
        for (i in 0 until drawers.length()) {
            val drawer = drawers.optJSONObject(i) ?: continue
            // Сама кнопка drawer
            drawer.optJSONObject("properties")?.let { props ->
                convertPojavButton(props)?.let { normalButtons.add(it) }
            }
            // Вложенные кнопки
            val bProps = drawer.optJSONArray("buttonProperties")
            if (bProps != null) {
                for (j in 0 until bProps.length()) {
                    val sub = bProps.optJSONObject(j) ?: continue
                    convertPojavButton(sub)?.let { normalButtons.add(it) }
                }
            }
        }
    }

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
            author = createTranslatable(default = "Imported from PojavLauncher"),
            description = createTranslatable(default = "Автоматически конвертировано"),
            versionCode = 1,
            versionName = "1.0"
        ),
        layers = listOf(layer),
        styles = emptyList()
    )
}

/**
 * Конвертирует одну кнопку PojavLauncher → NormalData.
 */
private fun convertPojavButton(btn: JSONObject): NormalData? {
    val name = btn.optString("name", "Button").ifBlank { "Button" }

    val wDp = btn.optDouble("width",  50.0).toFloat().coerceAtLeast(5f)
    val hDp = btn.optDouble("height", 50.0).toFloat().coerceAtLeast(5f)

    val dynamicX = btn.optString("dynamicX", "")
    val dynamicY = btn.optString("dynamicY", "")

    // Вычисляем X и Y левого верхнего угла в dp на референсном экране
    val xLeft = evalDynamic(dynamicX, wDp, hDp)
    val yTop  = evalDynamic(dynamicY, wDp, hDp)

    // ZalithLauncher хранит позицию центра кнопки
    val cxNorm = ((xLeft + wDp / 2f) / REF_W).coerceIn(0f, 1f)
    val cyNorm = ((yTop  + hDp / 2f) / REF_H).coerceIn(0f, 1f)

    val position = ButtonPosition(
        x = (cxNorm * 10000).roundToInt().coerceIn(0, 10000),
        y = (cyNorm * 10000).roundToInt().coerceIn(0, 10000)
    )

    // Размер: dp → проценты (width относительно ширины, height относительно высоты)
    val wPct = (wDp / REF_W * 10000).roundToInt().coerceIn(100, 10000)
    val hPct = (hDp / REF_H * 10000).roundToInt().coerceIn(100, 10000)

    val buttonSize = ButtonSize(
        type = ButtonSize.Type.Percentage,
        widthDp = wDp,
        heightDp = hDp,
        widthPercentage  = wPct,
        heightPercentage = hPct,
        widthReference   = ButtonSize.Reference.ScreenWidth,
        heightReference  = ButtonSize.Reference.ScreenHeight
    )

    val displayInGame = btn.optBoolean("displayInGame", true)
    val displayInMenu = btn.optBoolean("displayInMenu", false)
    val visibility = when {
        displayInGame && displayInMenu -> VisibilityType.ALWAYS
        displayInGame  -> VisibilityType.IN_GAME
        displayInMenu  -> VisibilityType.IN_MENU
        else           -> VisibilityType.ALWAYS
    }

    val clickEvents = mutableListOf<ClickEvent>()
    val keycodes = btn.optJSONArray("keycodes")
    if (keycodes != null) {
        for (k in 0 until keycodes.length()) {
            val raw = when (val v = keycodes.opt(k)) {
                is Int    -> v
                is Long   -> v.toInt()
                is Double -> v.toInt()
                is String -> v.toIntOrNull() ?: continue
                else      -> continue
            }
            if (raw == 0) continue
            val key = convertPojavKeycode(raw) ?: continue
            clickEvents.add(ClickEvent(type = ClickEvent.Type.Key, key = key))
        }
    }

    return NormalData(
        text = createTranslatable(default = name),
        uuid = newUUID(),
        position = position,
        buttonSize = buttonSize,
        visibilityType = visibility,
        _clickEvents = clickEvents,
        isSwipple    = btn.optBoolean("isSwipeable", false),
        isPenetrable = btn.optBoolean("passThruEnabled", false),
        isToggleable = btn.optBoolean("isToggle", false)
    )
}

/**
 * Вычисляет позицию (dp) из строки dynamicX/dynamicY PojavLauncher.
 *
 * Заменяет все переменные на числа и вычисляет арифметику.
 * preferred_scale = 100 (стандарт), margin = 4dp, экран 1000×562dp.
 */
private fun evalDynamic(expr: String, wDp: Float, hDp: Float): Float {
    if (expr.isBlank()) return 0f
    var s = expr.trim()

    // px(N) / 100.0 * ${preferred_scale}  →  N
    s = Regex("""px\(([\d.]+)\)\s*/\s*100\.0\s*\*\s*\$\{preferred_scale\}""")
        .replace(s) { it.groupValues[1] }

    // px(N) — без деления (редкий случай) → N
    s = Regex("""px\(([\d.]+)\)""")
        .replace(s) { it.groupValues[1] }

    s = s.replace("\${screen_width}",  REF_W.toString())
    s = s.replace("\${screen_height}", REF_H.toString())
    s = s.replace("\${margin}",        MARGIN.toString())
    s = s.replace("\${right}",         (REF_W - wDp).toString())
    s = s.replace("\${bottom}",        (REF_H - hDp).toString())
    s = s.replace("\${width}",         wDp.toString())
    s = s.replace("\${height}",        hDp.toString())

    return try {
        ArithParser(s.replace(" ", "")).parse()
    } catch (e: Exception) {
        lWarning("PojavConverter: не удалось вычислить '$expr': ${e.message}")
        0f
    }
}

/** Простой рекурсивный парсер арифметики: +, -, *, /, скобки. */
private class ArithParser(private val s: String) {
    private var i = 0
    fun parse() = parseExpr()
    private fun parseExpr(): Float {
        var r = parseTerm()
        while (i < s.length) {
            when (s[i]) {
                '+' -> { i++; r += parseTerm() }
                '-' -> { i++; r -= parseTerm() }
                else -> break
            }
        }
        return r
    }
    private fun parseTerm(): Float {
        var r = parseFactor()
        while (i < s.length) {
            when (s[i]) {
                '*' -> { i++; r *= parseFactor() }
                '/' -> { i++; val d = parseFactor(); r = if (d != 0f) r / d else 0f }
                else -> break
            }
        }
        return r
    }
    private fun parseFactor(): Float {
        if (i < s.length && s[i] == '(') { i++; val r = parseExpr(); if (i < s.length && s[i] == ')') i++; return r }
        if (i < s.length && s[i] == '-') { i++; return -parseFactor() }
        return parseNum()
    }
    private fun parseNum(): Float {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        return if (i == start) 0f else s.substring(start, i).toFloatOrNull() ?: 0f
    }
}

/**
 * Коды PojavLauncher → GLFW строки.
 * Отрицательные — специальные коды PojavLauncher.
 */
private fun convertPojavKeycode(code: Int): String? = when (code) {
    -1   -> null  // Keyboard toggle
    -2   -> null  // GUI toggle
    -3   -> "GLFW_MOUSE_BUTTON_LEFT"
    -4   -> "GLFW_MOUSE_BUTTON_RIGHT"
    -5   -> null  // Mouse mode toggle
    -6   -> "GLFW_MOUSE_BUTTON_MIDDLE"
    -7   -> null  // Scroll up
    -8   -> null  // Scroll down
    -9   -> null  // Launcher menu
    else -> convertGlfwInt(code)
}

/** Числовой GLFW-код → строка "GLFW_KEY_*". */
private fun convertGlfwInt(code: Int): String? = when (code) {
    32  -> "GLFW_KEY_SPACE"
    39  -> "GLFW_KEY_APOSTROPHE"
    44  -> "GLFW_KEY_COMMA"
    45  -> "GLFW_KEY_MINUS"
    46  -> "GLFW_KEY_PERIOD"
    47  -> "GLFW_KEY_SLASH"
    48  -> "GLFW_KEY_0"
    49  -> "GLFW_KEY_1"
    50  -> "GLFW_KEY_2"
    51  -> "GLFW_KEY_3"
    52  -> "GLFW_KEY_4"
    53  -> "GLFW_KEY_5"
    54  -> "GLFW_KEY_6"
    55  -> "GLFW_KEY_7"
    56  -> "GLFW_KEY_8"
    57  -> "GLFW_KEY_9"
    59  -> "GLFW_KEY_SEMICOLON"
    61  -> "GLFW_KEY_EQUAL"
    65  -> "GLFW_KEY_A"
    66  -> "GLFW_KEY_B"
    67  -> "GLFW_KEY_C"
    68  -> "GLFW_KEY_D"
    69  -> "GLFW_KEY_E"
    70  -> "GLFW_KEY_F"
    71  -> "GLFW_KEY_G"
    72  -> "GLFW_KEY_H"
    73  -> "GLFW_KEY_I"
    74  -> "GLFW_KEY_J"
    75  -> "GLFW_KEY_K"
    76  -> "GLFW_KEY_L"
    77  -> "GLFW_KEY_M"
    78  -> "GLFW_KEY_N"
    79  -> "GLFW_KEY_O"
    80  -> "GLFW_KEY_P"
    81  -> "GLFW_KEY_Q"
    82  -> "GLFW_KEY_R"
    83  -> "GLFW_KEY_S"
    84  -> "GLFW_KEY_T"
    85  -> "GLFW_KEY_U"
    86  -> "GLFW_KEY_V"
    87  -> "GLFW_KEY_W"
    88  -> "GLFW_KEY_X"
    89  -> "GLFW_KEY_Y"
    90  -> "GLFW_KEY_Z"
    91  -> "GLFW_KEY_LEFT_BRACKET"
    92  -> "GLFW_KEY_BACKSLASH"
    93  -> "GLFW_KEY_RIGHT_BRACKET"
    96  -> "GLFW_KEY_GRAVE_ACCENT"
    256 -> "GLFW_KEY_ESCAPE"
    257 -> "GLFW_KEY_ENTER"
    258 -> "GLFW_KEY_TAB"
    259 -> "GLFW_KEY_BACKSPACE"
    260 -> "GLFW_KEY_INSERT"
    261 -> "GLFW_KEY_DELETE"
    262 -> "GLFW_KEY_RIGHT"
    263 -> "GLFW_KEY_LEFT"
    264 -> "GLFW_KEY_DOWN"
    265 -> "GLFW_KEY_UP"
    266 -> "GLFW_KEY_PAGE_UP"
    267 -> "GLFW_KEY_PAGE_DOWN"
    268 -> "GLFW_KEY_HOME"
    269 -> "GLFW_KEY_END"
    280 -> "GLFW_KEY_CAPS_LOCK"
    290 -> "GLFW_KEY_F1"
    291 -> "GLFW_KEY_F2"
    292 -> "GLFW_KEY_F3"
    293 -> "GLFW_KEY_F4"
    294 -> "GLFW_KEY_F5"
    295 -> "GLFW_KEY_F6"
    296 -> "GLFW_KEY_F7"
    297 -> "GLFW_KEY_F8"
    298 -> "GLFW_KEY_F9"
    299 -> "GLFW_KEY_F10"
    300 -> "GLFW_KEY_F11"
    301 -> "GLFW_KEY_F12"
    340 -> "GLFW_KEY_LEFT_SHIFT"
    341 -> "GLFW_KEY_LEFT_CONTROL"
    342 -> "GLFW_KEY_LEFT_ALT"
    344 -> "GLFW_KEY_RIGHT_SHIFT"
    345 -> "GLFW_KEY_RIGHT_CONTROL"
    346 -> "GLFW_KEY_RIGHT_ALT"
    else -> null
}
