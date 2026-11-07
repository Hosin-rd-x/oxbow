/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's ChangeLikeAnimationPatch, which
 * targeted Instagram 439.0.0.37.89. The target class below is piko's 439
 * anchor; it was re-verified against the real 454.0.0.54.77 dex on 7 Nov 2026
 * (server /tmp/dexcheck, disassembled classes.dex):
 * Lcom/instagram/api/schemas/XDTUserActivationMetadataImpl exists with a
 * single <init>(LX/01qO, Boolean, Boolean, Boolean, Boolean, Boolean), where
 * LX/01qO is the like-animation enum (public final enum, holding
 * ARES_LIKE_ACTIVATION, RINGS_LIKE_ADRIAN, RINGS_LIKE_AKI_KOCHI, ...). So
 * parameters[0] is the animation enum and parameters[1] is the Boolean the
 * injected code flips — exactly as piko assumed. The patch run against the
 * 454 APK is the final proof.
 *
 * How it works: the extension class holds the placeholder string "className"
 * in its changeLikeAnimation method; this patch rewrites that first
 * const-string to the real enum class name, then at the start of the
 * constructor asks the extension for a replacement animation. A null answer
 * keeps the stock animation; a non-null one replaces p1 and sets p2 to TRUE.
 * The choice lives in Config.changeLikeAnimation() (key
 * "change_like_animation", default "ARES_LIKE_ACTIVATION" = stock, i.e. off).
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION
import com.feurstagram.patches.shared.freeLocals

private const val LIKE_EXTENSION_CLASS = "Lcom/feurstagram/extension/ChangeLikeAnimationPatch;"

internal object ChangeLikeAnimationExtensionFingerprint : Fingerprint(
    name = "changeLikeAnimation",
    definingClass = LIKE_EXTENSION_CLASS,
)

internal object XDTUserActivationMetadataImplInitFingerprint : Fingerprint(
    name = "<init>",
    definingClass = "Lcom/instagram/api/schemas/XDTUserActivationMetadataImpl;",
)

/** "Lcom/foo/Bar;" -> "com.foo.Bar", for Class.forName in the extension. */
private fun classNameToExtension(className: String): String =
    className.removePrefix("L").replace("/", ".").removeSuffix(";")

/**
 * Rewrites the const-string at [index] in the fingerprint's method.
 * Ported from piko's Fingerprint.changeStringAt (app.crimera.utils).
 */
context(patchContext: BytecodePatchContext)
private fun Fingerprint.changeStringAt(
    index: Int,
    value: String,
) {
    method.instructions.filter { it.opcode == Opcode.CONST_STRING }[index].let { instruction ->
        val register = (instruction as BuilderInstruction21c).registerA
        method.replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
    }
}

/** Rewrites the first const-string in the fingerprint's method. */
context(patchContext: BytecodePatchContext)
private fun Fingerprint.changeFirstString(value: String) {
    changeStringAt(0, value)
}

@Suppress("unused")
val changeLikeAnimationPatch =
    bytecodePatch(
        name = "Change like animation",
        description = "Change the animation to one from existing Rings like animations. " +
            "Off by default (stock animation); pick a Rings animation from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            XDTUserActivationMetadataImplInitFingerprint.method.apply {
                if (parameters.size < 2) {
                    throw PatchException("Like animation constructor has no animation and flag parameters")
                }
                val animationEnumClassType = parameters[0].type.toString()
                if (!animationEnumClassType.startsWith("L") ||
                    parameters[1].type.toString() != "Ljava/lang/Boolean;"
                ) {
                    throw PatchException("Like animation constructor parameters are not (enum, Boolean)")
                }
                if (freeLocals() < 1) {
                    throw PatchException("Like animation constructor has no free register for the hook")
                }

                ChangeLikeAnimationExtensionFingerprint.changeFirstString(
                    classNameToExtension(animationEnumClassType),
                )

                addInstructionsWithLabels(
                    0,
                    """
                    sget-object p2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    invoke-static {p1}, $LIKE_EXTENSION_CLASS->changeLikeAnimation(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :piko
                    sget-object p2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                    check-cast v0, $animationEnumClassType
                    move-object/from16 p1, v0
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }
        }
    }
