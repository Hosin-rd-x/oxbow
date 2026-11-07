/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's HideStoriesTrayPatch, which targeted
 * Instagram 439.0.0.37.89. The anchors below are piko's 439 anchors; all
 * three were re-verified present in the 454.0.0.54.77 dex on 7 Nov 2026
 * (server /tmp/dexcheck: "MainFeedStoryTrayBinderGroup",
 * "litho_main_feed_stories_tray" and "floating_tray_spacer" all FOUND).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

internal object MainFeedStoryTrayBinderGroupViewBinderFingerprint : Fingerprint(
    strings = listOf("MainFeedStoryTrayBinderGroup", "litho_main_feed_stories_tray", "floating_tray_spacer"),
)

@Suppress("unused")
val hideStoriesTrayPatch =
    bytecodePatch(
        name = "Hide stories tray",
        description = "Hides the stories tray from the main feed. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            MainFeedStoryTrayBinderGroupViewBinderFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    val iGetObjectInstruction =
                        instructions.last { it.opcode == Opcode.IGET_OBJECT && it.location.index < strIndex }
                    val iGetObjectIndex = iGetObjectInstruction.location.index
                    val targetIndex = iGetObjectIndex + 1

                    val storiesTrayViewGroupRegistry = iGetObjectInstruction.registersUsed[0]
                    val freeRegister = getInstruction(strIndex).registersUsed[0]

                    addInstructionsWithLabels(
                        targetIndex,
                        """
                        invoke-static { }, $CONFIG_CLASS->hideStoriesTray()Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko_hide_tray
                        const v$storiesTrayViewGroupRegistry, 0x0
                        """.trimIndent(),
                        ExternalLabel("piko_hide_tray", getInstruction(targetIndex + 1)),
                    )
                }
            }
        }
    }
