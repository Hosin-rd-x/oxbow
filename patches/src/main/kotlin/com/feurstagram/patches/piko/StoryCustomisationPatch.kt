/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's CustomiseStoryRingSizePatch,
 * CustomiseStoryTimestampPatch and FilterStoriesPatch, which targeted
 * Instagram 439.0.0.37.89. All three were re-verified against the real
 * 454.0.0.54.77 dex before porting (see the notes on each fingerprint):
 *
 * - Ring size: piko's literal trio (66.0f, 100.0f, 3.75) still identifies
 *   exactly one void method on 454, LX/009R.<init>, and the 100.0f constant
 *   sits in v2 right before the dp-conversion call, exactly where piko's
 *   insert expects it. The other two methods holding the same trio return
 *   F, so the returnType V filter excludes them.
 * - Timestamp: ReelItem has exactly one String(Context) method on 454
 *   (A0k) and it contains LONG_TO_DOUBLE, as piko's patch expects.
 * - Filter stories: piko hooked the outer tray parser (parseFromJson with
 *   "tray" / "share_to_friends_story_pending_media" / "hallpass_share_info",
 *   which still exists on 454 as LX/04nv.unsafeParseFromJson). This port
 *   instead follows Oxbow's own SuggestedTrayEntriesPatch pattern and hooks
 *   the per-entry parser (shared ReelParserFingerprint), which is the
 *   proven hook in this codebase and drops an entry by returning null from
 *   FilterStory.filter. The tray getters are resolved from the model
 *   serializer at patch time, but NOT with the shared reelGetterNames
 *   helper: on 454 its "first getter between static put calls" window
 *   spans several fields and returns the wrong getter for media_count
 *   (a Boolean getter) and for user (a Long getter) — verified by reading
 *   the serializer LX/07ky.A02. The local adjacentReelGetter below takes
 *   the interface getter nearest the field's name string instead, and
 *   checks the return type (media_count -> Integer, user -> User).
 *   The verified flag comes from User.A67, the only method in the dex
 *   that loads "is_verified" and returns Boolean (LiveTreeUserDict, which
 *   piko preferred, does not exist on 454).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.feurstagram.patches.shared.CONFIG_CLASS
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION
import com.feurstagram.patches.shared.REEL_INTF
import com.feurstagram.patches.shared.ReelParserFingerprint
import com.feurstagram.patches.shared.ReelSerializerFingerprint
import com.feurstagram.patches.shared.returnConstant

private const val STORY_TIMESTAMP_CLASS = "Lcom/feurstagram/extension/StoryTimestamp;"
private const val FILTER_STORY_CLASS = "Lcom/feurstagram/extension/FilterStory;"

// Credits InstaPro/MyInsta (via piko).
internal object StoryRingBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            literal(66.0f),
            literal(100.0f),
            literal(3.75),
        ),
)

@Suppress("unused")
val customiseStoryRingSizePatch =
    bytecodePatch(
        name = "Customise story ring size",
        description = "Change the size of the story ring around avatars, as a percentage " +
            "of the stock size (100 = stock). Off by default; turn it on from the " +
            "Stories settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            StoryRingBuilderFingerprint.method.apply {
                // The 100.0f literal is the second of the three literal matches;
                // it is loaded into a register that the dp-conversion call right
                // after it reads, so overwriting that register with the
                // preference value resizes the ring. Verified on 454: the
                // literal sits at LX/009R.<init> index 283 (CONST_HIGH16 v2).
                val ringSizeLiteralIndex = StoryRingBuilderFingerprint.instructionMatches[1].index
                val register = getInstruction(ringSizeLiteralIndex).registersUsed[0]

                addInstructions(
                    ringSizeLiteralIndex + 1,
                    """
                    invoke-static { }, $CONFIG_CLASS->customiseStoryRingSize()F
                    move-result v$register
                    """.trimIndent(),
                )
            }
        }
    }

internal object ReelItemTimestampFormatMethodFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/model/reels/ReelItem;",
    parameters = listOf("Landroid/content/Context;"),
)

@Suppress("unused")
val customiseStoryTimestampPatch =
    bytecodePatch(
        name = "Customise story timestamp",
        description = "Customise the timestamp that shows when the story was posted. " +
            "Off by default; turn it on from the Stories settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            ReelItemTimestampFormatMethodFingerprint.method.apply {
                val longToDoubleIndex = indexOfFirstInstruction(Opcode.LONG_TO_DOUBLE)
                val longToDoubleInstruction = getInstruction(longToDoubleIndex)
                val longToDoubleRegisters = longToDoubleInstruction.registersUsed
                val dummyRegister = longToDoubleRegisters[0]
                val postedTimestampRegister = longToDoubleRegisters[1]

                addInstructionsWithLabels(
                    longToDoubleIndex,
                    """
                    invoke-static/range {v$postedTimestampRegister .. v${postedTimestampRegister + 1}}, $STORY_TIMESTAMP_CLASS->customiseStoryTimestamp(J)Ljava/lang/String;
                    move-result-object v$dummyRegister
                    if-eqz v$dummyRegister, :piko
                    return-object v$dummyRegister
                    """.trimIndent(),
                    ExternalLabel("piko", longToDoubleInstruction),
                )
            }
        }
    }

