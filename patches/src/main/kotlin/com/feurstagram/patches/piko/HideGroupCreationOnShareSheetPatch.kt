/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's HideGroupCreationOnShareSheetPatch,
 * which targeted Instagram 439.0.0.37.89. The anchors below are piko's 439
 * anchors; both were re-verified present in the 454.0.0.54.77 dex on
 * 7 Nov 2026 (server /tmp/dexcheck: the
 * DirectShareSheetFragmentMessageComposerViewBinder class and the
 * Lcom/instagram/common/session/UserSession; class both FOUND).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

internal object ShareSheetItemsBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/List;", "Z", "Lcom/instagram/common/session/UserSession;"),
    definingClass = "Lcom/instagram/direct/fragment/sharesheet/view/DirectShareSheetFragmentMessageComposerViewBinder;",
)

@Suppress("unused")
val hideGroupCreationOnShareSheetPatch =
    bytecodePatch(
        name = "Hide group creation button on sharesheet",
        description = "Hides the \"create group\" button on the share sheet. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            ShareSheetItemsBinderFingerprint.method.apply {
                val firstGoto16Index = indexOfFirstInstruction(Opcode.GOTO_16)
                val firstInvokeStaticAfterGoto =
                    indexOfFirstInstruction(firstGoto16Index, Opcode.INVOKE_STATIC)

                val conditionIndex = firstGoto16Index + 2
                val freeRegister = findFreeRegister(conditionIndex + 1)

                addInstructionsWithLabels(
                    conditionIndex,
                    """
                    invoke-static { }, $CONFIG_CLASS->hideGroupCreationOnSharesheet()Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :piko_hide_group
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_hide_group", getInstruction(firstInvokeStaticAfterGoto)),
                )
            }
        }
    }
