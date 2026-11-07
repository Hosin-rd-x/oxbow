/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Ported to Oxbow (Morphe) from piko's MaterialYouTheme, which targeted
 * Instagram 439.0.0.37.89. Piko-only dependencies are replaced with Oxbow
 * equivalents: piko's SharedPref/Settings become direct reads of the same
 * "feurstagram_prefs" file Config uses (keys material_you_theme,
 * amoled_theme, instagram_theme_mode, instagram_theme_mode_synchronized),
 * so the Config getters the coordinator adds will share this state;
 * Logger becomes android.util.Log; ResourceUtils becomes
 * Resources.getIdentifier; Utils.getActivity becomes a current-activity
 * tracked by the lifecycle callbacks below. The native
 * applyComposePrismColor stub is kept byte-for-byte: the theme patch
 * replaces it with a generated implementation (see ComposePrismBlackPatch).
 * Overlay assets live under assets/oxbow/ (see MaterialYouThemeAPI31).
 * NOTE: this file still imports kotlin.Unit / kotlin.jvm.functions.Function1
 * for the native-theme callback wrapper; the extension module needs
 * kotlin-stdlib on its compile classpath for that (see port report).
 */
package com.feurstagram.extension.theme;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.RadioGroup;

import android.content.SharedPreferences;
import android.util.Log;

import com.feurstagram.extension.Config;

