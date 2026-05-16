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

import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.TextAlignment
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.updateLayoutToNew
import com.movtery.layer_controller.utils.getAButtonUUID
import com.movtery.layer_controller.utils.layoutJson
import com.movtery.layer_controller.utils.randomUUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Результат попытки конвертации JSON раскладки.
 */
sealed class ConversionResult {
    /** Конвертация прошла успешно. */
    data class Success(val layout: ControlLayout) : ConversionResult()
    /** JSON уже в правильном формате — конвертация не требуется. */
    data class AlreadyValid(val layout: ControlLayout) : ConversionResult()
    /** Формат JSON не распознан. */
    data class UnknownFormat(val reason: String) : ConversionResult()
    /** Ошибка при парсинге. */
    data class ParseError(val error: Exception) : ConversionResult()
}

/**
 * Пытается конвертировать произвольную JSON-строку раскладки управления
 * в текущий формат Zalith Launcher.
 *
 * Поддерживаемые форматы:
 * 1. Zalith — есть `editorVersion` + `info` + `layers` → [ConversionResult.AlreadyValid]
 * 2. Старый Zalith без `editorVersion` → мигрируем с версии 1
 * 3. **PojavLauncher** — есть `mControlDataList` → конвертируем
 * 4. Упрощённый с `layers` но без `info` → достраиваем `info`
 * 5. Нераспознанный → [ConversionResult.UnknownFormat]
 */
