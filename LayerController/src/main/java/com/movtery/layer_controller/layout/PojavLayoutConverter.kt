/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.layer_controller.layout

import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.TextAlignment
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.updateLayoutToNew
import com.movtery.layer_controller.utils.getAButtonUUID
import com.movtery.layer_controller.utils.randomUUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Маппинг числовых GLFW-кодов клавиш (из старого PoJav-формата)
 * в строковые имена, используемые в новом формате Zalith Launcher 2.
 *
 * Источник числовых значений: LwjglGlfwKeycode.java
 */
private val GLFW_CODE_TO_NAME: Map<Int, String> = mapOf(
    // Printable keys
    32  to "GLFW_KEY_SPACE",
    39  to "GLFW_KEY_APOSTROPHE",
    44  to "GLFW_KEY_COMMA",
    45  to "GLFW_KEY_MINUS",
    46  to "GLFW_KEY_PERIOD",
    47  to "GLFW_KEY_SLASH",
    48  to "GLFW_KEY_0",
    49  to "GLFW_KEY_1",
    50  to "GLFW_KEY_2",
    51  to "GLFW_KEY_3",
    52  to "GLFW_KEY_4",
    53  to "GLFW_KEY_5",
    54  to "GLFW_KEY_6",
    55  to "GLFW_KEY_7",
    56  to "GLFW_KEY_8",
    57  to "GLFW_KEY_9",
    59  to "GLFW_KEY_SEMICOLON",
    61  to "GLFW_KEY_EQUAL",
    65  to "GLFW_KEY_A",
    66  to "GLFW_KEY_B",
    67  to "GLFW_KEY_C",
    68  to "GLFW_KEY_D",
    69  to "GLFW_KEY_E",
    70  to "GLFW_KEY_F",
    71  to "GLFW_KEY_G",
    72  to "GLFW_KEY_H",
    73  to "GLFW_KEY_I",
    74  to "GLFW_KEY_J",
    75  to "GLFW_KEY_K",
    76  to "GLFW_KEY_L",
    77  to "GLFW_KEY_M",
    78  to "GLFW_KEY_N",
    79  to "GLFW_KEY_O",
    80  to "GLFW_KEY_P",
    81  to "GLFW_KEY_Q",
    82  to "GLFW_KEY_R",
    83  to "GLFW_KEY_S",
    84  to "GLFW_KEY_T",
    85  to "GLFW_KEY_U",
    86  to "GLFW_KEY_V",
    87  to "GLFW_KEY_W",
    88  to "GLFW_KEY_X",
    89  to "GLFW_KEY_Y",
    90  to "GLFW_KEY_Z",
    91  to "GLFW_KEY_LEFT_BRACKET",
    92  to "GLFW_KEY_BACKSLASH",
    93  to "GLFW_KEY_RIGHT_BRACKET",
    96  to "GLFW_KEY_GRAVE_ACCENT",
    161 to "GLFW_KEY_WORLD_1",
    162 to "GLFW_KEY_WORLD_2",
    // Function keys
    256 to "GLFW_KEY_ESCAPE",
    257 to "GLFW_KEY_ENTER",
    258 to "GLFW_KEY_TAB",
    259 to "GLFW_KEY_BACKSPACE",
    260 to "GLFW_KEY_INSERT",
    261 to "GLFW_KEY_DELETE",
    262 to "GLFW_KEY_RIGHT",
    263 to "GLFW_KEY_LEFT",
    264 to "GLFW_KEY_DOWN",
    265 to "GLFW_KEY_UP",
    266 to "GLFW_KEY_PAGE_UP",
    267 to "GLFW_KEY_PAGE_DOWN",
    268 to "GLFW_KEY_HOME",
    269 to "GLFW_KEY_END",
    280 to "GLFW_KEY_CAPS_LOCK",
    281 to "GLFW_KEY_SCROLL_LOCK",
    282 to "GLFW_KEY_NUM_LOCK",
    283 to "GLFW_KEY_PRINT_SCREEN",
    284 to "GLFW_KEY_PAUSE",
    290 to "GLFW_KEY_F1",
    291 to "GLFW_KEY_F2",
    292 to "GLFW_KEY_F3",
    293 to "GLFW_KEY_F4",
    294 to "GLFW_KEY_F5",
    295 to "GLFW_KEY_F6",
    296 to "GLFW_KEY_F7",
    297 to "GLFW_KEY_F8",
    298 to "GLFW_KEY_F9",
    299 to "GLFW_KEY_F10",
    300 to "GLFW_KEY_F11",
    301 to "GLFW_KEY_F12",
    302 to "GLFW_KEY_F13",
    303 to "GLFW_KEY_F14",
    304 to "GLFW_KEY_F15",
    305 to "GLFW_KEY_F16",
    306 to "GLFW_KEY_F17",
    307 to "GLFW_KEY_F18",
    308 to "GLFW_KEY_F19",
    309 to "GLFW_KEY_F20",
    310 to "GLFW_KEY_F21",
    311 to "GLFW_KEY_F22",
    312 to "GLFW_KEY_F23",
    313 to "GLFW_KEY_F24",
    314 to "GLFW_KEY_F25",
    // Keypad
    320 to "GLFW_KEY_KP_0",
    321 to "GLFW_KEY_KP_1",
    322 to "GLFW_KEY_KP_2",
    323 to "GLFW_KEY_KP_3",
    324 to "GLFW_KEY_KP_4",
    325 to "GLFW_KEY_KP_5",
    326 to "GLFW_KEY_KP_6",
    327 to "GLFW_KEY_KP_7",
    328 to "GLFW_KEY_KP_8",
    329 to "GLFW_KEY_KP_9",
    330 to "GLFW_KEY_KP_DECIMAL",
    331 to "GLFW_KEY_KP_DIVIDE",
    332 to "GLFW_KEY_KP_MULTIPLY",
    333 to "GLFW_KEY_KP_SUBTRACT",
    334 to "GLFW_KEY_KP_ADD",
    335 to "GLFW_KEY_KP_ENTER",
    336 to "GLFW_KEY_KP_EQUAL",
    // Modifier keys
    340 to "GLFW_KEY_LEFT_SHIFT",
    341 to "GLFW_KEY_LEFT_CONTROL",
    342 to "GLFW_KEY_LEFT_ALT",
    343 to "GLFW_KEY_LEFT_SUPER",
    344 to "GLFW_KEY_RIGHT_SHIFT",
    345 to "GLFW_KEY_RIGHT_CONTROL",
    346 to "GLFW_KEY_RIGHT_ALT",
    347 to "GLFW_KEY_RIGHT_SUPER",
    348 to "GLFW_KEY_MENU",
    // Mouse buttons (отрицательные значения в старом формате)
    -1  to "GLFW_MOUSE_BUTTON_LEFT",
    -2  to "GLFW_MOUSE_BUTTON_RIGHT",
    -3  to "GLFW_MOUSE_BUTTON_MIDDLE",
    -4  to "GLFW_MOUSE_BUTTON_4",
    -5  to "GLFW_MOUSE_BUTTON_5"
)

