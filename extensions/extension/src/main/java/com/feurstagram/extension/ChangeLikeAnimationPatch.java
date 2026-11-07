/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's ChangeLikeAnimationPatch
 * (app.morphe.extension.instagram.patches.feed.ChangeLikeAnimationPatch).
 * The bytecode patch that calls into this class lives in
 * patches/.../piko/ChangeLikeAnimationPatch.kt.
 *
 * Oxbow differences from piko:
 * - No Pref/Entity/Logger: the chosen animation is read fresh from
 *   Config.changeLikeAnimation() (key "change_like_animation", default
 *   "ARES_LIKE_ACTIVATION" = the stock animation) on every call, and the
 *   enum value is resolved with Enum.valueOf instead of piko's Entity
 *   reflection helper.
 * - The string "className" is a placeholder: the bytecode patch replaces
 *   the first const-string of changeLikeAnimation with the real like
 *   animation enum class name (e.g. "X.01qO" on 454). It must stay the
 *   FIRST and ONLY const-string in this method — that is why the
 *   "ARES_LIKE_ACTIVATION" check lives in isDefaultAnimation, not here.
 */

package com.feurstagram.extension;

@SuppressWarnings({"unused", "unchecked", "rawtypes"})
public final class ChangeLikeAnimationPatch {

    private ChangeLikeAnimationPatch() {
    }

    private static boolean isDefaultAnimation(String pref) {
        return pref == null || "ARES_LIKE_ACTIVATION".equals(pref);
    }

    // This is required in order to change the like animation class enum type on runtime.
    public static Object changeLikeAnimation(Object defaultAnimation) {
        try {
            String pref = Config.changeLikeAnimation();
            if (isDefaultAnimation(pref)) {
                return null;
            }

            Class<?> animationEnumClass = Class.forName("className");
            return Enum.valueOf((Class<? extends Enum>) animationEnumClass, pref);
        } catch (Exception ignored) {
            return defaultAnimation;
        }
    }
}
