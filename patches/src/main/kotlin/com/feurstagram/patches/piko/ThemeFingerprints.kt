/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's ThemeFingerprints, which targeted
 * Instagram 439.0.0.37.89. Anchors re-verified against the 454.0.0.54.77 dex
 * (server /tmp/dexcheck): "theme_settings" x1, "ig_device_theme" x3,
 * "KEY_CONFIG_CURRENT_SYSTEM_UI_MODE" x1, "search_row" x4,
 * "Lcom/instagram/compose/core/theme/BasePrismColorsV2;" x7, "GRAY_1600" x6.
 * The DarkModeSection anchor was RE-ANCHORED: piko's 439 string ends
 * "(AccessibilityOptionsComposeFragment.kt:267)"; the 454 dex has ":274".
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object LegacyDarkModeFragmentConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("theme_settings"),
)

internal object CurrentSystemUiModeFingerprint : Fingerprint(
    returnType = "I",
    parameters = emptyList(),
    strings =
        listOf(
            "ig_device_theme",
            "KEY_CONFIG_CURRENT_SYSTEM_UI_MODE",
        ),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.implementation != null
    },
)

internal object DarkModeSectionFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "com.instagram.settings.impl.accessibility.DarkModeSection " +
                "(AccessibilityOptionsComposeFragment.kt:274)",
            "dark",
            "light",
            "system",
        ),
    custom = { method, _ ->
        method.parameterTypes.count {
            it == "Lkotlin/jvm/functions/Function1;"
        } == 1
    },
)
