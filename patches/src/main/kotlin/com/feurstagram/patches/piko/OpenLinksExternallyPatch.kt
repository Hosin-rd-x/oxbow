/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's OpenLinksExternallyPatch, which targeted
 * Instagram 439.0.0.37.89. The fingerprint strings below are piko's 439 anchors;
 * they must be re-verified by the Morphe CLI patch run against the real 454 APK
 * before this patch can be trusted on 454.0.0.54.77. The external-open itself
 * is Oxbow's own Links.openExternally (no PikoUtils dependency).
 *
 * 454 re-verification (round 2, from the real 454.0.0.54.77 dex): LIKELY-FAIL.
 * Both anchor strings exist on 454 ("Tracking.ARG_CLICK_SOURCE" in 12 methods,
 * "TrackingInfo.ARG_MODULE_NAME" in 4 methods) but they never co-occur in a
 * method returning Z, so InAppBrowserFunctionFingerprint matches nothing on
 * 454 and the 439 fingerprint is left as-is. The closest 454 equivalent is
 * LX/0FHH.A00, the in-app-browser intent builder, which loads both strings
 * but returns an Intent, not a boolean, and takes the URL as a Uri, not a
 * String — so piko's hook logic (MOVE_OBJECT_FROM16 after the first string
 * gives the URL string; return the boolean) does not transfer. It was NOT
 * re-anchored: its two callers do not null-check the returned Intent, so
 * returning null to suppress the in-app browser risks a crash, and the safe
 * adaptation (return an external ACTION_VIEW intent instead) needs caller
 * verification first. Lead for a future round, not a forced port.
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val LINKS_CLASS = "Lcom/feurstagram/extension/Links;"

internal object InAppBrowserFunctionFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("Tracking.ARG_CLICK_SOURCE", "TrackingInfo.ARG_MODULE_NAME"),
)

@Suppress("unused")
val openLinksExternallyPatch =
    bytecodePatch(
        name = "Open links externally",
        description = "Links always open in your external browser, instead of the in-app browser. " +
            "Off by default; turn it on from the Keep settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            InAppBrowserFunctionFingerprint.let {
                val stringMatchIndex = it.stringMatches[0].index

                it.method.apply {
                    val urlResultObjIndex =
                        indexOfFirstInstructionOrThrow(
                            stringMatchIndex,
                            Opcode.MOVE_OBJECT_FROM16,
                        )

                    val urlRegister = getInstruction(urlResultObjIndex).registersUsed[0]
                    val freeRegister = findFreeRegister(urlResultObjIndex + 1)

                    addInstructionsWithLabels(
                        urlResultObjIndex + 1,
                        """
                        invoke-static/range { v$urlRegister .. v$urlRegister }, $LINKS_CLASS->openExternally(Ljava/lang/String;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko_links
                        return v$freeRegister
                    """,
                        ExternalLabel("piko_links", instructions[urlResultObjIndex + 1]),
                    )
                }
            }
        }
    }
