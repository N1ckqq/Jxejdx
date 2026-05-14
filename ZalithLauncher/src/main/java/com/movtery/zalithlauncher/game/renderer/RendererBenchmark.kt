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

package com.movtery.zalithlauncher.game.renderer

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 渲染器性能基准测试
 * 通过简单的OpenGL ES操作来估算各渲染器的相对性能
 *
 * 注意：这是一个简化的基准测试，使用EGL离屏渲染。
 * 由于实际的渲染器需要加载特定的native库（GL4ES, Zink等），
 * 此测试仅测量设备的基础OpenGL ES性能作为参考。
 * 真实的渲染器性能还取决于具体的翻译层效率。
 */
object RendererBenchmark {

    data class BenchmarkResult(
        val rendererName: String,
        val rendererIdentifier: String,
        /** 基础性能评分 (越高越好) */
        val score: Int,
        /** 估算的理论FPS */
        val estimatedFps: Int,
        /** 渲染器特性描述 */
        val characteristics: String,
        /** 是否推荐 */
        val recommended: Boolean
    )

    data class BenchmarkReport(
        val results: List<BenchmarkResult>,
        val deviceGpuInfo: String,
        val baselineScore: Int,
        val testDurationMs: Long
    )

    /**
     * 运行基准测试
     * 对所有兼容的渲染器进行性能评估
     */
    suspend fun runBenchmark(context: Context): BenchmarkReport = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val compatibleRenderers = Renderers.getCompatibleRenderers(context).second

        // 运行基础OpenGL ES测试（设备原生性能）
        val baselineScore = measureBaselinePerformance()

        val results = compatibleRenderers.map { renderer ->
            estimateRendererPerformance(renderer, baselineScore)
        }

        val duration = System.currentTimeMillis() - startTime
        val gpuInfo = getGpuInfo()

        BenchmarkReport(
            results = results.sortedByDescending { it.score },
            deviceGpuInfo = gpuInfo,
            baselineScore = baselineScore,
            testDurationMs = duration
        )
    }

    /**
     * 测量设备基础OpenGL ES性能
     * 使用EGL Pbuffer进行离屏渲染测试
     */
    private fun measureBaselinePerformance(): Int {
        var eglDisplay: EGLDisplay? = null
        var eglContext: EGLContext? = null
        var eglSurface: EGLSurface? = null

        try {
            // 初始化EGL
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return 500

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return 500

            // 配置EGL
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) return 500

            val config = configs[0] ?: return 500

            // 创建上下文
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return 500

            // 创建PBuffer Surface
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 256, EGL14.EGL_HEIGHT, 256, EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return 500

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            // Бенчмарк: считаем сколько draw calls за 500мс
            var drawCount = 0
            val testDuration = 500L
            val start = System.nanoTime()

            while ((System.nanoTime() - start) / 1_000_000 < testDuration) {
                GLES20.glClearColor(
                    (drawCount % 256) / 255f,
                    ((drawCount * 7) % 256) / 255f,
                    ((drawCount * 13) % 256) / 255f,
                    1f
                )
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glFlush()
                drawCount++
            }

            val elapsed = (System.nanoTime() - start) / 1_000_000
            // Нормализуем к оценке (draw calls per second → score)
            val drawsPerSecond = (drawCount * 1000L / elapsed).toInt()
            val score = (drawsPerSecond / 10).coerceIn(100, 10000)

            lInfo("Renderer benchmark baseline: $drawCount draws in ${elapsed}ms, score=$score")
            return score

        } catch (e: Exception) {
            lWarning("Benchmark failed", e)
            return 500
        } finally {
            // Очистка
            if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    /**
     * Оценка производительности конкретного рендерера на основе его характеристик
     * и базовой производительности устройства
     */
    private fun estimateRendererPerformance(renderer: RendererInterface, baselineScore: Int): BenchmarkResult {
        val id = renderer.getRendererId()
        val name = renderer.getRendererName()
        val identifier = renderer.getUniqueIdentifier()

        // Коэффициенты производительности для разных рендереров
        // (на основе известных характеристик каждого бэкенда)
        val (multiplier, characteristics) = when {
            id.contains("gl4es") && id.contains("ng") -> {
                0.85f to "Next-gen GL4ES. Good balance of compatibility and performance."
            }
            id.contains("gl4es") -> {
                0.80f to "Classic GL4ES. Stable, good compatibility with older versions."
            }
            id.contains("zink") || id.contains("vulkan") -> {
                0.70f to "Vulkan-based (Zink). Best for modern versions, higher RAM usage."
            }
            id.contains("virgl") -> {
                0.45f to "VirGL (virtual GPU). Slowest but most compatible renderer."
            }
            id.contains("freedreno") -> {
                0.75f to "Freedreno (Adreno native). Fast on Qualcomm devices."
            }
            id.contains("panfrost") -> {
                0.70f to "Panfrost (Mali native). Optimized for ARM Mali GPUs."
            }
            else -> {
                0.60f to "Unknown renderer type."
            }
        }

        val score = (baselineScore * multiplier).toInt()
        val estimatedFps = (score / 15).coerceIn(10, 120)

        // Рекомендуем рендерер с лучшим балансом производительности
        val recommended = when {
            id.contains("gl4es") && id.contains("ng") -> true
            id.contains("zink") && baselineScore > 800 -> true // Zink на мощных устройствах
            else -> false
        }

        return BenchmarkResult(
            rendererName = name,
            rendererIdentifier = identifier,
            score = score,
            estimatedFps = estimatedFps,
            characteristics = characteristics,
            recommended = recommended
        )
    }

    /**
     * Получение информации о GPU
     */
    private fun getGpuInfo(): String {
        var gpuInfo = "Unknown GPU"
        var eglDisplay: EGLDisplay? = null
        var eglContext: EGLContext? = null
        var eglSurface: EGLSurface? = null

        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return gpuInfo

            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: return gpuInfo

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs, 0)

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"

            gpuInfo = "$vendor $renderer ($glVersion)"

        } catch (e: Exception) {
            lWarning("Failed to get GPU info", e)
        } finally {
            if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != null) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != null) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }

        return gpuInfo
    }
}
