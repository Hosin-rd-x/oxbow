package com.feurstagram.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.feurstagram.extension.theme.MaterialYouTheme;

/**
 * Feurstagram settings, shown as a full-screen page when the Home tab is
 * long-pressed. Built entirely in code (no bundled resources) and persisted
 * through {@link Config}.
 *
 * <h3>The look: Material 3 Expressive, monochrome</h3>
 * White is the only accent — no hue anywhere, so the panel reads as part of a
 * dark OS rather than as a themed app. The Expressive part is in the shapes and
 * the type: large corner radii, options grouped into "connected" lists where the
 * group's outer corners are round and the joints between rows are nearly square,
 * pill-shaped buttons, sentence-case section labels, and a tight-tracked headline.
 * This class also owns the palette, type and shape helpers the other Feurstagram
 * surfaces ({@link UpdateChecker}, {@link Onboarding}) draw with, so the design
 * only lives in one place.
 *
 * <h3>Fitting any screen</h3>
 * Instagram targets a recent SDK, so its windows are edge-to-edge: system bars
 * and display cutouts overlap the dialog and nothing is inset for us. Every
 * Feurstagram window therefore goes through {@link #applyWindowInsets} and pads
 * itself — otherwise the action buttons sit under a three-button navigation bar
 * and the scroll area runs off the bottom of the screen. Content that can grow
 * (the settings list, a release-notes block) lives in a scroller bounded by what
 * is actually on screen, so a short screen or landscape shrinks it instead of
 * pushing the buttons away.
 */
public final class Settings {

    // --- Colour: a neutral dark scheme with white as the single accent --------

    /** Page background. */
    public static final int SURFACE = 0xFF0E0E0E;
    /** Cards, list rows. */
    public static final int SURFACE_CONTAINER = 0xFF1B1B1B;
    /** One step up: tonal buttons, the raised confirm card. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFF262626;
    public static final int ON_SURFACE = 0xFFF4F4F4;
    public static final int ON_SURFACE_VARIANT = 0xFFABABAB;
    public static final int OUTLINE = 0xFF757575;
    public static final int OUTLINE_VARIANT = 0xFF3A3A3A;
    /** The accent. Monochrome by design: white. */
    public static final int PRIMARY = 0xFFFFFFFF;

    static final String SPONSORS_URL = "https://github.com/sponsors/aic0d3r";
    public static final int ON_PRIMARY = 0xFF101010;
    /**
     * Destructive actions. A monochrome scheme has no red to lean on, so they
     * carry the same accent and are distinguished by their wording instead.
     */
    public static final int ERROR = 0xFFFFFFFF;
    public static final int ON_ERROR = 0xFF101010;
    public static final int DIVIDER = 0xFF2A2A2A;
    public static final int RIPPLE = 0x26FFFFFF;

    // --- Shape: the M3 Expressive corner scale --------------------------------

    /** Dialog / card corners. */
    private static final float CORNER_XL = 28f;
    /** The outer corners of a connected list group. */
    private static final float CORNER_L = 24f;
    /** The joints inside a connected list group. */
    private static final float CORNER_JOINT = 4f;
    /** Larger than half a view's height, so it renders as a pill. */
    private static final float CORNER_FULL = 200f;
    /** Gap between the rows of a connected group. */
    private static final int ROW_GAP_DP = 3;

    private Settings() {}

    /**
     * Install the Feurstagram entry points off the bottom tab bar: the long-press
     * settings gesture, the UI hiders, and the launch update check. The tab bar is
     * just a stable handle into the window's view tree.
     */
    public static void installHomeTabWatcher(ViewGroup tabBar) {
        if (tabBar == null) return;
        tabBar.getViewTreeObserver().addOnGlobalLayoutListener(new HomeTabWatcher(tabBar));
        Hiders.installAll(tabBar);
        Context activity = getActivityContext(tabBar);
        // Oxbow: no upstream follow card; "What's new" shows right away.
        UpdateChecker.checkWhatsNew(activity);
        UpdateChecker.check(activity);
    }

    /** Unwrap a view's context down to the hosting Activity when possible. */
    public static Context getActivityContext(View view) {
        if (view == null) return null;
        Context context = view.getContext();
        Context cursor = context;
        while (cursor != null && !(cursor instanceof Activity) && cursor instanceof ContextWrapper) {
            cursor = ((ContextWrapper) cursor).getBaseContext();
        }
        return cursor != null ? cursor : context;
    }

    /** How far the settings page rises while it opens. */
    private static final int OPEN_RISE_DP = 32;
    /** Length of the settings page's opening animation. */
    private static final long OPEN_DURATION_MS = 280L;

    public static void show(Context context) {
        if (context == null) return;
        try {
            SettingsDialog dialog = new SettingsDialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            View content = buildContent(context, dialog);
            dialog.page = content;
            dialog.setContentView(content);

            // Opening animation: the page rises and fades in over Instagram. The window
            // itself stays transparent (the page paints its own background), so what is
            // behind shows through until the page has settled.
            content.setAlpha(0f);
            content.setTranslationY(dp(context, OPEN_RISE_DP));
            dialog.setOnShowListener(d -> content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(OPEN_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start());

            // Intercept Back (incl. predictive-back gesture) to restart on change.
            dialog.setOnCancelListener(d -> {
                if (Config.isRestartPending()) {
                    CacheCleaner.clearAndRestart(context);
                }
            });

            styleWindow(dialog, Color.TRANSPARENT, 0f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context, "Oxbow settings unavailable here", Toast.LENGTH_LONG).show();
        }
    }

    // --- Window plumbing ------------------------------------------------------

    /**
     * Make a dialog window fill the screen edge to edge with light system-bar
     * icons, so the content underneath can pad itself against the bars and the
     * cutout. Every Feurstagram dialog goes through here.
     *
     * @param background window background colour, or 0 for a transparent scrim
     * @param dim        how much to darken what is behind (0 for the full-screen page)
     */
    public static void styleWindow(Dialog dialog, int background, float dim) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(background));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setDimAmount(dim);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        // Draw behind the bars and let the content pad itself. Without this the
        // window may not receive insets at all, and the padding below would be
        // computed from zeros.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Deprecated (and ignored) from API 35, but still what tints the bars on
        // everything older.
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // The surface underneath is dark, so the bar icons must be light — the
        // host activity may have asked for the opposite.
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /** Receives the space taken by system bars and display cutouts, in pixels. */
    public interface InsetTarget {
        void onInsets(int left, int top, int right, int bottom);
    }

