/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's DisableCommentsPatch,
 * DisableExplorePatch, DisableHighlightsPatch, DisableStoriesPatch (all under
 * links/distractionFree) and DisableDiscoverPeoplePatch (links/misc), which
 * targeted Instagram 439.0.0.37.89. In piko those five patches contain no
 * bytecode of their own: they only enable a Settings row, and the real work
 * is Links.interceptUri, called from TigonServiceLayer.startRequest by the
 * shared InterceptUriPatch. This file keeps that shape in Oxbow form:
 *
 * - One shared hook per patch into TigonServiceLayer.startRequest(*), the
 *   same anchor Oxbow's own NetworkBlockPatch already uses (class name and
 *   method-name prefix are unobfuscated and stable; "TigonServiceLayer" and
 *   "startRequest" both re-verified present in the 454.0.0.54.77 dex on
 *   7 Nov 2026, server /tmp/dexcheck).
 * - Each patch calls only its own Links.throwIf*Blocked check, so selecting
 *   one patch in the patcher never activates another patch's block; the
 *   Config toggle inside each check is the per-patch switch (default OFF).
 * - The URI path rules are piko's interceptUri rules, ported verbatim into
 *   Links.java (see there). One piko rule cannot match on getPath() — the
 *   "direct_v2/pending_inbox/?visual_message" branch carries a query string,
 *   which getPath() strips — and is ported as-is, so it stays inert in
 *   Oxbow exactly as it is inert in piko.
 */

package com.feurstagram.patches.piko

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val LINKS_CLASS = "Lcom/feurstagram/extension/Links;"

// Mirrors NetworkBlockPatch's anchor: Instagram 451 turned startRequest into
// a Systrace wrapper and moved its body into startRequestInternal, so the
// name is matched as a prefix and the method that actually reads the request
// URI is the one picked.
internal object DeclutterTigonStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    custom = { method, _ ->
        method.name.startsWith("startRequest") &&
            method.implementation?.instructions?.any { it.isUriLoad() } == true
    },
)

private fun Instruction.isUriLoad() =
    opcode == Opcode.IGET_OBJECT &&
        ((this as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/net/URI;"

/**
 * Inject a call to [linksMethod] right after the request URI is loaded, so
 * the thrown IOException is caught as a normal network failure and the
 * blocked surface stays empty. Shared by the five link-blocking patches.
 */
private fun invokeLinkBlock(linksMethod: String) {
    DeclutterTigonStartRequestFingerprint.method.apply {
        val uriLoad = instructions.first { it.isUriLoad() }
        val uriRegister = (uriLoad as OneRegisterInstruction).registerA

        addInstructions(
            uriLoad.location.index + 1,
            "invoke-static/range { v$uriRegister .. v$uriRegister }, " +
                "$LINKS_CLASS->$linksMethod(Ljava/net/URI;)V",
        )
    }
}

@Suppress("unused")
val disableCommentsPatch =
    bytecodePatch(
        name = "Disable comments",
        description = "Blocks the comments endpoints, so comments do not load. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            invokeLinkBlock("throwIfCommentsBlocked")
        }
    }

@Suppress("unused")
val disableExplorePatch =
    bytecodePatch(
        name = "Disable explore",
        description = "Blocks the Explore endpoints, so Explore stays empty. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            invokeLinkBlock("throwIfExploreBlocked")
        }
    }

@Suppress("unused")
val disableHighlightsPatch =
    bytecodePatch(
        name = "Disable highlights",
        description = "Blocks the highlights tray endpoint, so profile highlights do not load. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            invokeLinkBlock("throwIfHighlightsBlocked")
        }
    }

@Suppress("unused")
val disableStoriesPatch =
    bytecodePatch(
        name = "Disable stories",
        description = "Blocks the stories endpoints, so stories do not load. " +
            "Off by default; turn it on from the Feed settings page. Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            invokeLinkBlock("throwIfStoriesBlocked")
        }
    }

@Suppress("unused")
val disableDiscoverPeoplePatch =
    bytecodePatch(
        name = "Disable discover people",
        description = "Blocks the \"Discover people\" endpoints, so that section does not load " +
            "on profiles. Off by default; turn it on from the Feed settings page. " +
            "Ported from piko.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith(EXTENSION)

        execute {
            invokeLinkBlock("throwIfDiscoverPeopleBlocked")
        }
    }