/**
 * Проверяет, является ли JSON объект старым форматом PojavLauncher.
 *
 * Старый формат содержит поля: `mControlDataList`, `scaledAt`, `version`.
 */
internal fun isPojavLegacyFormat(jsonObject: JsonObject): Boolean {
    return jsonObject.containsKey("mControlDataList") &&
           jsonObject.containsKey("scaledAt")
}

/**
 * Конвертирует JSON в старом формате PojavLauncher (ZalithLauncher 1.x)
 * в [ControlLayout] нового формата Zalith Launcher 2.
 *
 * Структура старого формата:
 * ```json
 * {
 *   "version": 6,
 *   "scaledAt": 1000,
 *   "mControlDataList": [
 *     {
 *       "name": "W",
 *       "keycodes": [87],
 *       "x": 148.0, "y": 760.0,
 *       "width": 150.0, "height": 150.0,
 *       "isSwipable": false,
 *       "passThruEnabled": false,
 *       "dynamicX": null, "dynamicY": null
 *     }
 *   ],
 *   "mJoystickDataList": [ ... ],
 *   "mDrawerDataList": [ ... ]
 * }
 * ```
 *
 * Координаты и размер в старом формате — числа с плавающей точкой
 * в диапазоне 0..scaledAt (обычно 1000).
 * В новом формате позиции — 0..10000, размеры (проценты) — 100..10000.
 */
