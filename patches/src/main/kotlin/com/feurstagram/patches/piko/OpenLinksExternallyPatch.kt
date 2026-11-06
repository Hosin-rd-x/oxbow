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