    /**
     * Report the window's system-bar + cutout insets to {@code target}, now and on
     * every change (rotation, switching to three-button navigation, a foldable
     * unfolding).
     */
    public static void applyWindowInsets(View view, InsetTarget target) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.onInsets(left, top, right, bottom);
            return insets;
        });
        view.requestApplyInsets();
    }

    /**
     * Widest a column of text and controls is allowed to get. Past this a tablet,
     * an unfolded foldable or a landscape phone gives rows so wide the label and
     * its switch end up at opposite edges of the screen.
     */
    private static final int CONTENT_MAX_WIDTH_DP = 620;

    /**
     * Pad a full-screen root against the system bars and the cutout, and keep its
     * content centred within {@code maxWidthDp}.
     *
     * The width part has to run on every layout pass rather than only when the
     * insets change: {@code requestApplyInsets()} is asynchronous, so the first
     * layout happens with no insets at all, and a root that only listened for
     * insets would keep whatever it measured then.
     */
    public static void applyPageInsets(final View root, final int sideDp, final int verticalDp,
                                       final int maxWidthDp) {
        final Context context = root.getContext();
        final int[] insets = new int[4];
        Runnable apply = () -> {
            int usable = root.getWidth() - insets[0] - insets[2];
            int extra = Math.max(0, usable - dp(context, maxWidthDp)) / 2;
            int left = dp(context, sideDp) + insets[0] + extra;
            int top = dp(context, verticalDp) + insets[1];
            int right = dp(context, sideDp) + insets[2] + extra;
            int bottom = dp(context, verticalDp) + insets[3];
            if (root.getPaddingLeft() == left && root.getPaddingTop() == top
                    && root.getPaddingRight() == right && root.getPaddingBottom() == bottom) {
                return;
            }
            root.setPadding(left, top, right, bottom);
        };
        applyWindowInsets(root, (l, t, r, b) -> {
            insets[0] = l;
            insets[1] = t;
            insets[2] = r;
            insets[3] = b;
            apply.run();
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(apply::run);
    }

    // --- The full-screen page scaffold ----------------------------------------

    /**
     * The shape every Feurstagram full-screen surface takes: a headline block, a
     * scrolling middle that absorbs whatever space is left, and an action row
     * pinned above the navigation bar. Settings and both update screens are built
     * on it, so they inset, centre and shrink identically.
     */
    public static final class Page {
        public final LinearLayout root;
        public final LinearLayout header;
        public final ScrollView scroll;
        public final LinearLayout content;
        public final LinearLayout actions;

        Page(LinearLayout root, LinearLayout header, ScrollView scroll,
             LinearLayout content, LinearLayout actions) {
            this.root = root;
            this.header = header;
            this.scroll = scroll;
            this.content = content;
            this.actions = actions;
        }

        /** Add a pill that shares the action row equally with its siblings. */
        public Button addAction(Button button) {
            Context context = actions.getContext();
            if (actions.getChildCount() > 0) {
                View spacer = new View(context);
                actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));
            }
            actions.addView(button, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return button;
        }
    }

    public static Page newPage(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        // The fading edge is drawn inside the scroller's padding, so bottom
        // padding here would leave a band of unfaded rows under the fade. The
        // breathing room goes on the content instead, keeping the fade flush
        // with the scroller's edge, right above the action row.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(context, 12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        return new Page(root, header, scroll, content, actions);
    }

    /** Headline plus supporting line, in a page's header block. */
    public static void addPageTitle(Page page, String titleText, String subtitleText) {
        Context context = page.root.getContext();
        TextView title = new TextView(context);
        title.setText(titleText);
        headline(title);
        page.header.addView(title);

        if (subtitleText != null) {
            TextView subtitle = new TextView(context);
            subtitle.setText(subtitleText);
            body(subtitle);
            subtitle.setPadding(0, dp(context, 6), 0, 0);
            page.header.addView(subtitle);
        }
    }

    /** A full-screen page dialog, styled and edge-to-edge. */
    public static Dialog newPageDialog(Context context, View content) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        styleWindow(dialog, SURFACE, 0f);
        return dialog;
    }

    // --- The settings page ----------------------------------------------------

    /**
     * The settings window. Its pages share one dialog: Back on a section page
     * returns to the overview instead of closing settings. The platform routes
     * both the Back key and the predictive-back gesture through onBackPressed.
     */
    private static final class SettingsDialog extends Dialog {
        /** Set while a section page is open. */
        Runnable onBack;
        View page;
        private boolean closing;

        SettingsDialog(Context context) {
            super(context, android.R.style.Theme_Material_NoActionBar);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onBackPressed() {
            if (onBack != null) {
                onBack.run();
                return;
            }
            cancel();
        }

        @Override
        public void cancel() {
            if (closing) return;
            if (page == null || !isShowing() || !ValueAnimator.areAnimatorsEnabled()) {
                super.cancel();
                return;
            }
            closing = true;
            page.animate().cancel();
            page.animate().alpha(0f).translationY(dp(getContext(), OPEN_RISE_DP))
                    .setDuration(200L)
                    .setInterpolator(new AccelerateInterpolator(1.5f))
                    .withEndAction(this::finishCancel).start();
        }

        private void finishCancel() {
            super.cancel();
        }
    }

    /** Fills a section page's column. */
    private interface SectionBuilder {
        void build(Context context, LinearLayout column);
    }

    /** How far a page slides while it is swapped in. */
    private static final int PAGE_SLIDE_DP = 24;
    /** Length of the page swap. */
    private static final long PAGE_SWAP_MS = 180L;

    /**
     * The settings pages: an overview of sections, each opening its own page in
     * the same window. Header, scroller and the pinned action row are shared;
     * only the title, the back arrow and the column's rows change.
     */
    private static final class Pages {
        final Context context;
        final SettingsDialog dialog;
        final boolean hardcore;
        final View back;
        final TextView title;
        final TextView subtitle;
        final ScrollView scroll;
        final LinearLayout column;

        Pages(Context context, SettingsDialog dialog, boolean hardcore, View back, TextView title,
              TextView subtitle, ScrollView scroll, LinearLayout column) {
            this.context = context;
            this.dialog = dialog;
            this.hardcore = hardcore;
            this.back = back;
            this.title = title;
            this.subtitle = subtitle;
            this.scroll = scroll;
            this.column = column;
        }

        void showOverview(boolean animate) {
            dialog.onBack = null;
            back.setVisibility(View.GONE);
            title.setText("Oxbow");
            subtitle.setText(hardcore
                    ? "Permanent lock is on. You can tighten blocks but not loosen them. "
                            + "Reinstall to fully unlock."
                    : Config.isCooldownEnabled()
                    ? "Cooldown is on: loosening a block waits " + Config.getCooldownHours()
                            + " h. Tap Done to clear the cache and restart."
                    : "Choose what to hide. Tap Done to clear the cache and restart.");
            swap(animate, -1, () -> buildOverview(context, column, this));
        }

        void open(String name, String support, SectionBuilder builder) {
            dialog.onBack = () -> showOverview(true);
            back.setVisibility(View.VISIBLE);
            title.setText(name);
            subtitle.setText(support);
            swap(true, 1, () -> builder.build(context, column));
        }

        /** Rebuild the column, sliding in from the side the user is heading to. */
        private void swap(boolean animate, int direction, Runnable fill) {
            column.removeAllViews();
            fill.run();
            scroll.scrollTo(0, 0);
            if (!animate) return;
            column.setAlpha(0f);
            column.setTranslationX(direction * dp(context, PAGE_SLIDE_DP));
            column.animate().alpha(1f).translationX(0f).setDuration(PAGE_SWAP_MS)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private static View buildContent(Context context, SettingsDialog dialog) {
        // Snapshot block state so the permanent lock freezes only what's blocked now.
        Config.captureBaseline();
        boolean hardcore = Config.isHardcoreMode();

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        // Back arrow above the title, aligned with it: the 48dp touch target
        // reaches 12dp past the column's edge, as an M3 top app bar's does.
        ImageView back = new ImageView(context);
        back.setImageDrawable(igIcon(context, ON_SURFACE, "instagram_arrow_left_pano_outline_24",
                "instagram_arrow_left_outline_24"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Back");
        back.setBackground(ripple(RIPPLE, oval(Color.TRANSPARENT)));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48));
        backLp.setMargins(-dp(context, 12), 0, 0, dp(context, 4));
        header.addView(back, backLp);

        TextView title = new TextView(context);
        headline(title);
        header.addView(title);

        TextView subtitle = new TextView(context);
        body(subtitle);
        subtitle.setPadding(0, dp(context, 6), 0, 0);
        header.addView(subtitle);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        // The fading edge is drawn inside the scroller's padding, so bottom
        // padding here would leave a band of unfaded rows under the fade. The
        // breathing room goes on the content instead, keeping the fade flush
        // with the scroller's edge, right above the action row.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, dp(context, 12));
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Pages pages = new Pages(context, dialog, hardcore, back, title, subtitle, scroll, column);
        back.setOnClickListener(v -> pages.showOverview(true));
        pages.showOverview(false);

        // Pinned action bar, on every page, so Done is always one tap away. Two
        // equal pills rather than end-aligned buttons: it cannot overflow, however
        // narrow the screen or long the label.
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        Button lock = makeButton(context, "Permanent lock", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        lock.setOnClickListener(v -> {
            dialog.dismiss();
            confirmHardcore(context);
        });
        if (hardcore) {
            lock.setEnabled(false);
            lock.setAlpha(0.38f);
        }
        actions.addView(lock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View spacer = new View(context);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

        Button done = makeButton(context, "Done", PRIMARY, ON_PRIMARY, true);
        done.setOnClickListener(v -> {
            dialog.dismiss();
            onDone(context);
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return root;
    }

    // --- The overview and its sections -----------------------------------------

    /** Keys behind the "Blocked surfaces" page, in display order. */
    private static final String[] SURFACE_KEYS = {
            "block_feed", "block_explore", "block_reels", "block_friends_lane", "block_stories",
            "block_instants", "block_notes", "block_suggested", "block_ads", "block_meta_ai", "block_notifications",
            "block_comments", "block_endless_scroll", "block_story_swipe_up",
    };

    private static void buildOverview(Context context, LinearLayout column, Pages pages) {
        LinearLayout content = addSection(context, column, "Content");
        int hidden = 0;
        for (String key : SURFACE_KEYS) {
            if (isSurfaceHidden(key)) hidden++;
        }
        addSectionRow(context, content, "eye_off", "Blocked surfaces",
                hidden + " of " + SURFACE_KEYS.length + " hidden",
                () -> pages.open("Blocked surfaces", "Hide parts of Instagram.", Settings::buildSurfaces));
        addSectionRow(context, content, "layout", "Navigation bar", navSummary(context),
                () -> pages.open("Navigation bar", "Choose the icons on the bottom bar.", Settings::buildNav));
        addSectionRow(context, content, "home", "Feed",
                Config.isFollowingFeedOnly() ? "Following only" : "Recommended",
                () -> pages.open("Feed", "How the home feed is put together.", Settings::buildFeed));
        addSectionRow(context, content, "flag", "Landing page", landingLabel(Config.getLandingPage()),
                () -> pages.open("Landing page", "Where Instagram opens on a cold start.",
                        Settings::buildLanding));
        sealGroup(context, content);

        LinearLayout keep = addSection(context, column, "Oxbow");
        addSectionRow(context, keep, "save", "Keep and privacy", keepSummary(),
                () -> pages.open("Keep and privacy", "Save what matters, leave no trace.",
                        Settings::buildKeep));
        addSectionRow(context, keep, "lock", "Lock", lockSummary(),
                () -> pages.open("Lock", "Slow yourself down before undoing a block.", Settings::buildLock));
        sealGroup(context, keep);

        LinearLayout app = addSection(context, column, "App");
        addSectionRow(context, app, "moon", "Display and popups", displaySummary(),
                () -> pages.open("Display and popups", "How Instagram looks and talks to you.",
                        Settings::buildDisplay));
        addSectionRow(context, app, "download", "Updates",
                Config.isAutoUpdateEnabled() ? "Automatic check on" : "Automatic check off",
                () -> pages.open("Updates", "Keep Oxbow current.", Settings::buildUpdates));
        addSectionRow(context, app, "heart", "Support", "Help keep the project going",
                () -> pages.open("Support", "Oxbow is free and open source. Sponsoring keeps it working with new Instagram versions.", Settings::buildSupport));
        sealGroup(context, app);
    }

    /** Whether a surface is hidden, with that key's own default. */
    private static boolean isSurfaceHidden(String key) {
        switch (key) {
            case "block_feed": return Config.isFeedBlocked();
            case "block_explore": return Config.isExploreBlocked();
            case "block_reels": return Config.isReelsBlocked();
            case "block_friends_lane": return Config.isFriendsLaneBlocked();
            case "block_stories": return Config.isStoriesBlocked();
            case "block_instants": return Config.isInstantsBlocked();
            case "block_notes": return Config.isNotesBlocked();
            case "block_suggested": return Config.isSuggestedBlocked();
            case "block_ads": return Config.isAdsBlocked();
            case "block_meta_ai": return Config.isMetaAiBlocked();
            case "block_notifications": return Config.isNotificationsButtonBlocked();
            case "block_comments": return Config.isCommentsBlocked();
            case "block_endless_scroll": return Config.isEndlessScrollBlocked();
            case "block_story_swipe_up": return Config.isStorySwipeUpBlocked();
            default: return Config.getBlocked(key, false);
        }
    }

    private static String surfaceLabel(String key) {
        switch (key) {
            case "block_feed": return "Home feed";
            case "block_explore": return "Explore";
            case "block_reels": return "Reels";
            case "block_friends_lane": return "Friends in Reels";
            case "block_stories": return "Stories";
            case "block_instants": return "Instants";
            case "block_notes": return "Notes";
            case "block_suggested": return "Suggested accounts";
            case "block_ads": return "Ads";
            case "block_meta_ai": return "Meta AI and Muse";
            case "block_notifications": return "Notifications button";
            case "block_comments": return "Comments";
            case "block_endless_scroll": return "Endless scrolling";
            case "block_story_swipe_up": return "Story swipe-up";
            default: return key;
        }
    }

    private static void buildSurfaces(Context context, LinearLayout column) {
        LinearLayout surfaces = addGroup(context, column);
        for (String key : SURFACE_KEYS) {
            addRow(context, surfaces, surfaceLabel(key), key, isSurfaceHidden(key));
        }
        sealGroup(context, surfaces);
    }

    private static void buildNav(Context context, LinearLayout column) {
        addSectionLabel(context, column, "Preview");
        NavPreview preview = new NavPreview(context);
        column.addView(preview.view);

        LinearLayout nav = addSection(context, column, "Icons");
        Runnable refresh = preview::refresh;
        addRow(context, nav, "Home", "nav_show_home", Config.getBlocked("nav_show_home", true), refresh);
        addRow(context, nav, "Search", "nav_show_search", Config.getBlocked("nav_show_search", true), refresh);
        addRow(context, nav, "Reels", "nav_show_reels", Config.isReelsTabShown(), refresh);
        addRow(context, nav, "Create", "nav_show_create", Config.getBlocked("nav_show_create", true), refresh);
        addRow(context, nav, "Messages", "nav_show_direct", Config.getBlocked("nav_show_direct", true), refresh);
        addRow(context, nav, "Profile", "nav_show_profile", Config.getBlocked("nav_show_profile", true), refresh);
        sealGroup(context, nav);

        // Round-2 piko port: Oxbow's own tab model (order + visibility +
        // startup tab), separate from the icon toggles above. The patch gates
        // all three hooks on the master toggle, so off means stock behaviour.
        LinearLayout custom = addSection(context, column, "Custom tabs");
        addRow(context, custom, "Customize navigation bar", "customize_navigation_bar", Config.customizeNavigationBar());
        addRow(context, custom, "Remove empty bottom space", "remove_empty_bottom_space", Config.removeEmptyBottomSpace());
        sealGroup(context, custom);
        buildNavTabPicker(context, column);
        buildNavStartupChoice(context, column);

        // Instagram drops tabs between versions (451 has no Create tab). A toggle
        // for a tab the live bar does not have would do nothing, so it is greyed
        // out; outside the app the order is a guess and every row stays live.
        if (NavPreview.isLive(context)) {
            String[] tabs = {"feed_tab", "search_tab", "clips_tab", "creation_tab", "direct_tab", "profile_tab"};
            List<String> present = NavPreview.barOrder(context);
            for (int i = 0; i < tabs.length && i < nav.getChildCount(); i++) {
                if (present.contains(tabs[i])) continue;
                View row = nav.getChildAt(i);
                if (row instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) row;
                    for (int j = 0; j < group.getChildCount(); j++) group.getChildAt(j).setEnabled(false);
                }
                row.setEnabled(false);
                row.setClickable(false);
                row.setAlpha(0.38f);
            }
        }
    }

    private static void buildFeed(Context context, LinearLayout column) {
        LinearLayout feed = addGroup(context, column);
        addRow(context, feed, "Following feed only", "limit_following_feed", Config.isFollowingFeedOnly());
        addRow(context, feed, "Disable video autoplay", "disable_video_autoplay", Config.isVideoAutoplayDisabled());
        sealGroup(context, feed);

        // Round-2 piko ports: one toggle per hidden element, all default off.
        LinearLayout declutter = addSection(context, column, "Declutter");
        addRow(context, declutter, "Hide reshare button", "hide_reshare_button", Config.hideReshareButton());
        addRow(context, declutter, "Hide notes tray", "hide_notes_tray", Config.hideNotesTray());
        addRow(context, declutter, "Hide group creation button on sharesheet", "hide_group_creation_on_sharesheet", Config.hideGroupCreationOnSharesheet());
        addRow(context, declutter, "Disable swipe to create", "disable_swipe_to_create", Config.disableSwipeToCreate());
        addRow(context, declutter, "Disable comments", "disable_comments", Config.disableComments());
        addRow(context, declutter, "Disable explore", "disable_explore", Config.disableExplore());
        addRow(context, declutter, "Disable highlights", "disable_highlights", Config.disableHighlights());
        addRow(context, declutter, "Disable stories", "disable_stories", Config.disableStories());
        addRow(context, declutter, "Disable discover people", "disable_discover_people", Config.disableDiscoverPeople());
        addRow(context, declutter, "Hide stories tray", "hide_stories_tray", Config.hideStoriesTray());
        sealGroup(context, declutter);

        // Round-2 piko port: the like animation is one of Instagram's own
        // built-in animations; ARES_LIKE_ACTIVATION is the stock one.
        LinearLayout like = addSection(context, column, "Like animation");
        buildChoiceOptions(context, like, LIKE_ANIMATION_VALUES, LIKE_ANIMATION_LABELS,
                Config.changeLikeAnimation(), Config::setChangeLikeAnimation);
        sealGroup(context, like);
    }

    /** Piko's like-animation list (piko_array_change_like_animation[_val]). */
    private static final String[] LIKE_ANIMATION_VALUES = {
            "ARES_LIKE_ACTIVATION", "ANYWAY_LIKE_ACTIVATION", "RINGS_LIKE_ADRIAN",
            "RINGS_LIKE_AKI_KOCHI", "RINGS_LIKE_BRIAN", "RINGS_LIKE_BRICKLEY",
            "RINGS_LIKE_COLE", "RINGS_LIKE_DJAG", "RINGS_LIKE_DOLLY", "RINGS_LIKE_ELYSE",
            "RINGS_LIKE_FILM", "RINGS_LIKE_FUTURA", "RINGS_LIKE_GABRIEL",
            "RINGS_LIKE_GOLLORIA", "RINGS_LIKE_HOMESTEAD", "RINGS_LIKE_KIDS",
            "RINGS_LIKE_LAUFEY", "RINGS_LIKE_LINDA", "RINGS_LIKE_MIMLES",
            "RINGS_LIKE_NIGEL", "RINGS_LIKE_NINA", "RINGS_LIKE_OLIVIA",
            "RINGS_LIKE_SEB", "RINGS_LIKE_TWINS", "RINGS_LIKE_TYSHAWN", "RINGS_LIKE_ZARNA"};
    private static final String[] LIKE_ANIMATION_LABELS = {
            "Default", "Anyway", "Adrian", "Aki kochi", "Brian", "Brickley",
            "Cole", "DJAG", "Dolly", "Elyse", "Film", "Futura", "Gabriel",
            "Golloria", "Homestead", "Kids", "Laufey", "Linda", "Mimles",
            "Nigel", "Nina", "Olivia", "Seb", "Twins", "Tyshawn", "Zarna"};

    private static void buildLanding(Context context, LinearLayout column) {
        LinearLayout landing = addGroup(context, column);
        buildLandingOptions(context, landing, new Runnable[1]);
        sealGroup(context, landing);
    }

    private static void buildKeep(Context context, LinearLayout column) {
        LinearLayout keep = addSection(context, column, "Keep");
        addRow(context, keep, "Downloads", "download_enabled", Config.isDownloadEnabled());
        addRow(context, keep, "Copy caption", "copy_caption", Config.isCopyCaptionEnabled());
        addRow(context, keep, "Download all slides", "download_all_slides", Config.isDownloadAllSlides());
        addRow(context, keep, "Clean shared links", "clean_links", Config.isCleanLinks());
        addRow(context, keep, "Open links externally", "open_links_externally", Config.isOpenLinksExternally());
        sealGroup(context, keep);

        LinearLayout ghost = addSection(context, column, "Ghost mode");
        addRow(context, ghost, "Don't mark stories as seen", "ghost_story_seen", Config.isStorySeenHidden());
        addRow(context, ghost, "Don't send read receipts", "ghost_dm_seen", Config.isDmSeenHidden());
        addRow(context, ghost, "Don't show typing", "ghost_typing", Config.isTypingHidden());
        addRow(context, ghost, "Hide screenshots", "ghost_screenshot", Config.isScreenshotHidden());
        addRow(context, ghost, "Long-press New message", "ghost_shortcut", Config.isGhostShortcut());
        sealGroup(context, ghost);

        LinearLayout stories = addSection(context, column, "Stories");
        addRow(context, stories, "Replay story when it ends", "loop_story", Config.loopStory());
        addRow(context, stories, "Don't auto-advance stories", "disable_story_flipping", Config.disableStoryFlipping());
        sealGroup(context, stories);

        // Round-2 piko ports: story ring size and timestamp. Ring size is a
        // number (100 is the stock size); the timestamp is a choice list.
        LinearLayout storyStyle = addSection(context, column, "Story style");
        addNumberRow(context, storyStyle, "Customise story ring size",
                "Size of the ring around story avatars. 100 is the stock size.",
                String.valueOf(Config.customiseStoryRingSize()), true,
                text -> {
                    float value = Float.parseFloat(text);
                    if (value < 10f) value = 10f;
                    if (value > 200f) value = 200f;
                    Config.setCustomiseStoryRingSize(value);
                    return String.valueOf(value);
                });
        sealGroup(context, storyStyle);
        LinearLayout storyTime = addSection(context, column, "Story timestamp");
        buildChoiceOptions(context, storyTime, STORY_TIMESTAMP_VALUES, STORY_TIMESTAMP_LABELS,
                Config.customiseStoryTimestamp(), Config::setCustomiseStoryTimestamp);
        sealGroup(context, storyTime);

        // Round-2 piko port: hide stories by category. Multi-select dialogs
        // over piko's value lists (piko_array_reel_type[_val],
        // piko_array_user_type[_val]); min/max are plain numbers.
        LinearLayout filters = addSection(context, column, "Story filters");
        addMultiSelectRow(context, filters, "Story types to hide",
                "Hide stories of these types from the tray.",
                REEL_TYPE_VALUES, REEL_TYPE_LABELS, Config.filterStoryTypes(),
                Config::setFilterStoryTypes);
        addMultiSelectRow(context, filters, "User types to hide",
                "Hide stories from verified or unverified authors.",
                new String[]{"unverified", "verified"},
                new String[]{"Unverified", "Verified"}, Config.filterStoryUserTypes(),
                Config::setFilterStoryUserTypes);
        addNumberRow(context, filters, "Minimum story items",
                "Hide stories with fewer items than this.",
                String.valueOf(Config.filterStoryMinItems()), false,
                text -> {
                    int value = Integer.parseInt(text);
                    if (value < 1) value = 1;
                    if (value > 9999) value = 9999;
                    Config.setFilterStoryMinItems(value);
                    return String.valueOf(value);
                });
        addNumberRow(context, filters, "Maximum story items",
                "Hide stories with more items than this.",
                String.valueOf(Config.filterStoryMaxItems()), false,
                text -> {
                    int value = Integer.parseInt(text);
                    if (value < 1) value = 1;
                    if (value > 9999) value = 9999;
                    Config.setFilterStoryMaxItems(value);
                    return String.valueOf(value);
                });
        sealGroup(context, filters);
    }

    /** Piko's timestamp list (piko_array_customise_story_timestamp[_val]). */
    private static final String[] STORY_TIMESTAMP_VALUES = {"default", "detailed", "posttime", "timeleft"};
    private static final String[] STORY_TIMESTAMP_LABELS = {"Default", "Detailed", "Post time", "Time left"};

    /** Piko's reel-type list (piko_array_reel_type[_val]); labels are the
     *  value names made readable (ads_reel -> "Ads reel"). */
    private static final String[] REEL_TYPE_VALUES = {
            "ads_reel", "ar_effect_preview", "archive_day_media_reel", "archive_day_reel",
            "archive_friend_reel", "archive_map_reel", "archive_scheduled_for_deletion_reel",
            "archive_shop_reel", "axe_reel", "birthday_highlights", "bloks", "broadcast_reel",
            "daily_snapshot_reel", "election_reel", "empty_story_state_reel", "end_of_year",
            "expiring_story_reel", "fan_club_story", "follow_versaries", "friend_map_reel",
            "group_mention", "group_reel", "hallpass_reel", "highlight_reel",
            "highlight_rewind_reel", "inactive_hallpass_reel", "mas_reel", "media_reshare",
            "memory_reel", "my_week_reel", "netego_reel", "nux_reel", "private_story",
            "prompt_sticker_reel", "reconsideration_injected_story", "shopping_auto_highlight_reel",
            "shopping_pdp_reel", "shopping_smart_reel", "stars_welcome_reel",
            "story_stationary_timeline_reel", "subscription_shoutout_mention",
            "suggested_creator_reel", "suggested_user", "suggested_user_reel",
            "superlative_reel", "third_party_app_story", "typed_container", "user_reel"};
    private static final String[] REEL_TYPE_LABELS = {
            "Ads reel", "Ar effect preview", "Archive day media reel", "Archive day reel",
            "Archive friend reel", "Archive map reel", "Archive scheduled for deletion reel",
            "Archive shop reel", "Axe reel", "Birthday highlights", "Bloks", "Broadcast reel",
            "Daily snapshot reel", "Election reel", "Empty story state reel", "End of year",
            "Expiring story reel", "Fan club story", "Follow versaries", "Friend map reel",
            "Group mention", "Group reel", "Hallpass reel", "Highlight reel",
            "Highlight rewind reel", "Inactive hallpass reel", "Mas reel", "Media reshare",
            "Memory reel", "My week reel", "Netego reel", "Nux reel", "Private story",
            "Prompt sticker reel", "Reconsideration injected story", "Shopping auto highlight reel",
            "Shopping pdp reel", "Shopping smart reel", "Stars welcome reel",
            "Story stationary timeline reel", "Subscription shoutout mention",
            "Suggested creator reel", "Suggested user", "Suggested user reel",
            "Superlative reel", "Third party app story", "Typed container", "User reel"};

    private static String keepSummary() {
        int ghost = (Config.isStorySeenHidden() ? 1 : 0) + (Config.isDmSeenHidden() ? 1 : 0)
                + (Config.isTypingHidden() ? 1 : 0) + (Config.isScreenshotHidden() ? 1 : 0);
        return (Config.isDownloadEnabled() ? "Downloads on" : "Downloads off")
                + (ghost == 0 ? ", ghost mode off" : ", ghost mode " + ghost + "/4");
    }

    private static void buildDisplay(Context context, LinearLayout column) {
        LinearLayout display = addGroup(context, column);
        addRow(context, display, "Force dark (disable HDR)", "force_sdr", Config.isForceSdr());
        addRow(context, display, "Instagram popups", "hide_toasts", Config.arePopupsHidden());
        sealGroup(context, display);

        // Round-2 piko port (Material You / AMOLED). These rows are NOT plain
        // pref flips: the switch must apply the overlay through
        // MaterialYouTheme (which recreates activities), the shown state is
        // the live theme state, and each row is greyed out unless its theme
        // is available on this phone (Android 12+ with the overlay ready;
        // AMOLED also needs dark mode on).
        LinearLayout theme = addSection(context, column, "Theme");
        addThemeToggleRow(context, theme, "Material You",
                "Instagram follows your wallpaper colours (Android 12+).",
                MaterialYouTheme::isMaterialYouEnabled,
                MaterialYouTheme::isMaterialYouAvailable,
                MaterialYouTheme::requestMaterialYouChange);
        addThemeToggleRow(context, theme, "AMOLED",
                "Pure black instead of dark grey. Needs dark mode on.",
                MaterialYouTheme::isAmoledEnabled,
                () -> MaterialYouTheme.isAmoledAvailable() && MaterialYouTheme.canEnableAmoled(context),
                MaterialYouTheme::requestAmoledChange);
        sealGroup(context, theme);
    }

    private static void buildUpdates(Context context, LinearLayout column) {
        LinearLayout updates = addGroup(context, column);
        addRow(context, updates, "Automatic update check", "auto_update", Config.isAutoUpdateEnabled());
        sealGroup(context, updates);

        Button checkUpdate = makeButton(context, "Check for updates", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        checkUpdate.setOnClickListener(v -> UpdateChecker.checkNow(context));
        column.addView(checkUpdate, stackedButtonParams(context, 12));
    }

    private static void buildSupport(Context context, LinearLayout column) {
        Button sponsors = makeButton(context, "Sponsor on GitHub", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        sponsors.setOnClickListener(v -> openUrl(context, SPONSORS_URL));
        column.addView(sponsors, stackedButtonParams(context, 16));

        Button source = makeButton(context, "Source code and issues", 0, ON_SURFACE, false);
        source.setBackground(outlined(context, OUTLINE_VARIANT));
        source.setOnClickListener(v -> openUrl(context, "https://github.com/aic0d3r/oxbow"));
        column.addView(source, stackedButtonParams(context, 10));

        Button upstream = makeButton(context, "Built on FeurStagram by Jean Herail", 0, ON_SURFACE, false);
        upstream.setBackground(outlined(context, OUTLINE_VARIANT));
        upstream.setOnClickListener(v -> openUrl(context, "https://github.com/jean-voila/FeurStagram"));
        column.addView(upstream, stackedButtonParams(context, 10));
    }

    private static String lockSummary() {
        if (Config.isHardcoreMode()) return "Permanent lock on";
        int waiting = Config.pendingKeys().size();
        String waits = waiting == 0 ? "" : ", " + waiting + " waiting";
        return Config.isCooldownEnabled() ? "Cooldown " + Config.getCooldownHours() + " h" + waits : "Off" + waits;
    }

    private static String displaySummary() {
        String dark = Config.isForceSdr() ? "Force dark on" : "Force dark off";
        String popups = Config.arePopupsHidden() ? "popups hidden" : "popups shown";
        return dark + ", " + popups;
    }

    // --- Lock page: the cooldown ------------------------------------------------

    private static void buildLock(Context context, LinearLayout column) {
        if (Config.isHardcoreMode()) {
            LinearLayout on = addGroup(context, column);
            makeRow(context, on, "Permanent lock is on",
                    "It wins over the cooldown: blocks can be tightened, never loosened. Reinstall to unlock.");
            sealGroup(context, on);
            return;
        }
        LinearLayout waiting = new LinearLayout(context);
        waiting.setOrientation(LinearLayout.VERTICAL);

        LinearLayout group = addGroup(context, column);
        LinearLayout row = makeRow(context, group, "Cooldown",
                "Loosening a block or showing a hidden icon starts a wait; you confirm when it ends. "
                        + "Tightening stays instant.");
        ExpressiveSwitch toggle = new ExpressiveSwitch(context);
        toggle.setChecked(Config.isCooldownEnabled());
        toggle.setContentDescription("Cooldown");
        Runnable[] render = new Runnable[1];
        toggle.setOnCheckedChangeListener((btn, on) -> {
            if (!on && Config.isCooldownSticky()) {
                // Switching it off is itself loosening, so it waits too.
                btn.setChecked(true);
                requestCooldown(context, "cooldown_enabled");
                render[0].run();
                return;
            }
            Config.setBlocked("cooldown_enabled", on);
        });
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        sealGroup(context, group);

        LinearLayout hours = addSection(context, column, "Wait");
        List<RadioButton> marks = new ArrayList<>();
        for (int h : Config.COOLDOWN_HOURS) {
            LinearLayout hourRow = makeRow(context, hours, h == 1 ? "1 hour" : h + " hours", null);
            RadioButton mark = new RadioButton(context);
            mark.setClickable(false);
            mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            mark.setChecked(h == Config.getCooldownHours());
            hourRow.addView(mark);
            marks.add(mark);
            hourRow.setAlpha(Config.isCooldownSticky() ? 0.38f : 1f);
            hourRow.setOnClickListener(v -> {
                if (Config.isCooldownSticky()) {
                    Toast.makeText(context, "Turn the cooldown off first to change the wait.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Config.setCooldownHours(h);
                for (int i = 0; i < marks.size(); i++) marks.get(i).setChecked(Config.COOLDOWN_HOURS[i] == h);
            });
        }
        sealGroup(context, hours);

        addSectionLabel(context, column, "Waiting");
        column.addView(waiting);
        render[0] = () -> {
            waiting.removeAllViews();
            List<String> keys = Config.pendingKeys();
            if (keys.isEmpty()) {
                LinearLayout none = addGroup(context, waiting);
                makeRow(context, none, "Nothing waiting", null);
                sealGroup(context, none);
            }
            for (String key : keys) addPendingItem(context, waiting, key, render[0]);
            if (waiting.isAttachedToWindow()) {
                waiting.removeCallbacks(render[0]);
                waiting.postDelayed(render[0], 30_000L); // keeps the time left current; stops when the page is left
            }
        };
        render[0].run();
    }

    /** Starts the wait for a loosening and says so, or says one is already running. */
    private static void requestCooldown(Context context, String key) {
        long left = Config.cooldownLeft(key);
        if (left < 0) Config.startCooldown(key);
        Toast.makeText(context, left < 0 ? "Waiting " + Config.getCooldownHours() + " h. See Lock in settings."
                : "Already waiting: " + timeLeft(left) + " left", Toast.LENGTH_LONG).show();
    }

    private static void addPendingItem(Context context, LinearLayout parent, String key, Runnable refresh) {
        long left = Config.cooldownLeft(key);
        boolean ready = left == 0;
        LinearLayout group = addGroup(context, parent);
        makeRow(context, group, pendingLabel(key), ready ? "Ready. Confirm to apply it." : timeLeft(left) + " left");

        LinearLayout buttons = new LinearLayout(context);
        buttons.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 12));
        Button cancel = makeButton(context, "Cancel request", 0, ON_SURFACE, false);
        cancel.setBackground(outlined(context, OUTLINE_VARIANT));
        cancel.setOnClickListener(v -> {
            Config.cancelCooldown(key);
            refresh.run();
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ready) {
            buttons.addView(new View(context), new LinearLayout.LayoutParams(dp(context, 8), 1));
            Button apply = makeButton(context, "Apply", PRIMARY, ON_PRIMARY, true);
            apply.setOnClickListener(v -> {
                if (!Config.applyCooldown(key)) Toast.makeText(context, "Not ready yet", Toast.LENGTH_SHORT).show();
                refresh.run();
            });
            buttons.addView(apply, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        group.addView(buttons);
        sealGroup(context, group);
    }

    private static String pendingLabel(String key) {
        if (key.equals("cooldown_enabled")) return "Turn the cooldown off";
        if (key.startsWith("nav_show_")) {
            String tab = key.substring(9);
            return "Show the " + (tab.equals("direct") ? "messages" : tab) + " icon";
        }
        return "Unblock " + surfaceLabel(key);
    }

    private static String timeLeft(long ms) {
        long minutes = (ms + 59_999L) / 60_000L;
        if (minutes < 60) return minutes + " min";
        return minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
    }

    private static String landingLabel(String value) {
        for (int i = 0; i < LANDING_VALUES.length; i++) {
            if (LANDING_VALUES[i].equals(value)) return LANDING_LABELS[i];
        }
        return LANDING_LABELS[0];
    }

    /** The shown icons, in bar order, e.g. "Home, Messages, Search, Profile". */
    private static String navSummary(Context context) {
        StringBuilder out = new StringBuilder();
        for (String tab : NavPreview.barOrder(context)) {
            if (!NavPreview.isShown(tab)) continue;
            if (out.length() > 0) out.append(", ");
            out.append(NavPreview.label(tab));
        }
        return out.toString();
    }

    /**
     * A row that opens a section page: Instagram's own outline icon, the name, a
     * one-line summary of the current state, and a chevron.
     */
    private static void addSectionRow(Context context, LinearLayout parent, String icon, String label,
                                      String summary, Runnable onClick) {
        LinearLayout row = makeRow(context, parent, label, summary);
        Drawable glyph = igIcon(context, ON_SURFACE, "instagram_" + icon + "_pano_outline_24",
                "instagram_" + icon + "_outline_24");
        if (glyph != null) {
            ImageView lead = new ImageView(context);
            lead.setImageDrawable(glyph);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
            lp.setMargins(0, 0, dp(context, 16), 0);
            row.addView(lead, 0, lp);
        }
        Drawable chevron = igIcon(context, ON_SURFACE_VARIANT, "instagram_chevron_right_pano_outline_24",
                "instagram_chevron_right_outline_24");
        if (chevron != null) {
            ImageView trail = new ImageView(context);
            trail.setImageDrawable(chevron);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20));
            lp.setMargins(dp(context, 12), 0, 0, 0);
            row.addView(trail, lp);
        }
        row.setOnClickListener(v -> onClick.run());
    }

    /**
     * A live picture of the bottom bar: the icons that will show, in the order
     * Instagram lays them out, redrawn as the toggles below change.
     */
    private static final class NavPreview {
        private static final String[] DEFAULT_ORDER = {
                "feed_tab", "clips_tab", "direct_tab", "search_tab", "creation_tab", "profile_tab",
        };

        final LinearLayout view;
        private final Context context;
        private final List<String> order;

        NavPreview(Context context) {
            this.context = context;
            this.order = barOrder(context);
            view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(dp(context, 64));
            view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
            view.setBackground(roundedRect(SURFACE_CONTAINER, CORNER_L, context));
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            refresh();
        }

        void refresh() {
            view.removeAllViews();
            for (String tab : order) {
                if (!isShown(tab)) continue;
                Drawable glyph = glyph(context, tab);
                if (glyph == null) continue;
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(glyph);
                icon.setScaleType(ImageView.ScaleType.CENTER);
                view.addView(icon, new LinearLayout.LayoutParams(0, dp(context, 48), 1f));
            }
        }

        /** Whether the live tab bar can be read, so {@link #barOrder} is Instagram's own. */
        static boolean isLive(Context context) {
            if (!(context instanceof Activity)) return false;
            int barId = Hiders.resolveId(context, "tab_bar");
            return barId != 0 && ((Activity) context).findViewById(barId) instanceof ViewGroup;
        }

        /** The tab ids in the live bar's order, or a fixed order outside the app. */
        static List<String> barOrder(Context context) {
            List<String> out = new ArrayList<>();
            if (context instanceof Activity) {
                int barId = Hiders.resolveId(context, "tab_bar");
                View bar = barId == 0 ? null : ((Activity) context).findViewById(barId);
                if (bar instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) bar;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        int id = group.getChildAt(i).getId();
                        if (id == View.NO_ID) continue;
                        try {
                            out.add(context.getResources().getResourceEntryName(id));
                        } catch (Throwable ignored) {
                            // unnamed child: not a tab
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                for (String tab : DEFAULT_ORDER) out.add(tab);
            }
            return out;
        }

        static boolean isShown(String tab) {
            switch (tab) {
                case "feed_tab": return Config.getBlocked("nav_show_home", true);
                case "clips_tab": return Config.isReelsTabShown();
                case "direct_tab": return Config.getBlocked("nav_show_direct", true);
                case "search_tab": return Config.getBlocked("nav_show_search", true);
                case "creation_tab": return Config.getBlocked("nav_show_create", true);
                case "profile_tab": return Config.getBlocked("nav_show_profile", true);
                default: return false;
            }
        }

        static String label(String tab) {
            switch (tab) {
                case "feed_tab": return "Home";
                case "clips_tab": return "Reels";
                case "direct_tab": return "Messages";
                case "search_tab": return "Search";
                case "creation_tab": return "Create";
                case "profile_tab": return "Profile";
                default: return tab;
            }
        }

        private static Drawable glyph(Context context, String tab) {
            switch (tab) {
                case "feed_tab": return igIcon(context, ON_SURFACE, "instagram_home_pano_filled_24",
                        "instagram_home_pano_outline_24");
                case "clips_tab": return igIcon(context, ON_SURFACE, "instagram_reels_pano_outline_24");
                case "direct_tab": return igIcon(context, ON_SURFACE, "instagram_direct_pano_outline_24",
                        "instagram_direct_outline_24");
                case "search_tab": return igIcon(context, ON_SURFACE, "instagram_search_pano_outline_24");
                case "creation_tab": return igIcon(context, ON_SURFACE, "instagram_add_pano_outline_24",
                        "instagram_add_outline_24");
                case "profile_tab": return igIcon(context, ON_SURFACE, "instagram_user_pano_outline_24");
                default: return null;
            }
        }
    }

    /**
     * One of Instagram's own icons, by resource name, tinted for our surface: the
     * first of {@code names} that exists, or null. Nothing is bundled: the icon
     * set is the host app's, so it looks like the rest of Instagram, and a name
     * that disappears in a later build only drops the icon.
     */
    static Drawable igIcon(Context context, int tint, String... names) {
        android.content.res.Resources res = context.getResources();
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) id = res.getIdentifier(name, "drawable", "com.instagram.android");
            if (id == 0) continue;
            try {
                Drawable drawable = context.getDrawable(id);
                if (drawable == null) continue;
                drawable = drawable.mutate();
                drawable.setTint(tint);
                return drawable;
            } catch (Throwable ignored) {
                // not loadable in this build; try the next name
            }
        }
        return null;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** An unlabelled group, for a section page whose title already names it. */
    private static LinearLayout addGroup(Context context, LinearLayout parent) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, 16), 0, 0);
        parent.addView(group, lp);
        return group;
    }

    private static LinearLayout.LayoutParams stackedButtonParams(Context context, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, topDp), 0, 0);
        return lp;
    }

    private static void onDone(Context context) {
        if (context == null) return;
        // A pending change restarts straight away (no dialog to back out of).
        if (Config.isRestartPending()) {
            CacheCleaner.clearAndRestart(context);
            return;
        }
        showConfirm(context, "Restart Instagram?",
                "Oxbow will clear the cache and restart the app to apply your changes.",
                "Restart", PRIMARY, ON_PRIMARY,
                () -> CacheCleaner.clearAndRestart(context));
    }

    private static void confirmHardcore(Context context) {
        if (context == null) return;
        if (Config.isHardcoreMode()) {
            Toast.makeText(context, "Permanent lock is already enabled", Toast.LENGTH_LONG).show();
            return;
        }
        showConfirm(context, "Enable permanent lock?",
                "This is permanent for this installation. You will no longer be able to "
                        + "re-enable Home feed, Explore, Reels or Stories without reinstalling the app.",
                "Enable lock", ERROR, ON_ERROR,
                () -> {
                    Config.enableHardcoreMode();
                    Toast.makeText(context,
                            "Permanent lock enabled. Reinstall the app to unlock content.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // --- Confirmation cards ---------------------------------------------------

    /**
     * A centred card with a title, a body and two actions. Shared by the restart
     * and permanent-lock prompts, and shaped like {@link UpdateChecker}'s cards.
     */
    public static void showConfirm(Context context, String titleText, String bodyText,
                                   String confirmText, int confirmBg, int confirmFg,
                                   Runnable onConfirm) {
        try {
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            FrameLayout frame = cardFrame(context);
            LinearLayout card = addCard(context, frame);

            TextView title = new TextView(context);
            title.setText(titleText);
            titleLarge(title);
            card.addView(title);

            // Scrollable, and the one thing allowed to shrink: a long warning must
            // not push the buttons off a short screen or a landscape phone.
            ScrollView bodyScroll = new ScrollView(context);
            bodyScroll.setVerticalScrollBarEnabled(false);
            TextView body = new TextView(context);
            body.setText(bodyText);
            body(body);
            body.setPadding(0, dp(context, 10), 0, 0);
            bodyScroll.addView(body);
            card.addView(bodyScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            boundToFrame(frame, card, bodyScroll);

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonsLp.setMargins(0, dp(context, 24), 0, 0);
            card.addView(buttons, buttonsLp);

            Button cancel = makeButton(context, "Cancel", 0, ON_SURFACE, false);
            cancel.setBackground(outlined(context, OUTLINE_VARIANT));
            cancel.setOnClickListener(v -> dialog.dismiss());
            buttons.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            View spacer = new View(context);
            buttons.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

            Button confirm = makeButton(context, confirmText, confirmBg, confirmFg, true);
            confirm.setOnClickListener(v -> {
                dialog.dismiss();
                onConfirm.run();
            });
            buttons.addView(confirm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            dialog.setContentView(frame);
            dialog.setCanceledOnTouchOutside(true);
            styleWindow(dialog, 0, 0.6f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context, "Unable to open confirmation", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The full-window frame a centred card sits in: padded for the system bars and
     * the cutout, so a card never slips under them however the device is held.
     */
    public static FrameLayout cardFrame(Context context) {
        final FrameLayout frame = new FrameLayout(context);
        applyPageInsets(frame, 20, 20, CARD_MAX_WIDTH_DP);
        return frame;
    }

    /** M3 keeps dialogs narrow; past this they stop reading as a dialog. */
    private static final int CARD_MAX_WIDTH_DP = 520;

    /** Add the card itself, centred, to a {@link #cardFrame}. */
    public static LinearLayout addCard(Context context, FrameLayout frame) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(SURFACE_CONTAINER, CORNER_XL, context));
        int pad = dp(context, 24);
        card.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        frame.addView(card, lp);
        return card;
    }

    /**
     * Keep {@code card} inside {@code frame} by shrinking {@code growable} — the one
     * child allowed to give up height — when the card would otherwise be taller than
     * the space actually available. This is what lets a long release-notes block
     * survive landscape, a short screen or a three-button navigation bar instead of
     * pushing the buttons off the bottom.
     */
    public static void boundToFrame(final FrameLayout frame, final View card, final View growable) {
        // A global-layout listener, not frame.addOnLayoutChangeListener: the frame
        // fills the window and so never changes bounds, while the things that
        // matter — insets arriving a frame late, the card growing once the notes
        // are measured, a rotation — all show up as ordinary layout passes.
        frame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int available = frame.getHeight() - frame.getPaddingTop() - frame.getPaddingBottom();
            if (available <= 0 || card.getWidth() == 0) return;
            ViewGroup.LayoutParams lp = growable.getLayoutParams();
            // The card's laid-out height can't tell: it is capped at the space
            // available, its last children (the buttons) squeezed to fit. Its
            // natural height is what it would take with no limit.
            card.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int overflow = card.getMeasuredHeight() - available;
            if (overflow > 0) {
                int height = Math.max(dp(frame.getContext(), 72), growable.getMeasuredHeight() - overflow);
                if (lp.height != height) {
                    lp.height = height;
                    growable.setLayoutParams(lp);
                }
            } else if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    && overflow < -dp(frame.getContext(), 8)) {
                // Room came back (rotation, bars hidden): let it grow and re-measure.
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                growable.setLayoutParams(lp);
            }
        });
    }

    static void openUrl(Context context, String url) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(inBrowser(context, intent));
        } catch (Throwable t) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Routes a link past apps that claim its domain, straight to a browser. The GitHub app
     * claims every github.com link but can't show Sponsors pages: it relays them to a
     * browser from the background, which MIUI (and Android 14+ rules) silently block.
     * Instagram's manifest queries VIEW for every scheme, so all browsers are visible here.
     */
    private static Intent inBrowser(Context context, Intent intent) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            ResolveInfo preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            // A default browser (or the only one) resolves to itself; otherwise to the system resolver.
            if (preferred != null && !"android".equals(preferred.activityInfo.packageName)) {
                return intent.setPackage(preferred.activityInfo.packageName);
            }
            Set<String> browsers = new HashSet<>();
            for (ResolveInfo r : pm.queryIntentActivities(probe, 0)) browsers.add(r.activityInfo.packageName);
            ArrayList<ComponentName> apps = new ArrayList<>();
            for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
                if (!browsers.contains(r.activityInfo.packageName)) {
                    apps.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
                }
            }
            if (browsers.isEmpty() || apps.isEmpty()) return intent;
            Intent chooser = Intent.createChooser(intent, null);
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, apps.toArray(new ComponentName[0]));
            return chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (Throwable t) {
            return intent;
        }
    }

    // --- Grouped lists --------------------------------------------------------

    /** A sentence-case section label, M3 Expressive style (not all-caps). */
    private static void addSectionLabel(Context context, LinearLayout parent, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setTextColor(ON_SURFACE_VARIANT);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.01f);
        label.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 8));
        parent.addView(label);
    }

    /** Section label plus the container its rows go into. */
    private static LinearLayout addSection(Context context, LinearLayout parent, String text) {
        addSectionLabel(context, parent, text);
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group);
        return group;
    }

    /**
     * Give a finished group its connected shape: round on the outside, nearly
     * square where rows meet, with a hairline gap between them.
     */
    private static void sealGroup(Context context, LinearLayout group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = group.getChildAt(i);
            float top = i == 0 ? CORNER_L : CORNER_JOINT;
            float bottom = i == count - 1 ? CORNER_L : CORNER_JOINT;
            row.setBackground(ripple(RIPPLE,
                    roundedRect(SURFACE_CONTAINER, context, top, top, bottom, bottom)));
            if (i < count - 1) {
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).bottomMargin = dp(context, ROW_GAP_DP);
                    row.setLayoutParams(lp);
                }
            }
        }
    }

    /** The shared skeleton of a list row: label, supporting text, trailing control. */
    private static LinearLayout makeRow(Context context, LinearLayout parent, String label, String support) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
        row.setMinimumHeight(dp(context, 64));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        labelView.setTextColor(ON_SURFACE);
        texts.addView(labelView);

        if (support != null) {
            TextView supportView = new TextView(context);
            supportView.setText(support);
            supportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            supportView.setTextColor(ON_SURFACE_VARIANT);
            supportView.setLineSpacing(0f, 1.1f);
            supportView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(supportView);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static void addRow(Context context, LinearLayout parent, String label, String key, boolean value) {
        addRow(context, parent, label, key, value, null);
    }

    /**
     * @param onChanged run after the preference is written, for rows another
     *                  section depends on (the nav icons drive the landing picker)
     */
    private static void addRow(Context context, LinearLayout parent, String label, String key,
                               boolean value, Runnable onChanged) {
        LinearLayout row = makeRow(context, parent, label, supportText(key));

        ExpressiveSwitch toggle = new ExpressiveSwitch(context);
        toggle.setChecked(value);
        toggle.setContentDescription(label);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            // Hardcore: a frozen surface cannot be revealed; snap it back to hidden.
            if (Config.isHardcoreMode() && Config.isLockable(key)
                    && !Config.hidesSurface(key, isChecked) && Config.wasHiddenAtBaseline(key)) {
                btn.setChecked(!isChecked);
                return;
            }
            if (Config.needsCooldown(key, isChecked)) {
                btn.setChecked(!isChecked);
                requestCooldown(context, key);
                return;
            }
            if (Config.wouldLockOutSettings(key, isChecked)) {
                btn.setChecked(!isChecked);
                Toast.makeText(context, "Keep Home or Messages: long-press either one to open these settings.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Config.setBlocked(key, isChecked);
            Config.setNeedsRestart();
            if (onChanged != null) onChanged.run();
        });
        row.addView(toggle);
        row.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });

        // Freeze rows whose surface is already hidden under the permanent lock.
        if (Config.isLockable(key) && Config.isHardcoreMode() && Config.hidesSurface(key, value)) {
            toggle.setEnabled(false);
            row.setEnabled(false);
            row.setClickable(false);
            row.setAlpha(0.38f);
        }
    }

    private static String supportText(String key) {
        if (key.equals("auto_update")) return "Check GitHub for a new version on launch.";
        if (key.equals("block_ads")) return "Block sponsored ads across Instagram.";
        if (key.equals("block_meta_ai")) return "Hide Meta AI and Muse in the share sheet, promo cards and feed.";
        if (key.equals("limit_following_feed")) return "Show only accounts you follow (needs the feed unblocked).";
        if (key.equals("hide_toasts")) return "Hide every Instagram popup, including “couldn’t refresh feed”.";
        if (key.equals("force_sdr")) return "Keep blacks deep by stopping Instagram forcing HDR on the UI.";
        if (key.equals("block_friends_lane")) return "Hide the Friends tab and its avatars in the Reels header.";
        if (key.equals("block_notifications")) return "Hide the notifications (heart) button in the feed header.";
        if (key.equals("nav_show_home")) return "Hide it and long-press Messages to open these settings.";
        if (key.startsWith("nav_show_")) return "Show this icon in the navigation bar.";
        if (key.equals("download_enabled")) return "Download in ⋯ menus. Long-press a profile picture to save it.";
        if (key.equals("copy_caption")) return "Copy caption in the ⋯ menu of posts and reels.";
        if (key.equals("download_all_slides")) return "Posts with several photos or videos save all of them. Off: hold Download to save all once.";
        if (key.equals("clean_links")) return "Remove tracking (igsh, utm) from links you copy or share.";
        if (key.equals("ghost_story_seen")) return "Watch stories without appearing in the viewer list.";
        if (key.equals("ghost_dm_seen")) return "Chats you open stay unread for the sender. They clear for you.";
        if (key.equals("ghost_typing")) return "Others don't see “typing…” while you write.";
        if (key.equals("ghost_screenshot")) return "Don't tell people when you screenshot their disappearing photo or video.";
        if (key.equals("ghost_shortcut")) return "Hold New message in the inbox to switch all four on or off.";
        if (key.equals("block_comments")) return "Hide comment buttons and never open comments.";
        if (key.equals("disable_video_autoplay")) return "Videos wait for a tap instead of playing on their own.";
        if (key.equals("open_links_externally")) return "Links open in your browser, not inside Instagram.";
        if (key.equals("loop_story")) return "The story you're watching replays when it ends.";
        if (key.equals("disable_story_flipping")) return "Stories stop at the end instead of moving to the next one.";
        if (key.equals("hide_reshare_button")) return "Hides the reshare button on posts and reels.";
        if (key.equals("hide_notes_tray")) return "Hides the notes tray in the DM section.";
        if (key.equals("hide_group_creation_on_sharesheet")) return "Hides the “create group” button on the share sheet.";
        if (key.equals("disable_swipe_to_create")) return "Swiping right on Home no longer opens the create screen.";
        if (key.equals("disable_comments")) return "Blocks comments so they do not load.";
        if (key.equals("disable_explore")) return "Blocks Explore so it stays empty.";
        if (key.equals("disable_highlights")) return "Blocks profile highlights so they do not load.";
        if (key.equals("disable_stories")) return "Blocks stories so they do not load.";
        if (key.equals("disable_discover_people")) return "Blocks the “Discover people” section on profiles.";
        if (key.equals("hide_stories_tray")) return "Hides the stories tray from the main feed.";
        if (key.equals("customize_navigation_bar")) return "Let Oxbow choose which tabs show and in which order.";
        if (key.equals("remove_empty_bottom_space")) return "Removes the gap below the bottom navigation bar.";
        if (key.equals("block_endless_scroll")) return "A reel you open stays one reel. No “more like this” feeds.";
        if (key.equals("block_story_swipe_up")) return "The swipe-up gesture on a story does nothing. No reply bar, no quick reactions.";
        return "Hide this surface in Instagram.";
    }

    // --- Landing page picker --------------------------------------------------

    private static final String[] LANDING_VALUES = {"home", "search", "direct", "profile"};
    private static final String[] LANDING_LABELS = {"Home feed", "Search", "Direct messages", "Profile"};

    /**
     * @param landingSync out-parameter: receives a runnable that re-checks which
     *                    options are still selectable, for the nav rows to call
     *                    when they hide or show a tab
     */
    private static void buildLandingOptions(Context context, LinearLayout group, Runnable[] landingSync) {
        final List<View> rows = new ArrayList<>();
        final List<RadioButton> marks = new ArrayList<>();

        for (int i = 0; i < LANDING_VALUES.length; i++) {
            final String value = LANDING_VALUES[i];
            LinearLayout row = makeRow(context, group, LANDING_LABELS[i], null);

            RadioButton mark = new RadioButton(context);
            mark.setClickable(false);
            mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            row.addView(mark);

            rows.add(row);
            marks.add(mark);

            row.setOnClickListener(v -> {
                if (!Config.isLandingAvailable(value)) return;
                Config.setLandingPage(value);
                Config.setNeedsRestart();
                if (landingSync[0] != null) landingSync[0].run();
            });
        }

        // A surface whose nav icon is hidden is not a landing page you can choose:
        // it would hand back exactly what was hidden, and under the permanent lock
        // the picker was the one way left to reach a frozen surface (issue #116).
        landingSync[0] = () -> {
            String current = Config.getLandingPage();
            for (int i = 0; i < LANDING_VALUES.length; i++) {
                boolean available = Config.isLandingAvailable(LANDING_VALUES[i]);
                View row = rows.get(i);
                row.setEnabled(available);
                row.setAlpha(available ? 1f : 0.38f);
                marks.get(i).setChecked(LANDING_VALUES[i].equals(current));
            }
        };
        landingSync[0].run();
    }

    // --- Round-2 piko ports: choice / number / multi-select / theme rows ------

    /** Saves a string-valued setting (choice lists). */
    private interface StringChoiceSaver { void save(String value); }

    /** Radio list for a string-valued setting, modelled on buildLandingOptions. */
    private static void buildChoiceOptions(Context context, LinearLayout group,
                                           String[] values, String[] labels,
                                           String current, StringChoiceSaver saver) {
        final List<RadioButton> marks = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            LinearLayout row = makeRow(context, group, labels[i], null);
            RadioButton mark = new RadioButton(context);
            mark.setClickable(false);
            mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            mark.setChecked(value.equals(current));
            row.addView(mark);
            marks.add(mark);
            row.setOnClickListener(v -> {
                saver.save(value);
                Config.setNeedsRestart();
                for (int j = 0; j < values.length; j++) marks.get(j).setChecked(values[j].equals(value));
            });
        }
    }

    /** Parses, clamps and stores a typed number; returns the text to display,
     *  or throws NumberFormatException for non-numeric input. */
    private interface NumberSaver { String save(String text); }

    /** Tap-to-edit number row: opens a dialog with a numeric field. */
    private static void addNumberRow(Context context, LinearLayout parent, String label,
                                     String support, String currentText, boolean decimal,
                                     NumberSaver saver) {
        LinearLayout row = makeRow(context, parent, label, support);
        TextView valueView = new TextView(context);
        valueView.setText(currentText);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        valueView.setTextColor(ON_SURFACE_VARIANT);
        row.addView(valueView);
        row.setOnClickListener(v -> {
            EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER
                    | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
            input.setText(currentText);
            input.setSelection(input.getText().length());
            new AlertDialog.Builder(context)
                    .setTitle(label)
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) return;
                        try {
                            String shown = saver.save(text);
                            Config.setNeedsRestart();
                            valueView.setText(shown);
                        } catch (NumberFormatException e) {
                            Toast.makeText(context, "That is not a number.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /** Saves a set-valued setting (multi-select). */
    private interface SetSaver { void save(Set<String> value); }

    private static String multiSummary(Set<String> selected) {
        if (selected == null || selected.isEmpty()) return "None hidden";
        return selected.size() == 1 ? "1 hidden" : selected.size() + " hidden";
    }

    /** Tap-to-edit multi-select row: checkbox dialog over a fixed value list. */
    private static void addMultiSelectRow(Context context, LinearLayout parent, String label,
                                          String support, String[] values, String[] labels,
                                          Set<String> current, SetSaver saver) {
        LinearLayout row = makeRow(context, parent, label, support);
        TextView summaryView = new TextView(context);
        summaryView.setText(multiSummary(current));
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        summaryView.setTextColor(ON_SURFACE_VARIANT);
        row.addView(summaryView);
        row.setOnClickListener(v -> {
            boolean[] checked = new boolean[values.length];
            for (int i = 0; i < values.length; i++) checked[i] = current.contains(values[i]);
            new AlertDialog.Builder(context)
                    .setTitle(label)
                    .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("Save", (d, w) -> {
                        Set<String> next = new HashSet<>();
                        for (int i = 0; i < values.length; i++) if (checked[i]) next.add(values[i]);
                        saver.save(next);
                        Config.setNeedsRestart();
                        summaryView.setText(multiSummary(next));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /** Single-method boolean supplier (avoids java.util.function on old APIs). */
    private interface BoolCheck { boolean get(); }

    /** Applies a theme change; false means the change was refused. */
    private interface ThemeChange { boolean apply(Context context, boolean enabled); }

    /**
     * Switch row whose state comes from live theme state, not the raw pref
     * (piko's rows work the same way: the switch applies the overlay and
     * recreates activities, and greys out while unavailable).
     */
    private static void addThemeToggleRow(Context context, LinearLayout parent, String label,
                                          String support, BoolCheck isOn, BoolCheck isAvailable,
                                          ThemeChange change) {
        LinearLayout row = makeRow(context, parent, label, support);
        ExpressiveSwitch toggle = new ExpressiveSwitch(context);
        toggle.setChecked(isOn.get());
        toggle.setContentDescription(label);
        boolean available = isAvailable.get();
        toggle.setEnabled(available);
        row.setEnabled(available);
        row.setClickable(available);
        if (!available) row.setAlpha(0.38f);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!change.apply(context, isChecked)) {
                btn.setChecked(!isChecked);
                return;
            }
            Config.setNeedsRestart();
        });
        row.addView(toggle);
        row.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });
    }

    // --- Navigation tab picker (round-2 piko port) -----------------------------

    private static final String[] NAV_TAB_KEYS = {
            "home", "reels", "messages", "search", "profile", "notifications", "create"};
    private static final String[] NAV_TAB_LABELS = {
            "Home", "Reels", "Messages", "Search", "Profile", "Notifications", "Create"};

    private static String navTabLabel(NavigationBarPatch.Tab tab) {
        for (int i = 0; i < NAV_TAB_KEYS.length; i++) {
            if (NAV_TAB_KEYS[i].equals(tab.key())) return NAV_TAB_LABELS[i];
        }
        return tab.key();
    }

    private static void buildNavTabPicker(Context context, LinearLayout column) {
        LinearLayout picker = addSection(context, column, "Show and reorder tabs");
        Runnable rebuild = new Runnable() {
            @Override public void run() {
                picker.removeAllViews();
                fillNavTabPicker(context, picker, this);
                sealGroup(context, picker);
            }
        };
        fillNavTabPicker(context, picker, rebuild);
        sealGroup(context, picker);
    }

    private static void fillNavTabPicker(Context context, LinearLayout picker, Runnable rebuild) {
        NavigationBarPatch.Config cfg = NavigationBarPatch.loadConfig();
        List<NavigationBarPatch.Tab> order = new ArrayList<>(cfg.order());
        Set<NavigationBarPatch.Tab> visible = new HashSet<>(cfg.visible());
        for (int i = 0; i < order.size(); i++) {
            final NavigationBarPatch.Tab tab = order.get(i);
            final int index = i;
            LinearLayout row = makeRow(context, picker, navTabLabel(tab),
                    visible.contains(tab) ? "Shown in the bar" : "Hidden from the bar");
            LinearLayout arrows = new LinearLayout(context);
            arrows.setOrientation(LinearLayout.VERTICAL);
            Button up = new Button(context);
            up.setText("▲");
            up.setAllCaps(false);
            up.setMinHeight(0);
            up.setMinimumHeight(0);
            up.setPadding(dp(context, 8), 0, dp(context, 8), 0);
            up.setEnabled(index > 0);
            up.setOnClickListener(v -> {
                List<NavigationBarPatch.Tab> next = new ArrayList<>(order);
                java.util.Collections.swap(next, index, index - 1);
                NavigationBarPatch.saveConfig(next, visible, cfg.startup());
                rebuild.run();
            });
            Button down = new Button(context);
            down.setText("▼");
            down.setAllCaps(false);
            down.setMinHeight(0);
            down.setMinimumHeight(0);
            down.setPadding(dp(context, 8), 0, dp(context, 8), 0);
            down.setEnabled(index < order.size() - 1);
            down.setOnClickListener(v -> {
                List<NavigationBarPatch.Tab> next = new ArrayList<>(order);
                java.util.Collections.swap(next, index, index + 1);
                NavigationBarPatch.saveConfig(next, visible, cfg.startup());
                rebuild.run();
            });
            arrows.addView(up);
            arrows.addView(down);
            row.addView(arrows);
            ExpressiveSwitch toggle = new ExpressiveSwitch(context);
            toggle.setChecked(visible.contains(tab));
            toggle.setContentDescription(navTabLabel(tab));
            toggle.setOnCheckedChangeListener((btn, isChecked) -> {
                Set<NavigationBarPatch.Tab> next = new HashSet<>(visible);
                if (isChecked) next.add(tab); else next.remove(tab);
                NavigationBarPatch.saveConfig(order, next, cfg.startup());
                rebuild.run();
            });
            row.addView(toggle);
            row.setOnClickListener(v -> {
                if (toggle.isEnabled()) toggle.toggle();
            });
        }
    }

    private static void buildNavStartupChoice(Context context, LinearLayout column) {
        LinearLayout startup = addSection(context, column, "App startup tab");
        NavigationBarPatch.Config cfg = NavigationBarPatch.loadConfig();
        String current = cfg.startup() == null ? "home" : cfg.startup().key();
        buildChoiceOptions(context, startup, NAV_TAB_KEYS, NAV_TAB_LABELS, current,
                value -> {
                    NavigationBarPatch.Tab tab = NavigationBarPatch.Tab.fromKey(value);
                    NavigationBarPatch.saveConfig(cfg.order(), cfg.visible(), tab);
                });
        sealGroup(context, startup);
    }

    // --- Type, shape and control helpers --------------------------------------

    /** The page headline: large, tight-tracked, medium weight. */
    public static void headline(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.02f);
    }

    /** A card or dialog title. */
    public static void titleLarge(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
    }

    /** Supporting text. */
    public static void body(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(ON_SURFACE_VARIANT);
        view.setLineSpacing(0f, 1.15f);
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : context.getResources().getDimensionPixelSize(id);
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable roundedRect(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** Per-corner variant, for the connected list groups. */
    public static GradientDrawable roundedRect(int color, Context context,
                                               float topLeftDp, float topRightDp,
                                               float bottomRightDp, float bottomLeftDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float tl = dp(context, topLeftDp);
        float tr = dp(context, topRightDp);
        float br = dp(context, bottomRightDp);
        float bl = dp(context, bottomLeftDp);
        drawable.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return drawable;
    }

    /** A transparent pill with a hairline border, for low-emphasis buttons. */
    public static Drawable outlined(Context context, int strokeColor) {
        GradientDrawable shape = roundedRect(Color.TRANSPARENT, CORNER_FULL, context);
        shape.setStroke(dp(context, 1), strokeColor);
        return ripple(RIPPLE, shape);
    }

    public static Drawable ripple(int color, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static ColorStateList buildStateList(int checkedColor, int uncheckedColor) {
        int[][] states = {
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        int[] colors = {checkedColor, uncheckedColor};
        return new ColorStateList(states, colors);
    }

    /** A pill button. Expressive metrics: 52dp tall, generous horizontal padding. */
    public static Button makeButton(Context context, String text, int bgColor, int textColor, boolean filled) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setLetterSpacing(0f);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        button.setStateListAnimator(null);
        button.setBackground(filled
                ? ripple(RIPPLE, roundedRect(bgColor, CORNER_FULL, context))
                : ripple(RIPPLE, roundedRect(Color.TRANSPARENT, CORNER_FULL, context)));
        return button;
    }

    public static View makeDivider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return divider;
    }
}
