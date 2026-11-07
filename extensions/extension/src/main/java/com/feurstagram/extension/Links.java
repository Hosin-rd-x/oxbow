package com.feurstagram.extension;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.net.URI;

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

    // --- Ported from piko's Links.interceptUri (crimera/piko, GPL-3.0; see
    // NOTICE) ---
    // Piko calls one interceptUri from TigonServiceLayer.startRequest and
    // switches on all five toggles inside it. Oxbow splits it into one
    // method per toggle so each declutter patch (LinkBlockPatches.kt) injects
    // only its own check: selecting one patch can never activate another
    // patch's block. The URI path rules are piko's, ported verbatim.
    // Throwing here makes Instagram treat the request as a network failure,
    // exactly like Block.throwIfBlocked does. Each Config getter below is
    // wired by the coordinator (see the port report); all default OFF.
    //
    // Note: piko's "direct_v2/pending_inbox/?visual_message" branch is kept
    // as-is, but getPath() strips the query string, so that branch can never
    // match — inert in Oxbow exactly as it is inert in piko.

    /** Stories endpoints (reels tray, latest reel media, hallpass, media stream). */
    public static void throwIfStoriesBlocked(URI uri) throws IOException {
        if (uri == null || !Config.disableStories()) return;
        String path = uri.getPath();
        if (path == null) return;
        if (path.contains("/feed/reels_tray/")
                || path.contains("feed/get_latest_reel_media/")
                || path.contains("direct_v2/pending_inbox/?visual_message")
                || path.contains("stories/hallpass/")
                || path.contains("/api/v1/feed/reels_media_stream/")) {
            throw new IOException("Blocked by Oxbow");
        }
    }

    /** Explore endpoints (topical explore + explore search). */
    public static void throwIfExploreBlocked(URI uri) throws IOException {
        if (uri == null || !Config.disableExplore()) return;
        String host = uri.getHost() != null ? uri.getHost() : "";
        String path = uri.getPath();
        if (path == null) return;
        if (path.contains("/discover/topical_explore")
                || path.contains("/discover/topical_explore_stream")
                || (host.contains("i.instagram.com") && path.contains("/fbsearch/recent_searches/"))
                || (host.contains("i.instagram.com") && path.contains("/fbsearch/top_serp/"))) {
            throw new IOException("Blocked by Oxbow");
        }
    }

    /** Comments endpoints on media. */
    public static void throwIfCommentsBlocked(URI uri) throws IOException {
        if (uri == null || !Config.disableComments()) return;
        String path = uri.getPath();
        if (path == null) return;
        if (path.contains("/api/v1/media/") && path.contains("comments/")) {
            throw new IOException("Blocked by Oxbow");
        }
    }

    /** "Discover people" endpoints (ayml / chaining) on profiles. */
    public static void throwIfDiscoverPeopleBlocked(URI uri) throws IOException {
        if (uri == null || !Config.disableDiscoverPeople()) return;
        String path = uri.getPath();
        if (path == null) return;
        if (path.contains("/discover/ayml") || path.contains("/discover/chaining")) {
            throw new IOException("Blocked by Oxbow");
        }
    }

    /** Highlights tray endpoint on profiles. */
    public static void throwIfHighlightsBlocked(URI uri) throws IOException {
        if (uri == null || !Config.disableHighlights()) return;
        String path = uri.getPath();
        if (path == null) return;
        if (path.contains("/highlights_tray")) {
            throw new IOException("Blocked by Oxbow");
        }
    }
}
