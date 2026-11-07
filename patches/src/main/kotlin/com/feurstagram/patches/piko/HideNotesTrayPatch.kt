/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's HideNotesTrayPatch, which targeted
 * Instagram 439.0.0.37.89. The anchor below is piko's 439 resource anchor;
 * "cf_hub_recycler_view" is a resource name, not a dex string, so it cannot
 * appear in the dex — it was re-verified present in the 454.0.0.54.77 APK's
 * resources.arsc on 7 Nov 2026 (server ~/insta-build/merged/instagram.apk).
 * The resource id itself is resolved at patch time by resourceMappingPatch.
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

internal object NotesTrayBuilderConstructorFingerprint : Fingerprint(
    filters =
        listOf(resourceLiteral(ResourceType.ID, "cf_hub_recycler_view")),
)

@Suppress("unused")
val hideNotesTrayPatch =
    bytecodePatch(
        name = "Hide notes tray",
        description = "Hides the notes tray in the DM section. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        dependsOn(resourceMappingPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            NotesTrayBuilderConstructorFingerprint.apply {
                val notesRecyclerViewIDIndex = instructionMatches.first().index
                method.apply {
                    val notesRecyclerViewIndex =
                        indexOfFirstInstruction(notesRecyclerViewIDIndex, Opcode.IPUT_OBJECT)
                    val notesRecyclerViewInstruction = getInstruction(notesRecyclerViewIndex)
                    val notesTrayRegister = notesRecyclerViewInstruction.registersUsed[0]
                    val freeRegister = findFreeRegister(notesRecyclerViewIndex)

                    val iPutObjectInstruction = getInstruction(notesRecyclerViewIndex)

                    addInstructionsWithLabels(
                        notesRecyclerViewIndex,
                        """
                        invoke-static { }, $CONFIG_CLASS->hideNotesTray()Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko_notes_tray
                        const/16 v$freeRegister, 0x8
                        invoke-virtual {v$notesTrayRegister, v$freeRegister}, Landroid/view/View;->setVisibility(I)V
                        """.trimIndent(),
                        ExternalLabel("piko_notes_tray", iPutObjectInstruction),
                    )
                }
            }
        }
    }
