/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's FilterStory (crimera/piko, GPL-3.0;
 * see NOTICE), rewritten for Oxbow: no Entity base class and no PikoUtils.
 * The obfuscated getter names are resolved at patch time from Instagram's
 * own model serializer (see FilterStoriesPatch) and written into the stub
 * methods below by the patcher, exactly like Block.trayEntryTypeGetter.
 * The user check calls the verified getter directly on the User object
 * itself (User.A67 on 454), with no extra indirection.
 */

package com.feurstagram.extension;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Story-tray filter: called with every entry the tray parser builds.
 * Returns null for an entry the user's filters hide; the tray loop already
 * skips entries that parse to null, so the story never reaches the tray.
 * Anything unexpected (a getter that moved, a null user, a wrong type)
 * keeps the entry instead of breaking the tray.
 */
public final class FilterStory {

    private FilterStory() {}

    /** ReelResponseItemIntf getter for the reel type, resolved at patch time. */
    static String reelTypeGetter() {
        return "";
    }

    /** ReelResponseItemIntf getter for the media count, resolved at patch time. */
    static String mediaCountGetter() {
        return "";
    }

    /** ReelResponseItemIntf getter for the author User, resolved at patch time. */
    static String userGetter() {
        return "";
    }

    /** User getter for the verified flag, resolved at patch time. */
    static String isVerifiedGetter() {
        return "";
    }

    /** Call a no-arg getter by name, public if possible, declared otherwise. */
    private static Object invokeNoArg(Object target, String name) throws Exception {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        }
    }

    public static Object filter(Object entry) {
        if (entry == null) return null;
        try {
            Set<String> reelTypes = Config.filterStoryTypes();
            if (reelTypes != null && !reelTypes.isEmpty()) {
                String getter = reelTypeGetter();
                if (!getter.isEmpty()) {
                    Object type = invokeNoArg(entry, getter);
                    // The getter returns the reel-type enum; its toString is
                    // the JSON value ("ads_reel", "suggested_user", ...).
                    if (type != null && reelTypes.contains(String.valueOf(type))) return null;
                }
            }

            Set<String> userTypes = Config.filterStoryUserTypes();
            if (userTypes != null && !userTypes.isEmpty()) {
                String userGetter = userGetter();
                String verifiedGetter = isVerifiedGetter();
                if (!userGetter.isEmpty() && !verifiedGetter.isEmpty()) {
                    Object user = invokeNoArg(entry, userGetter);
                    if (user != null) {
                        Boolean verified = (Boolean) invokeNoArg(user, verifiedGetter);
                        boolean isVerified = Boolean.TRUE.equals(verified);
                        if (userTypes.contains("verified") && isVerified) return null;
                        if (userTypes.contains("unverified") && !isVerified) return null;
                    }
                }
            }

            String countGetter = mediaCountGetter();
            if (!countGetter.isEmpty()) {
                Object count = invokeNoArg(entry, countGetter);
                if (count instanceof Number) {
                    int mediaCount = ((Number) count).intValue();
                    if (mediaCount < Config.filterStoryMinItems()
                            || mediaCount > Config.filterStoryMaxItems()) return null;
                }
            }
        } catch (Throwable t) {
            return entry; // never break the tray over a shape we did not expect
        }
        return entry;
    }
}
