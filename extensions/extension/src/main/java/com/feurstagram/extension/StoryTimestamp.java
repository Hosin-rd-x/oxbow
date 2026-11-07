/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's StoryTimestamp (crimera/piko,
 * GPL-3.0; see NOTICE), rewritten for Oxbow: no PikoUtils/Logger, and the
 * format comes from {@link Config#customiseStoryTimestamp()} read fresh on
 * every call instead of once in a static initializer, so changing the
 * setting takes effect without a restart.
 */

package com.feurstagram.extension;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Custom story timestamps, called from the ReelItem timestamp formatter. */
public final class StoryTimestamp {

    private StoryTimestamp() {}

    /**
     * Format the story's posted time (seconds since epoch) according to the
     * setting, or null for the stock timestamp. "detailed" gives the full
     * weekday/date/time, "posttime" just the time, "timeleft" the time left
     * until the story expires (24h after posting).
     */
    public static String customiseStoryTimestamp(long postedTS) {
        String format = Config.customiseStoryTimestamp();
        if (format == null || "default".equals(format)) return null;

        String formattedTS = null;
        try {
            postedTS = postedTS * 1000;

            if ("detailed".equals(format)) {
                Date date = new Date(postedTS);
                SimpleDateFormat dateFormat =
                        new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());
                formattedTS = dateFormat.format(date);

            } else if ("posttime".equals(format)) {
                Date date = new Date(postedTS);
                SimpleDateFormat dateFormat =
                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                formattedTS = dateFormat.format(date);

            } else if ("timeleft".equals(format)) {
                long now = System.currentTimeMillis();
                long expireTS = postedTS + 86400000L; // +24 hours
                long diff = expireTS - now;

                if (diff <= 0) {
                    formattedTS = "0s";
                } else {
                    long totalSeconds = diff / 1000;

                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    formattedTS = hours + "h " + minutes + "m " + seconds + "s left";
                }
            }

        } catch (Exception e) {
            return null;
        }

        return formattedTS;
    }
}
