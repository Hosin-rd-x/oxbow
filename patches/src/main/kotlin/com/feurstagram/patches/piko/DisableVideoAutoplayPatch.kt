/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's DisableVideoAutoplayPatch, which targeted
 * Instagram 439.0.0.37.89. The fingerprint strings below are piko's 439 anchors;
 * they must be re-verified by the Morphe CLI patch run against the real 454 APK
 * before this patch can be trusted on 454.0.0.54.77.
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION
import com.feurstagram.patches.shared.returnEarlyIf

internal object DisableVideoAutoplayMethodFingerprint : Fingerprint(
    strings = listOf("ig_olympus_disable_video_autoplay", "ig_disable_video_autoplay", "ig_video_setting"),
    returnType = "Z",
)

// Credits to MyInsta (via piko).
@Suppress("unused")
val disableVideoAutoplayPatch =
    bytecodePatch(
        name = "Disable video autoplay",
        description = "Videos don't start playing by themselves in the feed. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            // The hooked method answers whether autoplay is disabled: return true
            // (disabled) while the toggle is on, stock behaviour otherwise.
            DisableVideoAutoplayMethodFingerprint.method
                .returnEarlyIf("isVideoAutoplayDisabled", "const/4 v0, 0x1\n            return v0")
        }
    }
