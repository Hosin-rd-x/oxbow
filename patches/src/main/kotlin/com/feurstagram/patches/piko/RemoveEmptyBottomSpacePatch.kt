/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's RemoveEmptyBottomSpacePatch, which
 * targeted Instagram 439.0.0.37.89. The fingerprint strings below are piko's
 * 439 anchors; they were re-verified present in the real 454.0.0.54.77 dex on
 * 7 Nov 2026 (server /tmp/dexcheck: "config_showNavigationBar" and
 * "_hasNavigationBar_notFound" both FOUND in classes2.dex), and the method
 * structure was checked by disassembling that dex: the method holding the
 * strings is the navigation-bar adjuster; its last IF_GTZ before the
 * "android" string jumps to the first SPUT after the strings, and the
 * instruction right after that IF_GTZ writes its register first
 * (sget-boolean), so piko's free-register choice is safe on 454 too.
 * Whether all three strings still sit in one method — and the patch as a
 * whole — is finally proven by the Morphe CLI patch run against the 454 APK.
 *
 * What the hook does: right after that IF_GTZ it asks
 * Config.removeEmptyBottomSpace(). While the toggle is off it jumps straight
 * to the SPUT (the nav-bar detection below it is skipped); while it is on,
 * the detection runs as stock. Piko's own default for this setting was on;
 * Oxbow wires it off by default (see Config.removeEmptyBottomSpace).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

internal object NavigationBarAdjusterFingerprint : Fingerprint(
    strings = listOf("android", "config_showNavigationBar", "_hasNavigationBar_notFound"),
)

// Credits to MyInsta (via piko).
@Suppress("unused")
val removeEmptyBottomSpacePatch =
    bytecodePatch(
        name = "Remove empty bottom space",
        description = "Removes empty space below bottom navigation bar. " +
            "Off by default; turn it on from the Navigation settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            // Thanks to MyInsta.
            NavigationBarAdjusterFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {
                    val lastIfGtzInstructionBeforeStr =
                        instructions.last {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.IF_GTZ
                        }
                    val index = lastIfGtzInstructionBeforeStr.location.index

                    val firstSPutIndexAfterStr = indexOfFirstInstruction(strIndex, Opcode.SPUT)

                    val freeRegister = getInstruction(index + 1).registersUsed[0]

                    addInstructionsWithLabels(
                        index + 1,
                        """
                        invoke-static {}, $CONFIG_CLASS->removeEmptyBottomSpace()Z
                        move-result v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(firstSPutIndexAfterStr)),
                    )
                }
            }
        }
    }
