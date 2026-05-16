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
import com.movtery.layer_controller.updateLayoutToNew
import com.movtery.layer_controller.utils.layoutJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    /** Ошибка сериализации при парсинге. */
    data class ParseError(val error: Exception) : ConversionResult()
}

/**
 * Пытается конвертировать произвольную JSON-строку раскладки управления
 * в текущий формат Zalith Launcher.
 *
 * Поддерживаемые сценарии:
 * 1. Корректный формат Zalith (есть `editorVersion`) — возвращает [ConversionResult.AlreadyValid].
 * 2. Старый формат Zalith без `editorVersion` — добавляет `editorVersion=1` и мигрирует.
 * 3. Упрощённый формат (есть `layers` но нет `info`) — генерирует `info` по умолчанию.
 * 4. Нераспознанный JSON — возвращает [ConversionResult.UnknownFormat].
 */
fun convertLayoutJson(jsonString: String): ConversionResult {
    val jsonObject = try {
        layoutJson.decodeFromString<JsonObject>(jsonString)
    } catch (e: Exception) {
        return ConversionResult.ParseError(e)
    }

    // 1. Уже правильный формат Zalith — просто грузим
    if (jsonObject.containsKey("editorVersion") && jsonObject.containsKey("info") && jsonObject.containsKey("layers")) {
        return try {
            val layout = loadLayoutFromString(jsonString)
            ConversionResult.AlreadyValid(layout)
        } catch (e: IllegalArgumentException) {
            // editorVersion > EDITOR_VERSION — неподдерживаемая версия
            ConversionResult.UnknownFormat("editorVersion is too new: ${e.message}")
        } catch (e: SerializationException) {
            ConversionResult.ParseError(e)
        }
    }

    // 2. Есть layers и info, но нет editorVersion — старый формат Zalith v1
    if (!jsonObject.containsKey("editorVersion") && jsonObject.containsKey("layers") && jsonObject.containsKey("info")) {
        return try {
            val patched = buildJsonObject {
                jsonObject.forEach { (key, value) -> put(key, value) }
                put("editorVersion", JsonPrimitive(1))
            }
            val layout = layoutJson.decodeFromString<ControlLayout>(patched.toString())
            ConversionResult.Success(updateLayoutToNew(layout))
        } catch (e: Exception) {
            ConversionResult.ParseError(e)
        }
    }

    // 3. Есть layers без info и editorVersion — пытаемся достроить info
    if (jsonObject.containsKey("layers") && !jsonObject.containsKey("info")) {
        return try {
            val name = jsonObject["name"]?.jsonPrimitive?.content
                ?: jsonObject["title"]?.jsonPrimitive?.content
                ?: "Imported Layout"
            val author = jsonObject["author"]?.jsonPrimitive?.content ?: ""

            val infoObj = buildJsonObject {
                put("name", buildTranslatableJson(name))
                put("author", buildTranslatableJson(author))
                put("description", buildTranslatableJson(""))
                put("versionCode", JsonPrimitive(0))
                put("versionName", JsonPrimitive("1.0"))
            }

            val patched = buildJsonObject {
                jsonObject.forEach { (key, value) -> put(key, value) }
                put("info", infoObj)
                put("editorVersion", JsonPrimitive(1))
                // убеждаемся что есть пустой styles если отсутствует
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

/** Строит JSON-объект TranslatableString с дефолтным значением. */
private fun buildTranslatableJson(default: String): JsonObject = buildJsonObject {
    put("default", JsonPrimitive(default))
    put("matchQueue", JsonArray(emptyList()))
}
