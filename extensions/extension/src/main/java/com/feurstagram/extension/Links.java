package com.feurstagram.extension;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Opening links in the external browser. Ported from piko's
 * Links.openExternally (crimera/piko, GPL-3.0; see NOTICE), rewritten for
 * Oxbow: no PikoUtils, the app context comes from {@link Config#getAppContext()}
 * and the toggle is {@link Config#isOpenLinksExternally()}.
 */
public final class Links {

    private Links() {}

    /**
     * Open {@code url} in the external browser when the toggle is on.
     * Instagram wraps outgoing links as https://l.instagram.com/?u=&lt;actual&gt;&amp;e=...,
     * so the real destination is unwrapped first. Returns true when the link was
     * opened externally (the in-app browser must then do nothing).
     */
    public static boolean openExternally(String url) {
        if (url == null || !Config.isOpenLinksExternally()) return false;
        try {
            String actual = Uri.parse(url).getQueryParameter("u");
            String target = actual != null ? actual : url;
            Context context = Config.getAppContext();
            if (context == null) return false;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