import java.util.Map;
import java.util.WeakHashMap;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public final class MaterialYouTheme {
    // Oxbow has no separate settings Activity (its settings UI lives inside
    // Instagram activities), so this prefix never matches an activity class.
    // It is kept so the "settings activity is not Instagram" guard in
    // resolveInstagramDark still reads the way piko wrote it.
    private static final String OXBOW_SETTINGS_ACTIVITY_PACKAGE =
            "com.feurstagram.extension.";
    // Preference keys, identical to piko's Settings keys, in the same
    // "feurstagram_prefs" file Config uses.
    private static final String KEY_MATERIAL_YOU_THEME = "material_you_theme";
    private static final String KEY_AMOLED_THEME = "amoled_theme";
    private static final String KEY_INSTAGRAM_THEME_MODE = "instagram_theme_mode";
    private static final String KEY_INSTAGRAM_THEME_MODE_SYNCHRONIZED =
            "instagram_theme_mode_synchronized";
    private static Activity currentActivity;
    private static boolean initializationFailureLogged;
    private static boolean lifecycleCallbacksRegistered;
    private static Boolean observedInstagramDark;
    private static final ActivityThemeGeneration ACTIVITY_GENERATIONS =
            new ActivityThemeGeneration();
    private static int themeTransitionDepth;
    private static Activity themeTransitionActivity;
    private static boolean themeTransitionOverlayChanged;
    private static boolean themeTransitionPikoRecreate;
    private static int lastComposePrismOverrideArgb = Integer.MIN_VALUE;
    private static int lastComposeSearchRowOverrideArgb = Integer.MIN_VALUE;

    private static final Application.ActivityLifecycleCallbacks ACTIVITY_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
            applyToActivity(activity);
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            currentActivity = activity;
            boolean stale = ACTIVITY_GENERATIONS.claimStale(activity);
            if (stale) {
                activity.recreate();
                return;
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (currentActivity == activity) {
                currentActivity = null;
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (currentActivity == activity) {
                currentActivity = null;
            }
            ACTIVITY_GENERATIONS.remove(activity);
        }
    };

    private MaterialYouTheme() {
    }

    public static void initialize(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }

        try {
            Context applicationContext = context.getApplicationContext();
            if (applicationContext == null) {
                applicationContext = context;
            }
            MaterialYouThemeAPI31.initialize(applicationContext);
            ThemeMode mode = currentMode();
            // The first activity must refresh colors after attaching its overlay.
            lastComposePrismOverrideArgb = Integer.MIN_VALUE;
            lastComposeSearchRowOverrideArgb =
                    MaterialYouState.composeSearchRowOverrideArgb(mode, 0);
            registerActivityCallbacks(applicationContext);
        } catch (Exception exception) {
            logInitializationFailureOnce(exception);
        }
    }

    public static void applyToActivity(Activity activity) {
        if (Build.VERSION.SDK_INT < 31
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }

        ThemeMode persistedMode = currentMode();
        boolean instagramDark = resolveInstagramDark(activity);
        ThemeMode requestedMode = availableRequestedModeOrFallback(
                persistedMode,
                instagramDark
        );
        ThemeMode previousEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                persistedMode,
                instagramDark
        );
        ThemeMode requestedEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                requestedMode,
                instagramDark
        );
        boolean resourcesChanged = previousEffectiveMode != requestedEffectiveMode;
        if (!MaterialYouThemeAPI31.setResourcesMode(
                activity,
                requestedEffectiveMode,
                instagramDark
        )) {
            if (resourcesChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        instagramDark
                );
            }
            return;
        }
        if (requestedMode != persistedMode && !persistMode(requestedMode)) {
            if (resourcesChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        instagramDark
                );
            }
            persistMode(persistedMode);
            return;
        }
        applyComposePrismMode(activity, requestedEffectiveMode);
        ACTIVITY_GENERATIONS.recordCreated(activity);
    }

    public static int getSystemUiMode() {
        return Build.VERSION.SDK_INT >= 31
                ? MaterialYouThemeAPI31.getSystemNightMask()
                : Configuration.UI_MODE_NIGHT_UNDEFINED;
    }

    public static int resolveSystemUiModeCache(int cachedNightMask) {
        int liveSystemNightMask = getSystemUiMode();
        return MaterialYouState.resolveCachedSystemNightMask(
                Build.VERSION.SDK_INT,
                cachedNightMask,
                liveSystemNightMask
        );
    }

    public static boolean isMaterialYouAvailable() {
        return Build.VERSION.SDK_INT >= 31 && MaterialYouThemeAPI31.isReady(ThemeMode.MATERIAL_YOU);
    }

    public static boolean isAmoledAvailable() {
        return Build.VERSION.SDK_INT >= 31 && MaterialYouThemeAPI31.isReady(ThemeMode.AMOLED);
    }

    public static boolean canEnableAmoled(Context context) {
        Activity activity = getActivity(context);
        boolean available = isAmoledAvailable();
        boolean instagramDark = resolveInstagramDark(activity);
        return available && instagramDark;
    }

    public static boolean isMaterialYouEnabled() {
        return isMaterialYouAvailable()
                && MaterialYouState.hasMaterialYou(currentMode());
    }

    public static boolean isAmoledEnabled() {
        return isAmoledAvailable()
                && MaterialYouState.hasAmoled(currentMode());
    }

    public static Function1<Integer, Unit> wrapNativeThemeCallback(
            Function1<Integer, Unit> callback
    ) {
        if (Build.VERSION.SDK_INT < 31
                || callback == null
                || callback instanceof NativeThemeCallback) {
            return callback;
        }
        return new NativeThemeCallback(callback);
    }

    public static void observeComposeNativeThemeMode(int nativeMode) {
        int observedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        if (MaterialYouState.shouldPersistObservedNativeMode(
                currentInstagramThemeMode(),
                observedMode,
                isInstagramThemeModeSynchronized()
        )) {
            persistInstagramThemeMode(observedMode);
        }
    }

    public static RadioGroup.OnCheckedChangeListener wrapLegacyNativeThemeListener(
            RadioGroup.OnCheckedChangeListener listener,
            int packedIds
    ) {
        if (Build.VERSION.SDK_INT < 31
                || listener == null
                || listener instanceof LegacyNativeThemeController) {
            return listener;
        }
        return new LegacyNativeThemeController(
                listener,
                MaterialYouState.unpackLegacyRadioId(packedIds, 0),
                MaterialYouState.unpackLegacyRadioId(packedIds, 1),
                MaterialYouState.unpackLegacyRadioId(packedIds, 2)
        );
    }

    public static String getLegacySelectedRadioId(String nativeId, int packedIds) {
        Integer observedNativeMode =
                MaterialYouState.nativeModeForLegacySelectionId(nativeId, packedIds);
        if (observedNativeMode != null) {
            int currentNativeMode = currentInstagramThemeMode();
            if (!MaterialYouState.shouldPersistObservedNativeMode(
                    currentNativeMode,
                    observedNativeMode,
                    isInstagramThemeModeSynchronized()
            )) {
                return nativeId;
            }
            if (currentNativeMode == observedNativeMode) {
                persistInstagramThemeMode(observedNativeMode);
                return nativeId;
            }
            Activity activity = getCurrentActivity();
            if (Build.VERSION.SDK_INT >= 31
                    && activity != null
                    && Looper.myLooper() == Looper.getMainLooper()) {
                requestNativeThemeChange(
                        activity,
                        currentModeForRequest(),
                        observedNativeMode,
                        null
                );
            } else {
                persistInstagramThemeMode(observedNativeMode);
            }
        }
        return nativeId;
    }

    public static boolean requestMaterialYouChange(Context context, boolean enabled) {
        return requestMaterialYouChange(getActivity(context), enabled);
    }

    public static boolean requestAmoledChange(Context context, boolean enabled) {
        return requestAmoledChange(getActivity(context), enabled);
    }

    private static boolean requestAmoledChange(Activity activity, boolean enabled) {
        if (enabled && !canEnableAmoled(activity)) {
            return false;
        }
        return requestNativeThemeChange(
                activity,
                MaterialYouState.modeForAmoledToggle(
                        enabled,
                        currentModeForRequest()
                ),
                null,
                null
        );
    }

    private static boolean isActivityDark(Activity activity) {
        return activity != null && MaterialYouState.isActivityUiModeDark(
                activity.getResources().getConfiguration().uiMode
        );
    }

    private static boolean resolveInstagramDark(Activity activity) {
        boolean activityDark = isActivityDark(activity);
        boolean oxbowSettingsActivity = activity == null
                || activity.getClass().getName().startsWith(OXBOW_SETTINGS_ACTIVITY_PACKAGE);
        observedInstagramDark = MaterialYouState.updateObservedInstagramDark(
                observedInstagramDark,
                oxbowSettingsActivity,
                activityDark
        );
        return MaterialYouState.resolveInstagramDark(
                observedInstagramDark,
                currentInstagramThemeMode(),
                isInstagramThemeModeSynchronized(),
                getSystemUiMode()
        );
    }

    private static boolean requestMaterialYouChange(Activity activity, boolean enabled) {
        return requestNativeThemeChange(
                activity,
                MaterialYouState.modeForMaterialYouToggle(
                        enabled,
                        currentModeForRequest()
                ),
                null,
                null
        );
    }

    private static boolean requestNativeThemeChange(
            Activity activity,
            ThemeMode mode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        if (Build.VERSION.SDK_INT < 31
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()) {
            return false;
        }

        return applyMode(
                activity,
                mode,
                nativeMode,
                nativeAction
        );
    }

    private static ThemeMode availableRequestedModeOrFallback(
            ThemeMode mode,
            boolean instagramDark
    ) {
        return MaterialYouState.availableRequestedModeOrFallback(
                mode,
                instagramDark,
                isMaterialYouAvailable(),
                isAmoledAvailable(),
                MaterialYouThemeAPI31.isReady(ThemeMode.AMOLED_MATERIAL_YOU)
        );
    }

    private static boolean applyMode(
            Activity activity,
            ThemeMode requestedMode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        ThemeMode persistedPreviousMode = currentMode();
        int previousNativeMode = currentInstagramThemeMode();
        int requestedNativeMode = nativeMode == null
                ? previousNativeMode
                : MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        int systemNightMask = getSystemUiMode();
        int currentNightMask = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean previousInstagramDark = resolveInstagramDark(activity);
        boolean requestedInstagramDark = nativeMode == null
                ? previousInstagramDark
                : MaterialYouState.isEffectiveDark(
                        requestedNativeMode,
                        systemNightMask
                );
        ThemeMode previousRequestedMode = availableRequestedModeOrFallback(
                persistedPreviousMode,
                previousInstagramDark
        );
        requestedMode = availableRequestedModeOrFallback(
                requestedMode,
                requestedInstagramDark
        );
        ThemeMode previousEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                previousRequestedMode,
                previousInstagramDark
        );
        ThemeMode requestedEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                requestedMode,
                requestedInstagramDark
        );
        int targetNightMask = MaterialYouState.targetNightMask(
                requestedNativeMode,
                systemNightMask
        );
        boolean overlayChanged = previousEffectiveMode != requestedEffectiveMode
                || previousInstagramDark != requestedInstagramDark;
        boolean pikoRecreate = MaterialYouState.shouldPikoRecreate(
                previousEffectiveMode,
                requestedEffectiveMode,
                nativeAction != null,
                currentNightMask,
                targetNightMask
        );
        if (!MaterialYouThemeAPI31.setResourcesMode(
                activity,
                requestedEffectiveMode,
                requestedInstagramDark
        )) {
            if (overlayChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        previousInstagramDark
                );
            }
            return false;
        }
        boolean requestedStateCommitted =
                (previousRequestedMode == requestedMode || persistMode(requestedMode))
                        && (!MaterialYouState.shouldPersistNativeThemeBeforeAction(
                                nativeMode != null,
                                nativeAction != null
                        ) || persistInstagramThemeMode(requestedNativeMode));
        if (!requestedStateCommitted) {
            if (overlayChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        previousInstagramDark
                );
            }
            persistMode(persistedPreviousMode);
            if (nativeMode != null) {
                persistInstagramThemeMode(previousNativeMode);
            }
            return false;
        }
        applyComposePrismMode(activity, requestedEffectiveMode);
        runThemeTransition(
                activity,
                nativeAction,
                pikoRecreate,
                overlayChanged
        );
        observedInstagramDark =
                MaterialYouState.updateObservedInstagramDarkForNativeMode(
                        observedInstagramDark,
                        nativeMode,
                        requestedInstagramDark
                );
        if (MaterialYouState.shouldPersistNativeThemeAfterAction(
                nativeMode != null,
                nativeAction != null
        )) {
            return persistInstagramThemeMode(requestedNativeMode);
        }
        return true;
    }

    private static void applyComposePrismMode(Context context, ThemeMode mode) {
        int materialYouBackgroundArgb = 0;
        if (MaterialYouState.hasMaterialYou(mode)) {
            int backgroundResourceId = context.getResources().getIdentifier(
                    "igds_primary_background",
                    "color",
                    context.getPackageName()
            );
            if (backgroundResourceId != 0) {
                try {
                    materialYouBackgroundArgb = context.getColor(backgroundResourceId);
                } catch (Resources.NotFoundException ignored) {
                }
            }
        }

        int overrideArgb = MaterialYouState.composePrismOverrideArgb(
                mode,
                materialYouBackgroundArgb
        );
        boolean changed = overrideArgb != lastComposePrismOverrideArgb;
        applyComposePrismColor(overrideArgb, changed);
        lastComposePrismOverrideArgb = overrideArgb;
        lastComposeSearchRowOverrideArgb =
                MaterialYouState.composeSearchRowOverrideArgb(
                        mode,
                        materialYouBackgroundArgb
                );
    }

    private static native void applyComposePrismColor(
            int overrideArgb,
            boolean refreshPalettes
    );

    public static long resolveComposeSearchRowBackground(long nativePackedColor) {
        return MaterialYouState.composeSearchRowBackground(
                nativePackedColor,
                lastComposeSearchRowOverrideArgb
        );
    }

    private static void runThemeTransition(
            Activity activity,
            Runnable nativeAction,
            boolean pikoRecreate,
            boolean overlayChanged
    ) {
        boolean rootTransition = themeTransitionDepth == 0;
        if (rootTransition) {
            themeTransitionActivity = activity;
            themeTransitionOverlayChanged = false;
            themeTransitionPikoRecreate = false;
        }
        themeTransitionOverlayChanged |= overlayChanged;
        themeTransitionPikoRecreate |= pikoRecreate;
        themeTransitionDepth++;
        try {
            if (nativeAction != null) {
                nativeAction.run();
            }
        } finally {
            themeTransitionDepth--;
            if (themeTransitionDepth == 0) {
                Activity refreshActivity = themeTransitionActivity;
                themeTransitionActivity = null;
                boolean changedOverlay = themeTransitionOverlayChanged;
                boolean recreateWithPiko = themeTransitionPikoRecreate;
                themeTransitionOverlayChanged = false;
                themeTransitionPikoRecreate = false;
                if (refreshActivity != null && changedOverlay) {
                    ACTIVITY_GENERATIONS.advance();
                    ACTIVITY_GENERATIONS.recordCreated(refreshActivity);
                }
                if (refreshActivity != null && recreateWithPiko) {
                    refreshActivity.recreate();
                }
            }
        }
    }

    private static ThemeMode currentMode() {
        return MaterialYouState.resolveMode(
                getBooleanPref(KEY_MATERIAL_YOU_THEME, false),
                getBooleanPref(KEY_AMOLED_THEME, false)
        );
    }

    private static ThemeMode currentModeForRequest() {
        return currentMode();
    }

    private static boolean persistMode(ThemeMode mode) {
        boolean materialYouEnabled = MaterialYouState.hasMaterialYou(mode);
        boolean amoledEnabled = MaterialYouState.hasAmoled(mode);
        boolean materialYouWritten = setBooleanPref(KEY_MATERIAL_YOU_THEME, materialYouEnabled);
        boolean amoledWritten = setBooleanPref(KEY_AMOLED_THEME, amoledEnabled);
        return materialYouWritten && amoledWritten;
    }

    private static int currentInstagramThemeMode() {
        try {
            return MaterialYouState.sanitizeNativeThemeMode(
                    Integer.parseInt(getStringPref(KEY_INSTAGRAM_THEME_MODE, "-1"))
            );
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isInstagramThemeModeSynchronized() {
        return getBooleanPref(KEY_INSTAGRAM_THEME_MODE_SYNCHRONIZED, false);
    }

    private static boolean persistInstagramThemeMode(int nativeMode) {
        int sanitizedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        boolean modeWritten = setStringPref(
                KEY_INSTAGRAM_THEME_MODE,
                Integer.toString(sanitizedMode)
        );
        if (!modeWritten) {
            return false;
        }
        return setBooleanPref(KEY_INSTAGRAM_THEME_MODE_SYNCHRONIZED, true);
    }

    private static Activity getActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }

            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static synchronized void registerActivityCallbacks(Context context) {
        if (lifecycleCallbacksRegistered || !(context instanceof Application)) {
            return;
        }
        ((Application) context).registerActivityLifecycleCallbacks(ACTIVITY_CALLBACKS);
        lifecycleCallbacksRegistered = true;
    }

    private static synchronized void logInitializationFailureOnce(Exception exception) {
        if (initializationFailureLogged) {
            return;
        }
        initializationFailureLogged = true;
        Log.e("MaterialYouTheme", "Failed to initialize theme resources", exception);
    }

    /** The activity most recently resumed, for the native-theme callbacks. */
    private static Activity getCurrentActivity() {
        return currentActivity;
    }

    // Direct SharedPreferences access in Config's "feurstagram_prefs" file.
    // (Deliberately not via Config getters: Config.java must not be edited by
    // this port, and the getters do not exist yet. Once the coordinator adds
    // isMaterialYouEnabled()/isAmoledEnabled() etc. to Config, these helpers
    // can switch to them; the keys and file stay the same.)
    private static SharedPreferences prefs() {
        Context context = Config.getAppContext();
        return context == null
                ? null
                : context.getSharedPreferences("feurstagram_prefs", Context.MODE_PRIVATE);
    }

    private static boolean getBooleanPref(String key, boolean defaultValue) {
        SharedPreferences prefs = prefs();
        return prefs == null ? defaultValue : prefs.getBoolean(key, defaultValue);
    }

    private static boolean setBooleanPref(String key, boolean value) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.edit().putBoolean(key, value).commit();
    }

    private static String getStringPref(String key, String defaultValue) {
        SharedPreferences prefs = prefs();
        return prefs == null ? defaultValue : prefs.getString(key, defaultValue);
    }

    private static boolean setStringPref(String key, String value) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.edit().putString(key, value).commit();
    }

    private static final class ActivityThemeGeneration {
        private final Map<Activity, Long> generations = new WeakHashMap<>();
        private long currentGeneration;

        private synchronized void recordCreated(Activity activity) {
            generations.put(activity, currentGeneration);
        }

        private synchronized void advance() {
            currentGeneration++;
        }

        private synchronized boolean claimStale(Activity activity) {
            Long activityGeneration = generations.get(activity);
            if (activityGeneration == null) {
                recordCreated(activity);
                return false;
            }
            if (activityGeneration == currentGeneration) {
                return false;
            }

            generations.put(activity, currentGeneration);
            return true;
        }

        private synchronized void remove(Activity activity) {
            generations.remove(activity);
        }
    }

    private static final class NativeThemeCallback implements Function1<Integer, Unit> {
        private final Function1<Integer, Unit> delegate;

        private NativeThemeCallback(Function1<Integer, Unit> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Unit invoke(Integer nativeMode) {
            ThemeMode currentMode = currentModeForRequest();
            requestNativeThemeChange(
                    getCurrentActivity(),
                    MaterialYouState.modeForNativeThemeSelection(currentMode),
                    nativeMode,
                    () -> delegate.invoke(nativeMode)
            );
            return Unit.INSTANCE;
        }
    }

    private static final class LegacyNativeThemeController
            implements RadioGroup.OnCheckedChangeListener {
        private final RadioGroup.OnCheckedChangeListener delegate;
        private final int lightId;
        private final int darkId;
        private final int systemId;

        private LegacyNativeThemeController(
                RadioGroup.OnCheckedChangeListener delegate,
                int lightId,
                int darkId,
                int systemId
        ) {
            this.delegate = delegate;
            this.lightId = lightId;
            this.darkId = darkId;
            this.systemId = systemId;
        }

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            ThemeMode currentMode = currentModeForRequest();
            Integer nativeMode = MaterialYouState.nativeModeForLegacySelection(
                    checkedId,
                    lightId,
                    darkId,
                    systemId
            );
            if (nativeMode == null) {
                delegate.onCheckedChanged(group, checkedId);
                return;
            }
            requestNativeThemeChange(
                    getActivity(group.getContext()),
                    MaterialYouState.modeForNativeThemeSelection(currentMode),
                    nativeMode,
                    () -> delegate.onCheckedChanged(group, checkedId)
            );
        }
    }

}