/**
 * The `ReelResponseItemIntf` getter for [field]: the no-arg interface call
 * nearest the field's JSON name in the model serializer. The serializer
 * writes each field as `const-string <name>` immediately next to its getter
 * (before it for nested models, after it for plain values), so the nearest
 * call is the field's own getter. When [expectedReturnType] is given, the
 * getter must return exactly that type, or the patch fails loudly instead
 * of silently filtering on the wrong field.
 */
private fun BytecodePatchContext.adjacentReelGetter(
    field: String,
    expectedReturnType: String?,
): String {
    val insns = ReelSerializerFingerprint.method.instructions.toList()
    val at =
        insns.indexOfFirst {
            (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                ((it as ReferenceInstruction).reference as StringReference).string == field
        }
    if (at < 0) throw PatchException("Story tray model: no $field in serializer")
    for (distance in 1..6) {
        for (index in listOf(at - distance, at + distance)) {
            if (index < 0 || index >= insns.size) continue
            val instruction = insns[index]
            if (instruction.opcode != Opcode.INVOKE_INTERFACE &&
                instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE
            ) continue
            val reference = (instruction as ReferenceInstruction).reference as? MethodReference
                ?: continue
            if (reference.definingClass != REEL_INTF || reference.parameterTypes.isNotEmpty()) continue
            if (expectedReturnType != null && reference.returnType != expectedReturnType) {
                throw PatchException(
                    "Story tray model: getter for $field returns ${reference.returnType}, " +
                        "expected $expectedReturnType",
                )
            }
            return reference.name
        }
    }
    throw PatchException("Story tray model: no getter next to $field")
}

internal object FilterStoryReelTypeGetterFingerprint : Fingerprint(
    definingClass = FILTER_STORY_CLASS,
    name = "reelTypeGetter",
)

internal object FilterStoryMediaCountGetterFingerprint : Fingerprint(
    definingClass = FILTER_STORY_CLASS,
    name = "mediaCountGetter",
)

internal object FilterStoryUserGetterFingerprint : Fingerprint(
    definingClass = FILTER_STORY_CLASS,
    name = "userGetter",
)

internal object FilterStoryIsVerifiedGetterFingerprint : Fingerprint(
    definingClass = FILTER_STORY_CLASS,
    name = "isVerifiedGetter",
)

/** The `is_verified` getter on the user model itself (User.A67 on 454). */
internal object UserIsVerifiedFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/user/model/User;",
    strings = listOf("is_verified"),
    returnType = "Ljava/lang/Boolean;",
)

// Heavily based on @brosssh work (via piko).
// https://github.com/brosssh/instagram-morphe-patches-library/blob/dev/patch-library/src/main/kotlin/app/morphe/library/instagram/patches/FilterStoriesListPatch.kt
@Suppress("unused")
val filterStoriesPatch =
    bytecodePatch(
        name = "Filter stories",
        description = "Filter stories to hide based on different categories (story type, " +
            "verified/unverified authors, number of items). Off by default; turn it " +
            "on from the Story filters settings. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            // reel_type returns the reel-type enum; its toString is the JSON
            // value ("ads_reel", "suggested_user", ...), which is what the
            // setting stores. media_count is an Integer, user is a User.
            FilterStoryReelTypeGetterFingerprint.method.returnConstant(
                adjacentReelGetter("reel_type", null),
            )
            FilterStoryMediaCountGetterFingerprint.method.returnConstant(
                adjacentReelGetter("media_count", "Ljava/lang/Integer;"),
            )
            FilterStoryUserGetterFingerprint.method.returnConstant(
                adjacentReelGetter("user", "Lcom/instagram/user/model/User;"),
            )
            FilterStoryIsVerifiedGetterFingerprint.method.returnConstant(
                UserIsVerifiedFingerprint.method.name,
            )

            // Same returns hook as SuggestedTrayEntriesPatch: every entry the
            // tray parser builds goes through FilterStory.filter, which turns
            // a filtered-out entry into null, and the tray loop already skips
            // entries that parse to null.
            ReelParserFingerprint.method.apply {
                val returns = instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                    .map { it.index to (it.value as OneRegisterInstruction).registerA }
                    .reversed()
                for ((index, register) in returns) {
                    addInstructions(
                        index,
                        "invoke-static/range { v$register .. v$register }, " +
                            "$FILTER_STORY_CLASS->filter(Ljava/lang/Object;)Ljava/lang/Object;\n" +
                            "move-result-object v$register",
                    )
                }
            }
        }
    }