internal fun convertPojavLegacyLayout(jsonObject: JsonObject): ControlLayout {
    val scaledAt = jsonObject["scaledAt"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 } ?: 1000.0

    val controlList = (jsonObject["mControlDataList"] as? JsonArray) ?: JsonArray(emptyList())
    val drawerList  = (jsonObject["mDrawerDataList"]  as? JsonArray) ?: JsonArray(emptyList())

    val buttons = mutableListOf<NormalData>()

    // --- Обычные кнопки ---
    for (item in controlList) {
        val obj = item as? JsonObject ?: continue
        val button = parsePojavButton(obj, scaledAt) ?: continue
        buttons.add(button)
    }

    // --- Ящики (drawer) — группа кнопок, которые показываются при нажатии ---
    // В старом формате drawer хранит дочерние кнопки в "mChildren".
    // Конвертируем их как обычные кнопки (без логики drawer, т.к. в новом формате её нет).
    for (item in drawerList) {
        val obj = item as? JsonObject ?: continue
        // Сама кнопка ящика
        val drawerButton = parsePojavButton(obj, scaledAt) ?: continue
        buttons.add(drawerButton)
        // Дочерние кнопки внутри ящика
        val children = (obj["mChildren"] as? JsonArray) ?: continue
        for (child in children) {
            val childObj = child as? JsonObject ?: continue
            val childButton = parsePojavButton(childObj, scaledAt) ?: continue
            buttons.add(childButton)
        }
    }

    // Примечание: mJoystickDataList не конвертируется —
    // в новом формате джойстик является частью специальных настроек лаунчера,
    // а не обычным элементом раскладки.

    val layer = ControlLayer(
        name = "Imported",
        uuid = randomUUID(),
        hide = false,
        hideWhenMouse = true,
        hideWhenGamepad = true,
        hideWhenJoystick = false,
        visibilityType = VisibilityType.ALWAYS,
        normalButtons = buttons,
        textBoxes = emptyList()
    )

    val info = ControlLayout.Info(
        name = TranslatableString("Imported Layout", emptyList()),
        author = TranslatableString("", emptyList()),
        description = TranslatableString("Converted from PojavLauncher format", emptyList()),
        versionCode = 0,
        versionName = "1.0"
    )

    val layout = ControlLayout(
        info = info,
        layers = listOf(layer),
        styles = emptyList(),
        editorVersion = 1
    )

    return updateLayoutToNew(layout)
}

/**
 * Парсит одну кнопку из старого формата PoJav.
 *
 * Координаты x, y и размеры width, height хранятся как числа
 * в диапазоне 0..[scaledAt].
 * Преобразование в новый формат:
 * - позиция: `(value / scaledAt * 10000).toInt()` → диапазон 0..10000
 * - размер (процент): `(value / scaledAt * 10000).toInt().coerceIn(100, 10000)` → 100..10000
 */
private fun parsePojavButton(obj: JsonObject, scaledAt: Double): NormalData? {
    val name = obj["name"]?.jsonPrimitive?.content ?: return null

    val x = obj["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val y = obj["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val width  = obj["width"]?.jsonPrimitive?.doubleOrNull  ?: (scaledAt * 0.1)
    val height = obj["height"]?.jsonPrimitive?.doubleOrNull ?: (scaledAt * 0.1)

    val posX = ((x / scaledAt) * 10000).toInt().coerceIn(0, 10000)
    val posY = ((y / scaledAt) * 10000).toInt().coerceIn(0, 10000)
    val sizeW = ((width  / scaledAt) * 10000).toInt().coerceIn(100, 10000)
    val sizeH = ((height / scaledAt) * 10000).toInt().coerceIn(100, 10000)

    val isSwipable      = obj["isSwipable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
    val passThruEnabled = obj["passThruEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    // Определяем тип видимости по полю "displayInGame" или по умолчанию ALWAYS
    val displayInGame = obj["displayInGame"]?.jsonPrimitive?.intOrNull
    val visibilityType = when (displayInGame) {
        1    -> VisibilityType.IN_GAME
        2    -> VisibilityType.IN_MENU
        else -> VisibilityType.ALWAYS
    }

    // Коды клавиш — массив числовых GLFW-кодов
    val keycodes = (obj["keycodes"] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.intOrNull }
        ?: emptyList()

    val clickEvents = keycodes.mapNotNull { code ->
        val name = GLFW_CODE_TO_NAME[code] ?: return@mapNotNull null
        // Мышиные кнопки (отрицательные коды) идут как LauncherEvent
        val type = if (code < 0) ClickEvent.Type.LauncherEvent else ClickEvent.Type.Key
        ClickEvent(type = type, key = name)
    }

    return NormalData(
        text = TranslatableString(name, emptyList()),
        uuid = getAButtonUUID(),
        position = ButtonPosition(posX, posY),
        buttonSize = ButtonSize(
            type = ButtonSize.Type.Percentage,
            widthDp = 50f,
            heightDp = 50f,
            widthPercentage = sizeW,
            heightPercentage = sizeH,
            widthReference = ButtonSize.Reference.ScreenHeight,
            heightReference = ButtonSize.Reference.ScreenHeight
        ),
        buttonStyle = null,
        textAlignment = TextAlignment.Center,
        textBold = false,
        textItalic = false,
        textUnderline = false,
        visibilityType = visibilityType,
        _clickEvents = clickEvents,
        isSwipple = isSwipable,
        isPenetrable = passThruEnabled,
        isToggleable = false
    )
}
