/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's LoopStoryPatch and
 * DisableStoryFlippingPatch, which targeted Instagram 439.0.0.37.89. Both hook
 * the same ReelViewerFragment method; the fingerprint anchors below are piko's
 * 439 anchors and must be re-verified by the Morphe CLI patch run against the
 * real 454 APK before these patches can be trusted on 454.0.0.54.77.
 * The two patches insert into the same method, so their jump labels are kept
 * distinct (piko_loop_* / piko_noflip) instead of piko's shared ":piko".
 *
 * 454 re-verification (round 2, from the real 454.0.0.54.77 dex):
 * - Loop story: LIKELY-FAIL. No ReelViewerFragment method takes a single
 *   Object parameter on 454, so StoryProgressCompletedFingerprint matches
 *   nothing; and no ReelViewerFragment method at all contains the seek
 *   INVOKE_INTERFACE (I,Z)V call piko's restart block needs, so the loop
 *   mechanism itself has no 454 equivalent in this class. The 439
 *   fingerprint is left as-is and the patch will probably fail again.
 * - Disable story flipping: RE-ANCHORED to StoryItemFinishedFingerprint.
 *   ReelViewerFragment.A1f(ReelItem)V is the story-item-finished handler
 *   on 454: it logs "end_scene", reads "userSession", resets the photo
 *   timer, and calls the advance (A0s) / exit (A1E) helpers. It is the
 *   only ReelViewerFragment method loading both "userSession" and
 *   "end_scene". Early return there stops the item-end processing, i.e.
 *   the story does not advance. Note it also skips the seen-marking in
 *   A0r, so a finished story may stay unmarked while flipping is off.
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.indexOfFirstStringInstructionOrThrow
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

internal object StoryProgressCompletedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
)

/**
 * 454 re-anchor for disableStoryFlipping only (see the file header): the
 * story-item-finished handler ReelViewerFragment.A1f(ReelItem)V. Unique in
 * the class by the "userSession" + "end_scene" strings. Loop story keeps
 * the 439 fingerprint above and is expected to fail on 454.
 */
internal object StoryItemFinishedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession", "end_scene"),
    parameters = listOf("Lcom/instagram/model/reels/ReelItem;"),
)

@Suppress("unused")
val loopStoryPatch =
    bytecodePatch(
        name = "Loop story",
        description = "Replay the current story when it ends, instead of moving on. " +
            "Off by default; turn it on from the Stories settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            StoryProgressCompletedFingerprint.method.apply {
                val entryCast = getInstruction(indexOfFirstInstructionOrThrow(Opcode.CHECK_CAST))
                val reelItemRegister = entryCast.registersUsed[0]
                val reelItemType = entryCast.getReference<TypeReference>()!!.type

                val restartIndex =
                    indexOfFirstInstructionReversedOrThrow(
                        indexOfFirstStringInstructionOrThrow("resume"),
                        Opcode.INVOKE_STATIC,
                    )

                val seekInstruction =
                    getInstruction(
                        indexOfFirstInstructionOrThrow(restartIndex) {
                            opcode == Opcode.INVOKE_INTERFACE &&
                                getReference<MethodReference>()?.let { reference ->
                                    reference.returnType == "V" &&
                                        reference.parameterTypes.map(CharSequence::toString) ==
                                        listOf("I", "Z")
                                } == true
                        },
                    )
                val positionRegister = seekInstruction.registersUsed[1]
                val playingRegister = seekInstruction.registersUsed[2]

                check(
                    p0Register > 0 &&
                        reelItemRegister > 0 &&
                        positionRegister < p0Register &&
                        playingRegister < p0Register,
                ) {
                    "Story restart block does not keep its state in local registers"
                }

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static { }, $CONFIG_CLASS->loopStory()Z
                    move-result v0
                    if-eqz v0, :piko_loop_entry
                    check-cast v$reelItemRegister, $reelItemType
                    const/4 v$playingRegister, 0x1
                    const/4 v$positionRegister, 0x0
                    goto :piko_loop
                    """.trimIndent(),
                    ExternalLabel("piko_loop_entry", getInstruction(0)),
                    ExternalLabel("piko_loop", getInstruction(restartIndex)),
                )
            }
        }
    }

@Suppress("unused")
val disableStoryFlippingPatch =
    bytecodePatch(
        name = "Disable story flipping",
        description = "Disable automatic flipping/moving to the next story. " +
            "Off by default; turn it on from the Stories settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            StoryItemFinishedFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static { }, $CONFIG_CLASS->disableStoryFlipping()Z
                    move-result v0
                    if-eqz v0, :piko_noflip
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_noflip", getInstruction(0)),
                )
            }
        }
    }