fun convertLayoutJson(jsonString: String): ConversionResult {
    val jsonObject = try {
        layoutJson.decodeFromString<JsonObject>(jsonString)
    } catch (e: Exception) {
        return ConversionResult.ParseError(e)
    }

    // 1. Правильный формат Zalith
    if (jsonObject.containsKey("editorVersion") &&
        jsonObject.containsKey("info") &&
        jsonObject.containsKey("layers")
    ) {
        return try {
            ConversionResult.AlreadyValid(loadLayoutFromString(jsonString))
        } catch (e: IllegalArgumentException) {
            ConversionResult.UnknownFormat("editorVersion is too new: ${e.message}")
        } catch (e: SerializationException) {
            ConversionResult.ParseError(e)
        }
    }

    // 2. PojavLauncher format: mControlDataList, mDrawerDataList, mJoystickDataList
    if (jsonObject.containsKey("mControlDataList")) {
        return convertFromPojav(jsonObject)
    }

    // 3. Старый Zalith без editorVersion
    if (!jsonObject.containsKey("editorVersion") &&
        jsonObject.containsKey("layers") &&
        jsonObject.containsKey("info")
    ) {
        return try {
            val patched = buildJsonObject {
                jsonObject.forEach { (k, v) -> put(k, v) }
                put("editorVersion", JsonPrimitive(1))
            }
            val layout = layoutJson.decodeFromString<ControlLayout>(patched.toString())
            ConversionResult.Success(updateLayoutToNew(layout))
        } catch (e: Exception) {
            ConversionResult.ParseError(e)
        }
    }

    // 4. Есть layers но нет info
    if (jsonObject.containsKey("layers") && !jsonObject.containsKey("info")) {
        return try {
            val name = jsonObject["name"]?.jsonPrimitive?.contentOrNull
                ?: jsonObject["title"]?.jsonPrimitive?.contentOrNull
                ?: "Imported Layout"
            val author = jsonObject["author"]?.jsonPrimitive?.contentOrNull ?: ""
            val patched = buildJsonObject {
                jsonObject.forEach { (k, v) -> put(k, v) }
                put("info", buildInfoJson(name, author))
                put("editorVersion", JsonPrimitive(1))
                if (!jsonObject.containsKey("styles")) put("styles", JsonArray(emptyList()))
            }
            val layout = layoutJson.decodeFromString<ControlLayout>(patched.toString())
            ConversionResult.Success(updateLayoutToNew(layout))
        } catch (e: Exception) {
            ConversionResult.ParseError(e)
        }
    }

    return ConversionResult.UnknownFormat(
        "JSON does not contain required fields: 'editorVersion', 'info', 'layers'. " +
        "Found keys: ${jsonObject.keys.take(10).joinToString()}"
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// PojavLauncher converter
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Конвертирует раскладку PojavLauncher в формат Zalith.
 *
 * Структура PojavLauncher:
 * ```json
 * {
 *   "mControlDataList": [{ "keycodes": [keycode,...], "name": "...",
 *       "x": 0.1, "y": 0.9, "width": 100.0, "height": 100.0,
 *       "dynamicX": ..., "dynamicY": ..., "isToggleable": false }],
 *   "mDrawerDataList": [ { ...button..., "properties": { "data": [...subbtons...] } } ],
 *   "mJoystickDataList": [ ... ],  // пропускаем — у Zalith своя система джойстиков
 *   "scaledAt": 720.0,
 *   "version": 1
 * }
 * ```
 * Координаты x/y — доля экрана 0.0–1.0 (умноженная на ширину/высоту при scaledAt).
 * width/height — пиксели при scaledAt.
 */
private fun convertFromPojav(root: JsonObject): ConversionResult {
    return try {
        // scaledAt — высота эталонного экрана при которой записывались размеры
        val scaledAt = root["scaledAt"]?.jsonPrimitive?.doubleOrNull ?: 720.0

        val buttons = mutableListOf<NormalData>()

        // Обычные кнопки
        root["mControlDataList"]?.jsonArray?.forEach { el ->
            runCatching { convertPojavButton(el.jsonObject, scaledAt) }
                .getOrNull()?.let { buttons += it }
        }

        // Кнопки-дравер (группы): сама кнопка-триггер + суб-кнопки внутри
        root["mDrawerDataList"]?.jsonArray?.forEach { el ->
            val drawer = el.jsonObject
            runCatching { convertPojavButton(drawer, scaledAt) }
                .getOrNull()?.let { buttons += it }
            drawer["properties"]?.jsonObject
                ?.get("data")?.jsonArray
                ?.forEach { sub ->
                    runCatching { convertPojavButton(sub.jsonObject, scaledAt) }
                        .getOrNull()?.let { buttons += it }
                }
        }

        // mJoystickDataList — пропускаем (Zalith использует встроенный джойстик)

        val layer = ControlLayer(
            name = "Imported from PojavLauncher",
            uuid = randomUUID(),
            hide = false,
            hideWhenMouse = true,
            hideWhenGamepad = true,
            hideWhenJoystick = false,
            visibilityType = VisibilityType.ALWAYS,
            normalButtons = buttons,
            textBoxes = emptyList()
        )

        val layout = ControlLayout(
            info = ControlLayout.Info(
                name = TranslatableString(default = "Pojav Layout"),
                author = TranslatableString(default = ""),
                description = TranslatableString(default = "Imported from PojavLauncher"),
                versionCode = 0,
                versionName = "1.0"
            ),
            layers = listOf(layer),
            styles = emptyList(),
            editorVersion = EDITOR_VERSION
        )

        ConversionResult.Success(layout)
    } catch (e: Exception) {
        ConversionResult.ParseError(e)
    }
}

/**
 * Конвертирует одну кнопку PojavLauncher в [NormalData].
 * Возвращает null если данных недостаточно.
 */
private fun convertPojavButton(btn: JsonObject, scaledAt: Double): NormalData? {
    val name = btn["name"]?.jsonPrimitive?.contentOrNull
        ?: btn["label"]?.jsonPrimitive?.contentOrNull
        ?: return null

    // Координаты: доля 0.0–1.0 → Zalith 0–10000
    val xFrac = btn["x"]?.jsonPrimitive?.doubleOrNull ?: return null
    val yFrac = btn["y"]?.jsonPrimitive?.doubleOrNull ?: return null
    val xZ = (xFrac * 10000).roundToInt().coerceIn(0, 10000)
    val yZ = (yFrac * 10000).roundToInt().coerceIn(0, 10000)

    // Размер: пиксели при scaledAt → проценты от высоты (единицы Zalith: 100 = 1%)
    val wPx = btn["width"]?.jsonPrimitive?.doubleOrNull ?: 50.0
    val hPx = btn["height"]?.jsonPrimitive?.doubleOrNull ?: 50.0
    val wPct = ((wPx / scaledAt) * 10000).roundToInt().coerceIn(100, 10000)
    val hPct = ((hPx / scaledAt) * 10000).roundToInt().coerceIn(100, 10000)

    // keycodes → ClickEvent.Key
    val clickEvents = btn["keycodes"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.intOrNull }
        ?.filter { it != 0 }
        ?.map { ClickEvent(type = ClickEvent.Type.Key, key = it.toString()) }
        ?: emptyList()

    val isToggleable = btn["isToggleable"]?.jsonPrimitive?.contentOrNull
        .equals("true", ignoreCase = true)

    return NormalData(
        text = TranslatableString(default = name),
        uuid = getAButtonUUID(),
        position = ButtonPosition(x = xZ, y = yZ),
        buttonSize = ButtonSize(
            type = ButtonSize.Type.Percentage,
            widthDp = 50f,
            heightDp = 50f,
            widthPercentage = wPct,
            heightPercentage = hPct,
            widthReference = ButtonSize.Reference.ScreenHeight,
            heightReference = ButtonSize.Reference.ScreenHeight
        ),
        buttonStyle = null,
        textAlignment = TextAlignment.Center,
        textBold = false,
        textItalic = false,
        textUnderline = false,
        visibilityType = VisibilityType.ALWAYS,
        isSwipple = false,
        isPenetrable = false,
        isToggleable = isToggleable
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

private fun buildInfoJson(name: String, author: String): JsonObject = buildJsonObject {
    put("name", buildTranslatableJson(name))
    put("author", buildTranslatableJson(author))
    put("description", buildTranslatableJson(""))
    put("versionCode", JsonPrimitive(0))
    put("versionName", JsonPrimitive("1.0"))
}

private fun buildTranslatableJson(default: String): JsonObject = buildJsonObject {
    put("default", JsonPrimitive(default))
    put("matchQueue", JsonArray(emptyList()))
}
