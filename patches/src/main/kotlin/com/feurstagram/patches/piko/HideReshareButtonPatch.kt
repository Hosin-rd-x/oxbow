/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's HideReshareButtonPatch, which targeted
 * Instagram 439.0.0.37.89. The anchors below are piko's 439 anchors; both were
 * re-verified present in the 454.0.0.54.77 dex on 7 Nov 2026 (server
 * /tmp/dexcheck: "enable_media_notes_production" and
 * "getOptionalBooleanValueByHashCode" both FOUND). The hash literal is the
 * Java hashCode of "enable_media_notes_production" (-545107410).
 * The two hooks share one toggle; the LiveTree hook's jump label is kept
 * distinct (:piko_reshare_nopatch) and the parser hook uses the shared
 * returnEarlyIf helper (label :instagram, local to that method).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION
import com.feurstagram.patches.shared.returnEarlyIf

// Credits: brosssh
// https://github.com/brosssh/morphe-patches/commit/6a781ef8e0951ad5aa898fa17d094cfbfa5dd9fb

// The hash code of the field of interest. It is used as the key of a hashmap.
internal const val notesTag = "enable_media_notes_production"
internal val hashedFieldInteger = notesTag.hashCode()

internal object FeedResponseMediaParserFingerprint : Fingerprint(
    filters =
        listOf(
            string(notesTag),
            literal(hashedFieldInteger),
        ),
    returnType = "Ljava/lang/Boolean;",
)

internal object LiveTreeGetOptionalBooleanFingerprint : Fingerprint(
    name = "getOptionalBooleanValueByHashCode",
    definingClass = "Lcom/instagram/pando/livetree/LiveTreeJNI;",
)

@Suppress("unused")
val hideReshareButtonPatch =
    bytecodePatch(
        name = "Hide reshare button",
        description = "Hides the reshare button from both posts and reels. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            // The media parser answers whether the reshare button is enabled for
            // this media: answer false (hidden) while the toggle is on.
            FeedResponseMediaParserFingerprint.method.returnEarlyIf(
                "hideReshareButton",
                "sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;\n            return-object v0",
            )

            // If it is trying to get the value for our field of interest via the
            // Pando native library, force the value to false instead.
            LiveTreeGetOptionalBooleanFingerprint.method.addInstructions(
                0,
                """
                const v0, $hashedFieldInteger
                if-ne p1, v0, :piko_reshare_nopatch
                invoke-static { }, $CONFIG_CLASS->hideReshareButton()Z
                move-result v0
                if-eqz v0, :piko_reshare_nopatch
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
                :piko_reshare_nopatch
                nop
                """.trimIndent(),
            )
        }
    }
