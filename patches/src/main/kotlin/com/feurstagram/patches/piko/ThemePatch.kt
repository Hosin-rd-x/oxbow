/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's ThemePatch, which targeted Instagram
 * 439.0.0.37.89. Structure follows Oxbow's ClonePatch (a resourcePatch with a
 * dependent bytecodePatch that only captures its BytecodePatchContext, so the
 * bytecode installs run through context(bytecodePatchContext) from here).
 * Differences from piko: no dependsOn(settingsPatch / sharedExtensionPatch /
 * resourceMappingPatch) — Oxbow has no such patches; the settings toggles live
 * in Oxbow's own Config/Settings (see the wiring note in the port report) and
 * the extension bundle is merged by the other bytecode patches' extendWith.
 * Overlay assets are written under assets/oxbow/ (see ThemeResources.kt).
 * The AMOLED option is the patch-time choice for Android 8–11 only; on
 * Android 12+ the runtime toggles in settings decide (see MaterialYouTheme).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM

@Suppress("unused")
val themePatch =
    resourcePatch(
        name = "Theme",
        description =
            "Adds Material You and AMOLED controls to Oxbow settings " +
                "on Android 12 and later. On Android 8–11, it applies a fixed " +
                "Material You-style theme or an optional AMOLED theme. " +
                "Off by default; turn it on from the Theme settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val amoled by booleanOption(
            key = "amoled",
            default = false,
            title = "Pure-black AMOLED theme for Android 8–11",
            description =
                "Use a fixed pure-black AMOLED theme instead of the fixed Material " +
                    "You-style theme on Android 8–11. On Android 12 and later, use " +
                    "the AMOLED control in Oxbow settings.",
        )

        var bytecodePatchContext: BytecodePatchContext? = null

        dependsOn(
            bytecodePatch {
                execute {
                    bytecodePatchContext = this
                }
            },
        )

        execute {
            try {
                val originalApi31Base = captureApi31Base()

                forceWhiteOnMediaChrome()
                preserveCreationButtonContrast()
                preserveLightClipsComposerContrast()
                preserveLightOverflowStampBackgrounds()
                applyLegacyTheme(amoled == true)

                restoreApi31Base(
                    snapshot = originalApi31Base,
                )
                writeMaterialYouOverlay(night = false)
                writeMaterialYouOverlay(night = true)
                writeAmoledOverlay(originalApi31Base)
                writeAmoledMaterialYouOverlay()

                context(requireNotNull(bytecodePatchContext)) {
                    installComposePrismBlackRuntime(legacyAmoled = amoled == true)
                    installSystemDefaultUiModeHook()
                    installThemeLifecycleHooks()
                    installNativeThemeModeSync()
                }
            } finally {
                bytecodePatchContext = null
            }
        }
    }
