package com.pomelo.tv;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Écran d'accueil minimaliste : paysage qui suit l'heure du jour, horloge, dock de favoris, grille de toutes les apps. */
public class HomeActivity extends Activity {

    private static final long WALLPAPER_TICK_MS = 60_000;
    /** Choix proposés dans les réglages ; 0 : ne change jamais. */
    private static final int[] WALLPAPER_MINUTES = {1, 5, 15, 30, 60, 0};
    /** Délais de l'économiseur ; 0 : jamais. */
    private static final int[] SAVER_MINUTES = {1, 2, 5, 10, 15, 0};
    private static final String KEY_SAVER_MINUTES = "saver_minutes";
    /** Délais d'inactivité avant l'extinction ; 0 : jamais. */
    private static final int[] SLEEP_MINUTES = {0, 15, 30, 45, 60, 90, 120};
    static final String KEY_SLEEP_MINUTES = "sleep_minutes";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_TILE_SMOOTHING = "tile_smoothing";
    private static final String KEY_SHELF = "shelf";
    private static final String KEY_SHELF_ASKED = "shelf_permission_asked";
    private static final String TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS";
    /** Délai seulement quand une flèche reste maintenue : survoler le dock à toute vitesse ne déclenche rien. */
    private static final long SHELF_HELD_KEY_DELAY_MS = 450, SHELF_REFRESH_MS = 10 * 60_000;
    private static final int SHELF_IMAGE_CACHE = 40;
    /** Distance parcourue par les images quand elles arrivent ou repartent, dans le sens du déplacement. */
    private static final int SHELF_ARRIVAL_SLIDE = 48;
    /** Au-delà, on affiche les cartes dont l'image est arrivée plutôt que de faire attendre davantage. */
    private static final long SHELF_IMAGES_MAX_WAIT_MS = 6_000;
    /** Une image couvre 3 colonnes du dock : deux images remplissent exactement ses 6 colonnes. */
    private static final int SHELF_CARD_COLUMNS = 3;
    private static final float SHELF_CARD_FOCUS_SCALE = 1.04f;
    /** Lissage des bords : aucun, tuile sélectionnée ou en mode giggle (par défaut), toutes les tuiles. */
    private static final int SMOOTH_OFF = 0, SMOOTH_SELECTED = 1, SMOOTH_ALL = 2;
    /** La première entrée suit le téléviseur ; une langue non traduite retombe sur l'anglais, la langue par défaut des textes. */
    private static final String[] LANGUAGES = {"", "fr", "en", "es", "de", "it"};
    private static final String[] LANGUAGE_NAMES = {null, "Français", "English", "Español", "Deutsch", "Italiano"};
    private static final String KEY_NAV_SOUND = "nav_sound";
    private static boolean navSound = true;
    private static final long FADE_MS = 2_000;
    static final String PREFS = "pomelo";
    private static final int PANEL_BG = 0xB80B0C10;
    private static final String KEY_DOCK = "dock";
    private static final String KEY_WALLPAPER_MINUTES = "wallpaper_minutes";
    /** Le dock et la grille partagent colonnes, tuiles et écarts : un dock plein tombe pile sur les colonnes. */
    private static final int GRID_COLUMNS = 6, DOCK_MAX = GRID_COLUMNS;
    private static final int GRID_TILE_W = 124, GRID_TILE_H = 70;
    private static final float GRID_GAP = 21f;
    /**
     * Du bord du dock aux tuiles. Une tuile agrandie déborde plus en largeur qu'en hauteur : la marge de côté, un peu
     * plus grande au repos, absorbe la moitié de cette différence, et dockEdgeShift le reste.
     */
    private static final int DOCK_INSET = 18;
    private static final float DOCK_SIDE_INSET = 21.75f;
    /** Même espace du bas du dock à la grille et entre deux lignes de la grille. */
    private static final int GRID_ROW_GAP = 30;
    private static final int DOCK_HEIGHT = 106, DOCK_OPEN_TOP = 36, GRID_PADDING_TOP = GRID_ROW_GAP;
    /** Ce qui dépasse de la grille en bas de l'accueil, pour montrer qu'il y a des apps en dessous. */
    private static final int GRID_PEEK = 14;
    private static final float TILE_FOCUS_SCALE = 1.2f;
    /** Part du zoom qu'une tuile atteint toujours, même survolée très vite. Au-delà, deux voisines se toucheraient. */
    private static final float FOCUS_MOMENTUM = 0.6f;
    private static final TimeInterpolator MOMENTUM_EASE = new android.view.animation.DecelerateInterpolator(1.4f);
    private static final String KEY_GRID = "grid";
    private static final String KEY_DOCK_BLUR = "dock_blur";
    private static final String KEY_ACTION_TILES = "action_tiles";
    /** Tuiles internes, rangées dans le dock et la grille comme des apps. */
    private static final String TILE_SOURCES = "pomelo:sources", TILE_SETTINGS = "pomelo:settings", TILE_POWER = "pomelo:power";
    // Les ressorts arrivent vite puis s'éteignent lentement : la durée inclut cette fin très douce.
    private static final float ANIM_SLOWDOWN = 1.8f;
    /** Dock de départ : les premières de ces applis qui sont installées. */
    private static final String[] DEFAULT_DOCK = {
            "com.mediatek.wwtv.tvcenter",
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.plexapp.android", "com.spotify.tv.android", "tv.twitch.android.app",
            "org.smarttube.beta", "org.smarttube.stable", "com.hbo.hbonow", "com.apple.atve.androidtv.appletv",
            "org.videolan.vlc", "org.xbmc.kodi"};
    private static final int DEFAULT_DOCK_SIZE = 4;
    private static final String KEY_HIDDEN_INPUTS = "hidden_inputs";
    private static final String KEY_INPUTS_ORDER = "inputs_order";
    private static final float CARD_FOCUS_SCALE = 1.18f;
    /** Demande d'ouvrir le panneau des entrées (touche Source, via HomeKeyService). */
    static final String EXTRA_SHOW_INPUTS = "show_inputs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private final TimeInterpolator ease = Spring.SOFT;

    private Typeface fontLight, fontRegular, fontMedium;
    private FrameLayout root;
    private ImageView bgFront, bgBack;
    private ObjectAnimator fadeAnim;
    private LinearLayout dock;
    private Squircle shelf;
    private int dockWidthBefore;
    private View dockScroll;
    private View clock;
    private FrameLayout allAppsOverlay;
    private View allAppsVeil;
    private ScrollView allAppsScroll;
    private FrameLayout allAppsGrid;
    private boolean allAppsOpen;
    private View allAppsReturnFocus;
    private FrameLayout inputsOverlay, settingsOverlay;
    private LinearLayout inputsRow, settingsList;
    private TextView inputsTitle;
    private ScrollView settingsScroll;
    private boolean inSourcesPage;
    private View panelReturnFocus;
    private boolean stoppedSinceResume;

    // Recommandations au-dessus du dock.
    private FrameLayout shelfRoot;
    private LinearLayout shelfRow;
    private HorizontalScrollView shelfScroll;
    private View shelfTile;
    private String shelfPkg, shelfPreparing;
    private boolean shelfEnabled;
    private Map<String, List<TvPrograms.Program>> shelfPrograms = new HashMap<>();
    private long shelfLoadedAt;
    /** Sens du dernier déplacement dans le dock : 1 vers la droite, -1 vers la gauche, 0 sans déplacement. */
    private int shelfArrivalDirection;
    private boolean keyHeld;
    /** Le dock redescend : les images sont déjà en place, le voile qui s'efface les dévoile sans autre animation. */
    private boolean shelfNoAnimation;
    private int lastDockFocusIndex = -1;
    private final ExecutorService shelfWorker = Executors.newSingleThreadExecutor();
    /** Peu d'images gardées en mémoire : une carte perdue se recharge toute seule. */
    private final Map<String, Bitmap> shelfImages = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > SHELF_IMAGE_CACHE;
        }
    };
    private final Runnable shelfShow = this::showShelfForFocus;
    private int tileSmoothing;
    private TextClock timeClock, dateClock;
    private FrameLayout accessibilityPopup;
    private View accessibilityPopupReturnFocus;
    /** État du service d'accessibilité quand la page des réglages a été remplie. */
    private boolean settingsBuiltWithService;
    private boolean saverActive;
    /** Touche qui vient de réveiller l'économiseur : on l'ignore jusqu'à son relâchement. */
    private int swallowedKey = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable saverTimeout = this::enterSaver;
    /** Le panneau des sources s'est ouvert par-dessus une autre appli ou une entrée : Retour doit y revenir. */
    private boolean inputsOverOtherApp;
    /** Met à jour le panneau ouvert quand on branche ou débranche un appareil. */
    private final TvInputManager.TvInputCallback inputStateCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) {
            if (inputsOverlay.getVisibility() != View.VISIBLE || movingTile != null) return;
            View focused = inputsRow.findFocus();
            int index = focused != null ? inputsRow.indexOfChild(focused) : 0;
            fillInputsRow();
            if (inputsRow.getChildCount() > 0) {
                inputsRow.getChildAt(Math.min(index, inputsRow.getChildCount() - 1)).requestFocus();
            }
        }
    };
    private final List<String> themes = new ArrayList<>();
    private int themeIndex;
    private String shownWallpaper;
    private long lastWallpaperChange;
    private static final int BLUR_W = 96, BLUR_H = 54;
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap blurBack, blurFront;
    private float blurMix = 1f;
    private ValueAnimator blurFade;
    private boolean dockBlur;
    private float dp;

    // Mode « déplacer » du dock (appui long).
    private View movingTile;
    private ValueAnimator wiggle, moveSlide;
    private float moveFromX, moveFromY, moveSlideLeft;
    private boolean ignoreCenterUp;

    /**
     * Défile en douceur pour garder une marge autour de la sélection, quelle que soit la façon dont elle change
     * (flèches, touche Source), et ne bouge plus une fois au bout de la liste.
     */
    private static final class EdgeScroll extends HorizontalScrollView {
        private final int edge;
        private final long slideMs;
        private ObjectAnimator slide;

        EdgeScroll(Context context, int edge) {
            this(context, edge, slow(420));
        }

        EdgeScroll(Context context, int edge, long slideMs) {
            super(context);
            this.edge = edge;
            this.slideMs = slideMs;
            setFocusable(false); // sinon, quand son contenu est bloqué, le focus peut tomber sur le conteneur invisible
        }

        /** Sert seulement au calcul du défilement : rien n'est estompé à l'écran. */
        @Override
        public int getHorizontalFadingEdgeLength() {
            boolean scrollable = getChildCount() > 0 && getChildAt(0).getWidth() > getWidth();
            return scrollable ? edge : 0;
        }

        @Override
        public boolean arrowScroll(int direction) {
            View next = FocusFinder.getInstance().findNextFocus(this, findFocus(), direction);
            if (next != null && next != this && next.requestFocus()) playNavigationSound(next, direction);
            return true;
        }

        @Override
        public void requestChildFocus(View child, View focused) {
            if (slide != null) slide.cancel();
            int from = getScrollX();
            super.requestChildFocus(child, focused);
            int to = getScrollX();
            if (from == to || !isLaidOut()) return;
            scrollTo(from, 0);
            slide = ObjectAnimator.ofInt(this, "scrollX", from, to).setDuration(slideMs);
            slide.setInterpolator(Spring.SOFT);
            slide.start();
        }
    }

    /** Même principe à la verticale : la sélection ne colle pas au bord et le défilement reste doux. */
    private static final class SmoothScroll extends ScrollView {
        private final int edge;
        private ObjectAnimator slide;

        SmoothScroll(Context context, int edge) {
            super(context);
            this.edge = edge;
            setFocusable(false); // sinon, quand son contenu est bloqué, le focus peut tomber sur le conteneur invisible
        }

        @Override
        public int getVerticalFadingEdgeLength() {
            return edge;
        }

        @Override
        public boolean arrowScroll(int direction) {
            View next = FocusFinder.getInstance().findNextFocus(this, findFocus(), direction);
            if (next != null && next != this && next.requestFocus()) playNavigationSound(next, direction);
            return true;
        }

        @Override
        public void requestChildFocus(View child, View focused) {
            if (slide != null) slide.cancel();
            int from = getScrollY();
            super.requestChildFocus(child, focused);
            slideFrom(from);
        }

        /**
         * Si le texte d'une ligne change en même temps que la sélection, la mise en page est en attente :
         * Android ne défile alors qu'ici, et il faut animer ce saut aussi.
         */
        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int from = getScrollY();
            super.onLayout(changed, l, t, r, b);
            slideFrom(from);
        }

        private void slideFrom(int from) {
            int to = getScrollY();
            if (from == to || !isLaidOut()) return;
            if (slide != null) slide.cancel();
            scrollTo(0, from);
            slide = ObjectAnimator.ofInt(this, "scrollY", from, to).setDuration(slow(420));
            slide.setInterpolator(Spring.SOFT);
            slide.start();
        }
    }

    /** Android ne fait ce son que s'il déplace la sélection lui-même, pas quand on la déplace à sa place. */
    private static void playNavigationSound(View v, int direction) {
        if (navSound) v.playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
    }

    private static long slow(long ms) {
        return Math.round(ms * ANIM_SLOWDOWN);
    }

    static class AppEntry {
        String pkg;
        CharSequence label;
        Drawable banner;
        boolean isBanner;
        /** La bannière en tout petit, sans ses bords, placée dessous : ses coins transparents prennent ses couleurs. */
        Bitmap bannerBackdrop;
    }

    private final Runnable wallpaperTick = new Runnable() {
        @Override public void run() {
            // Le tic reste à la minute : l'éclairage suit l'heure même si le paysage change rarement.
            int minutes = wallpaperMinutes();
            long now = System.currentTimeMillis();
            if (minutes > 0 && now - lastWallpaperChange >= minutes * WALLPAPER_TICK_MS - 1_000) {
                themeIndex++;
                lastWallpaperChange = now;
            }
            showWallpaper();
            handler.postDelayed(this, WALLPAPER_TICK_MS);
        }
    };

    /** Fait pendant que l'écran est éteint : au rallumage, l'accueil est déjà prêt, sans animation. */
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            resetHome();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (movingTile != null) return;
            loadApps();
            buildDock(false);
            buildAllApps();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(withLanguage(base));
    }

    /** Applique la langue choisie ; sans choix, le contexte garde celle du téléviseur. */
    static Context withLanguage(Context base) {
        String code = base.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANGUAGE, "");
        if (code.isEmpty()) return base;
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    private void applyLanguage(String code) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LANGUAGE, code).apply();
        Locale locale = code.isEmpty() ? Resources.getSystem().getConfiguration().getLocales().get(0) : new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        HomeKeyService.languageChanged(config);
        dateClock.setFormat24Hour("EEEE d MMMM");
        inputsTitle.setText(R.string.inputs);
        loadApps();
        buildDock(false);
        buildAllApps();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        navSound = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_NAV_SOUND, true);
        tileSmoothing = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_TILE_SMOOTHING, SMOOTH_SELECTED);
        shelfEnabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SHELF, true);
        fontLight = Typeface.createFromAsset(getAssets(), "fonts/Inter-Light.ttf");
        fontRegular = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf");
        fontMedium = Typeface.createFromAsset(getAssets(), "fonts/Inter-Medium.ttf");
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false); // une tuile du dock en plein déplacement sort des limites du dock
        setContentView(root);

        bgBack = newBackground();
        bgFront = newBackground();
        root.addView(bgBack, match());
        root.addView(bgFront, match());

        clock = buildClock();
        root.addView(clock);

        // Le voile passe sous le dock, qui reste net ; la grille passe dessus, pour qu'une tuile
        // qui descend du dock reste visible pendant son trajet.
        allAppsVeil = new View(this);
        allAppsVeil.setBackgroundColor(PANEL_BG);
        allAppsVeil.setAlpha(0f);
        shelfRoot = buildShelf();
        root.addView(shelfRoot, match());
        root.addView(allAppsVeil, match());

        HorizontalScrollView scroll = new EdgeScroll(this, px(20));
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        int dockSide = px(DOCK_SIDE_INSET) - px(GRID_GAP / 2f); // chaque tuile ajoute sa moitié d'écart
        dock.setPadding(dockSide, px(DOCK_INSET), dockSide, px(DOCK_INSET));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        shelf = new Squircle(px(34), 0x38FFFFFF);
        shelf.setStroke(1, 0x2EFFFFFF);
        dock.setBackground(shelf);
        // Toute la largeur de l'écran, dock centré dedans : il reste centré quel que soit le nombre d'apps.
        scroll.addView(dock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        dockScroll = scroll;
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dlp.bottomMargin = px(44);
        root.addView(scroll, dlp);

        allAppsOverlay = buildAllAppsOverlay();
        root.addView(allAppsOverlay, match());

        inputsOverlay = buildInputsOverlay();
        root.addView(inputsOverlay, match());
        settingsOverlay = buildSettingsOverlay();
        root.addView(settingsOverlay, match());

        dockScroll.animate().setUpdateListener(a -> refreshDockBackdrop());
        allAppsVeil.animate().setUpdateListener(a -> refreshDockBackdrop());
        setDockBlur(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DOCK_BLUR, false));

        loadThemes();
        renameSavedTiles();
        loadApps();
        addActionTilesOnce();
        buildAllApps();
        buildDock(true);
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        showWallpaper();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        TvInputManager inputs = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (inputs != null) inputs.registerCallback(inputStateCallback, handler);
        if (getIntent().getBooleanExtra(EXTRA_SHOW_INPUTS, false)) {
            showInputs();
            inputsOverOtherApp = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        stoppedSinceResume = false;
        armSaver();
        refreshSettingsAfterAccessibility();
        refreshShelfData();
        refreshShelf();
        handler.removeCallbacks(wallpaperTick);
        handler.postDelayed(wallpaperTick, WALLPAPER_TICK_MS);
        showWallpaper(); // rattrape un changement de moment de la journée pendant l'absence
        if (!allAppsOpen && !panelVisible() && dock.getChildCount() > 0
                && dock.findFocus() == null) {
            dock.getChildAt(0).requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(saverTimeout);
        if (saverActive) exitSaver();
        hideShelf();
        handler.removeCallbacks(wallpaperTick);
        if (movingTile != null) endMove();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stoppedSinceResume = true;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        unregisterReceiver(screenOffReceiver);
        shelfWorker.shutdownNow();
        TvInputManager inputs = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (inputs != null) inputs.unregisterCallback(inputStateCallback);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (movingTile != null) endMove();
        boolean wantsInputs = intent.getBooleanExtra(EXTRA_SHOW_INPUTS, false);
        if (wantsInputs && inputsOverlay.getVisibility() == View.VISIBLE) {
            focusNextInput();
            return;
        }
        boolean overOtherApp = stoppedSinceResume; // onPause ne suffit pas : l'accueil y passe aussi avant onNewIntent
        closeAllApps(false);
        hidePanels();
        if (wantsInputs) {
            showInputs();
            inputsOverOtherApp = overOtherApp;
            playNavigationSound(root, View.FOCUS_RIGHT);
        }
    }

    /**
     * Toutes les touches passent ici : l'économiseur les intercepte, et la navigation par défaut est faite à la main,
     * car Android y joue son son sans permettre de le couper pour une seule appli.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        keyHeld = down && ev.getRepeatCount() > 0;
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(ev);
        }
        if (code == swallowedKey) {
            if (!down) swallowedKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (saverActive) {
            if (down) {
                exitSaver();
                armSaver();
                swallowedKey = code;
            }
            return true;
        }
        armSaver();
        if (super.dispatchKeyEvent(ev)) return true;
        if (!down) return false;
        int direction = focusDirection(code);
        if (direction == 0) return false;
        View next = FocusFinder.getInstance().findNextFocus(root, getCurrentFocus(), direction);
        if (next != null && next.requestFocus(direction)) playNavigationSound(next, direction);
        return true;
    }

    private int focusDirection(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    /** Dock en bas, sélection sur la première tuile, aucun menu ouvert. */
    private void resetHome() {
        if (movingTile != null) endMove();
        hideShelf();
        closeAllApps(false);
        hidePanels();
        saverActive = false;
        swallowedKey = KeyEvent.KEYCODE_UNKNOWN;
        for (View v : new View[]{dockScroll, allAppsScroll, allAppsOverlay, clock}) v.animate().cancel();
        dockScroll.setAlpha(1f);
        dockScroll.setTranslationY(0);
        dockScroll.scrollTo(0, 0);
        allAppsScroll.setTranslationY(gridPeekOffset());
        allAppsOverlay.setAlpha(1f);
        clock.setAlpha(1f);
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    // ---------- Économiseur : seul le fond reste à l'écran après un moment sans toucher à la télécommande ----------

    private void armSaver() {
        handler.removeCallbacks(saverTimeout);
        int minutes = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SAVER_MINUTES, 2);
        if (minutes > 0) handler.postDelayed(saverTimeout, minutes * WALLPAPER_TICK_MS);
    }

    private void enterSaver() {
        if (movingTile != null) { // on ne coupe pas un déplacement en cours
            armSaver();
            return;
        }
        hidePanels();
        closeAllApps(true);
        hideShelf();
        saverActive = true;
        int away = px(DOCK_HEIGHT + 60);
        dockScroll.animate().alpha(0f).translationY(away).setDuration(slow(420)).setInterpolator(ease).start();
        allAppsScroll.animate().translationY(gridPeekOffset() + away).setDuration(slow(420)).setInterpolator(ease).start();
    }

    private void exitSaver() {
        saverActive = false;
        dockScroll.animate().alpha(1f).translationY(0).setDuration(slow(380)).setInterpolator(ease).start();
        allAppsScroll.animate().translationY(gridPeekOffset()).setDuration(slow(380)).setInterpolator(ease).start();
        refreshShelf();
    }

    @Override
    public void onBackPressed() {
        if (movingTile != null) endMove();
        else if (accessibilityPopup != null) hideAccessibilityPopup(true);
        else if (shelfRow.findFocus() != null) focusShelfTile();
        else if (allAppsOpen) closeAllApps(true);
        else if (inSourcesPage) closeSourcesPage();
        else {
            boolean backToOtherApp = inputsOverOtherApp && inputsOverlay.getVisibility() == View.VISIBLE;
            hidePanels();
            if (backToOtherApp) moveTaskToBack(true);
        }
        // Écran d'accueil : retour ne quitte pas.
    }

    // ---------- Fond d'écran ----------

    /** Les fichiers sont nommés <thème>_<moment>.jpg ; on garde la liste des thèmes. */
    private void loadThemes() {
        TreeSet<String> set = new TreeSet<>();
        try {
            for (String f : getAssets().list("wallpapers")) {
                int us = f.lastIndexOf('_');
                if (us > 0) set.add(f.substring(0, us));
            }
        } catch (Exception ignored) {
        }
        themes.addAll(set);
        lastWallpaperChange = System.currentTimeMillis();
        if (!themes.isEmpty()) themeIndex = (int) ((System.currentTimeMillis() / WALLPAPER_TICK_MS) % themes.size());
    }

    private ImageView newBackground() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    /** Thème courant, éclairé comme maintenant : mélange des deux moments de la journée qui encadrent l'heure. */
    private void showWallpaper() {
        if (themes.isEmpty()) return;
        final String theme = themes.get(themeIndex % themes.size());
        final DayPhase p = DayPhase.now();
        final String key = theme + p.from + p.to + Math.round(p.mix * 20);
        if (key.equals(shownWallpaper)) return;
        shownWallpaper = key;
        new Thread(() -> {
            Bitmap bmp = decode(theme + "_" + p.from + ".jpg", true);
            if (bmp != null && p.mix > 0.02f && !p.from.equals(p.to)) {
                Bitmap next = decode(theme + "_" + p.to + ".jpg", false);
                if (next != null) {
                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    paint.setAlpha(Math.round(p.mix * 255));
                    new Canvas(bmp).drawBitmap(next, 0, 0, paint);
                    next.recycle();
                }
            }
            final Bitmap result = bmp;
            if (result == null) return;
            final Bitmap blurred = blurred(result);
            handler.post(() -> {
                crossfadeTo(result);
                fadeDockBackdropTo(blurred);
            });
        }).start();
    }

    private Bitmap decode(String name, boolean mutable) {
        try (InputStream in = getAssets().open("wallpapers/" + name)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            o.inMutable = mutable;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** bgBack garde l'image courante figée pendant que bgFront fait apparaître la nouvelle. */
    private void crossfadeTo(Bitmap bmp) {
        if (fadeAnim != null) fadeAnim.cancel();
        bgBack.setImageDrawable(bgFront.getDrawable());
        bgFront.setImageBitmap(bmp);
        bgFront.setAlpha(0f);
        fadeAnim = ObjectAnimator.ofFloat(bgFront, View.ALPHA, 0f, 1f).setDuration(FADE_MS);
        fadeAnim.start();
    }

    // ---------- Fond flou du dock ----------

    /** Le paysage réduit puis flouté : à cette taille, c'est instantané, et l'agrandissement lisse le reste. */
    private Bitmap blurred(Bitmap src) {
        Bitmap small = Bitmap.createScaledBitmap(src, BLUR_W, BLUR_H, true);
        int[] px = new int[BLUR_W * BLUR_H];
        small.getPixels(px, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, BLUR_W, BLUR_H, 1, BLUR_W);
            boxBlur(px, BLUR_H, BLUR_W, BLUR_W, 1);
        }
        return Bitmap.createBitmap(px, BLUR_W, BLUR_H, Bitmap.Config.RGB_565);
    }

    /** Moyenne glissante sur chaque ligne : length pixels espacés de step, lines lignes espacées de lineStep. */
    private void boxBlur(int[] px, int length, int lines, int step, int lineStep) {
        final int radius = 3, size = radius * 2 + 1;
        int[] line = new int[length];
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep;
            for (int i = 0; i < length; i++) line[i] = px[base + i * step];
            int r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = line[Math.max(0, Math.min(length - 1, i))];
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
            for (int i = 0; i < length; i++) {
                px[base + i * step] = 0xFF000000 | (r / size) << 16 | (g / size) << 8 | (b / size);
                int out = line[Math.max(0, i - radius)], in = line[Math.min(length - 1, i + radius + 1)];
                r += ((in >> 16) & 255) - ((out >> 16) & 255);
                g += ((in >> 8) & 255) - ((out >> 8) & 255);
                b += (in & 255) - (out & 255);
            }
        }
    }

    private void fadeDockBackdropTo(Bitmap next) {
        if (blurFade != null) blurFade.cancel();
        blurBack = blurFront;
        blurFront = next;
        blurMix = blurBack == null ? 1f : 0f;
        blurFade = ValueAnimator.ofFloat(blurMix, 1f).setDuration(FADE_MS);
        blurFade.addUpdateListener(a -> {
            blurMix = (float) a.getAnimatedValue();
            refreshDockBackdrop();
        });
        blurFade.start();
    }

    private void setDockBlur(boolean on) {
        dockBlur = on;
        shelf.setUnderlay(on ? this::drawDockBackdrop : null);
    }

    private void refreshDockBackdrop() {
        if (dockBlur) dock.invalidate();
    }

    /** Dessine le paysage flouté à sa place à l'écran, pour que le dock semble le voir à travers lui. */
    private void drawDockBackdrop(Canvas c) {
        if (blurFront == null) return;
        int[] loc = new int[2];
        dock.getLocationOnScreen(loc);
        c.translate(-loc[0], -loc[1]);
        c.scale(root.getWidth() / (float) BLUR_W, root.getHeight() / (float) BLUR_H);
        if (blurBack != null && blurMix < 1f) {
            blurPaint.setAlpha(255);
            c.drawBitmap(blurBack, 0, 0, blurPaint);
        }
        blurPaint.setAlpha(Math.round(blurMix * 255));
        c.drawBitmap(blurFront, 0, 0, blurPaint);
        // Le dock passe devant le voile de la grille : on l'assombrit pareil.
        int alpha = Math.round((PANEL_BG >>> 24) * allAppsVeil.getAlpha());
        if (alpha > 0) c.drawColor((alpha << 24) | (PANEL_BG & 0xFFFFFF));
    }

    // ---------- Horloge ----------

    private View buildClock() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        // Heure française, changement d'heure compris, même si le fuseau de la télé n'en a pas.
        time.setTimeZone(DayPhase.FRANCE.getID());
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(44);
        time.setTypeface(fontLight);
        time.setShadowLayer(10, 0, 2, 0x88000000);
        TextClock date = new TextClock(this);
        date.setTimeZone(DayPhase.FRANCE.getID());
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTypeface(fontRegular);
        timeClock = time;
        dateClock = date;
        date.setTextColor(0xDDFFFFFF);
        date.setTextSize(16);
        date.setShadowLayer(8, 0, 2, 0x88000000);
        box.addView(time);
        box.addView(date);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = px(36);
        lp.rightMargin = px(56);
        box.setLayoutParams(lp);
        fadeIn(box);
        return box;
    }

    // ---------- Applications ----------

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        apps.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName())) continue;
            AppEntry e = new AppEntry();
            e.pkg = ai.packageName;
            e.label = ri.loadLabel(pm);
            Drawable b = ai.loadBanner(pm);
            if (b == null) b = ai.applicationInfo.loadBanner(pm);
            e.isBanner = b != null;
            e.banner = b != null ? b : ri.loadIcon(pm);
            if (b != null) e.bannerBackdrop = bannerBackdrop(b);
            apps.add(e);
        }
        apps.add(actionEntry(TILE_SOURCES, R.string.inputs, TileArt.SOURCES));
        apps.add(actionEntry(TILE_SETTINGS, R.string.settings, TileArt.SETTINGS));
        apps.add(actionEntry(TILE_POWER, R.string.power_off, TileArt.POWER));
        Collections.sort(apps, (a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
    }

    /** Null pour un logo sur fond transparent : on le verrait en double, flou, derrière lui-même. */
    private Bitmap bannerBackdrop(Drawable banner) {
        final int w = 32, h = 18, bleed = 4;
        Rect old = banner.copyBounds();
        Bitmap whole = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        banner.setBounds(0, 0, w, h);
        banner.draw(new Canvas(whole));
        boolean opaque = Color.alpha(whole.getPixel(w / 2, 1)) > 250 && Color.alpha(whole.getPixel(1, h / 2)) > 250;
        whole.recycle();
        Bitmap out = null;
        if (opaque) {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            banner.setBounds(-bleed, -bleed, w + bleed, h + bleed);
            banner.draw(new Canvas(out));
        }
        banner.setBounds(old);
        return out;
    }

    private AppEntry actionEntry(String pkg, int label, int art) {
        AppEntry e = new AppEntry();
        e.pkg = pkg;
        e.label = getString(label);
        e.banner = new TileArt(art);
        e.isBanner = true;
        e.bannerBackdrop = bannerBackdrop(e.banner);
        return e;
    }

    /** Les tuiles internes s'appelaient « zen:… » jusqu'à la version 2.6.7 : elles gardent leur place. */
    private void renameSavedTiles() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : new String[]{KEY_DOCK, KEY_GRID}) {
            String saved = prefs.getString(key, null);
            if (saved != null) edit.putString(key, saved.replace("zen:", "pomelo:"));
        }
        edit.apply();
    }

    /** Les anciens boutons Sources et Réglages deviennent des tuiles, à la fin du dock. */
    private void addActionTilesOnce() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ACTION_TILES, false)) return;
        List<String> pkgs = dockPackages();
        if (!pkgs.contains(TILE_SOURCES)) pkgs.add(TILE_SOURCES);
        if (!pkgs.contains(TILE_SETTINGS)) pkgs.add(TILE_SETTINGS);
        prefs.edit().putString(KEY_DOCK, TextUtils.join(",", pkgs)).putBoolean(KEY_ACTION_TILES, true).apply();
    }

    private AppEntry find(String pkg) {
        for (AppEntry e : apps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    private List<String> dockPackages() {
        String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DOCK, null);
        List<String> out = new ArrayList<>();
        if (s == null) {
            for (String p : DEFAULT_DOCK) {
                if (out.size() < DEFAULT_DOCK_SIZE && find(p) != null) out.add(p);
            }
            return out;
        }
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private List<String> visibleDock() {
        List<String> out = new ArrayList<>();
        for (String p : dockPackages()) if (find(p) != null && out.size() < DOCK_MAX) out.add(p);
        return out;
    }

    /** Les apps hors du dock, dans l'ordre choisi ; les nouvelles arrivent à la fin, par ordre alphabétique. */
    private List<String> gridPackages() {
        List<String> docked = visibleDock(), out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GRID, "");
        for (String p : saved.split(",")) {
            if (find(p) != null && !docked.contains(p) && !out.contains(p)) out.add(p);
        }
        for (AppEntry e : apps) if (!docked.contains(e.pkg) && !out.contains(e.pkg)) out.add(e.pkg);
        return out;
    }

    private void saveLayout(List<String> docked, List<String> grid) {
        List<String> order = new ArrayList<>(docked);
        for (String p : dockPackages()) if (find(p) == null) order.add(p); // apps désinstallées : on garde leur place en fin
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_DOCK, TextUtils.join(",", order))
                .putString(KEY_GRID, TextUtils.join(",", grid)).apply();
    }

    private void launch(String pkg) {
        closeAllApps(false);
        if (TILE_SOURCES.equals(pkg)) {
            showInputs();
            return;
        }
        if (TILE_SETTINGS.equals(pkg)) {
            showSettings();
            return;
        }
        if (TILE_POWER.equals(pkg)) {
            if (sleepAvailable()) {
                HomeKeyService.sleepNow();
            } else { // l'extinction passe par ce service : on explique comment l'activer
                showSettings();
                showAccessibilityPopup();
            }
            return;
        }
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void startSafe(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Dock ----------

    private void buildDock(boolean intro) {
        View focused = dock.findFocus();
        Object focusedTag = focused != null ? focused.getTag() : null;
        dock.removeAllViews();
        for (String pkg : visibleDock()) {
            AppEntry e = find(pkg);
            View tile = appTile(e);
            tile.setTag(pkg);
            tile.setOnClickListener(v -> launch(e.pkg));
            tile.setOnLongClickListener(v -> {
                if (movingTile == null) startMove(v);
                return true;
            });
            tile.setOnKeyListener(this::onTileKey);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(GRID_TILE_W), px(GRID_TILE_H));
            lp.leftMargin = lp.rightMargin = px(GRID_GAP / 2f);
            dock.addView(tile, lp);
        }
        for (int i = 0; i < dock.getChildCount(); i++) {
            View c = dock.getChildAt(i);
            if (focusedTag != null && focusedTag.equals(c.getTag())) c.requestFocus();
        }
        if (intro) {
            fadeIn(dockScroll);
            fadeIn(allAppsOverlay);
        }
    }

    // ---------- Mode giggle : réorganiser les apps (OK maintenu) ----------

    private int appTileCount() {
        int n = 0;
        while (n < dock.getChildCount() && dock.getChildAt(n).getTag() instanceof String
                && find((String) dock.getChildAt(n).getTag()) != null) n++;
        return n;
    }

    private void startMove(View tile) {
        movingTile = tile;
        ignoreCenterUp = true;
        tile.setTranslationZ(px(8));
        hideShelf();
        updateTileSmoothing(tile);
        startWiggle();
    }

    /**
     * Lente oscillation en rotation et en translation, sur des périodes différentes pour qu'elle ne se répète pas :
     * les sinus donnent l'inertie (ralentit aux extrêmes). Elle suit movingTile, même quand la tuile est recréée.
     */
    /** Une tuile qui perd la sélection reste lissée le temps de reprendre sa taille. */
    private void updateTileSmoothing(View tile) {
        if (!(tile instanceof Squircle.Frame)) return;
        boolean smooth = tileSmoothing == SMOOTH_ALL
                || (tileSmoothing == SMOOTH_SELECTED && (tile.hasFocus() || tile == movingTile));
        ((Squircle.Frame) tile).setSmoothEdges(smooth);
    }

    private void startWiggle() {
        long start = SystemClock.uptimeMillis();
        wiggle = ValueAnimator.ofFloat(0f, 1f);
        wiggle.setDuration(1000);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.addUpdateListener(an -> {
            View tile = movingTile;
            if (tile == null) return;
            double t = (SystemClock.uptimeMillis() - start) * 2 * Math.PI;
            tile.setRotation((float) (1.5 * Math.sin(t / 289)));
            tile.setTranslationX(dockEdgeShift(tile) + moveFromX * moveSlideLeft + (float) (dp * Math.sin(t / 378)));
            tile.setTranslationY(moveFromY * moveSlideLeft + (float) (0.75 * dp * Math.sin(t / 467)));
        });
        wiggle.start();
    }

    private void endMove() {
        View tile = movingTile;
        movingTile = null;
        if (wiggle != null) wiggle.cancel();
        if (moveSlide != null) moveSlide.cancel();
        tile.setTranslationZ(0);
        tile.animate().rotation(0).translationX(dockEdgeShift(tile)).translationY(0)
                .setStartDelay(0).setDuration(slow(260)).setInterpolator(ease)
                .withEndAction(() -> updateTileSmoothing(tile)).start();
        animateFocus(tile, tile.hasFocus(), tile.getParent() == inputsRow ? CARD_FOCUS_SCALE : TILE_FOCUS_SCALE);
        handler.postDelayed(() -> {
            if (movingTile == null) allAppsOverlay.setClipChildren(true);
        }, slow(340));
        refreshShelf();
    }

    /** Comme sur l'Apple TV : vers le bas, le dock monte et les autres apps apparaissent dessous ; vers le haut, retour. */
    private boolean onDockVerticalKey(int code, KeyEvent ev) {
        boolean press = ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0;
        if (allAppsOpen) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (press) closeAllApps(true);
                return true;
            }
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (press) focusGridUnder(dock.findFocus());
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (shelfRoot.getVisibility() != View.VISIBLE || shelfRow.getChildCount() == 0) return false;
            if (press) {
                shelfRow.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                View first = shelfRow.getChildAt(0);
                if (first.requestFocus()) playNavigationSound(first, View.FOCUS_UP);
            }
            return true;
        }
        if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (press) showAllApps();
        return true;
    }

    private boolean onTileKey(View v, int code, KeyEvent ev) {
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        // OK maintenu : les télécommandes envoient des répétitions, on entre en mode giggle dès la première.
        if (movingTile == null && center && down && (ev.isLongPress() || ev.getRepeatCount() > 0)) {
            startMove(v);
            return true;
        }
        if (movingTile != null) return onMovingKey(code, ev);
        if (v.getParent() == dock) return onDockVerticalKey(code, ev);
        return down && onGridEdgeKey(v, code);
    }

    private boolean onMovingKey(int code, KeyEvent ev) {
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) moveTile(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    if (ignoreCenterUp && code != KeyEvent.KEYCODE_BACK) ignoreCenterUp = false;
                    else endMove();
                }
                return true;
            default:
                return false;
        }
    }

    /** Déplace l'app en mode giggle dans le dock ou la grille ; l'ordre est enregistré aussitôt et tout se réarrange. */
    private void moveTile(int code) {
        String pkg = (String) movingTile.getTag();
        List<String> docked = visibleDock(), grid = gridPackages();
        int i = docked.indexOf(pkg);
        boolean leavesDock = false;
        if (i >= 0) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && i > 0) Collections.swap(docked, i, i - 1);
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i < docked.size() - 1) Collections.swap(docked, i, i + 1);
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && docked.size() > 1) { // le dock garde au moins une tuile
                docked.remove(i);
                grid.add(Math.min(gridColumnUnder(movingTile), grid.size()), pkg);
                leavesDock = true;
            } else return;
        } else {
            i = grid.indexOf(pkg);
            int last = grid.size() - 1;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && i % GRID_COLUMNS > 0) Collections.swap(grid, i, i - 1);
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i % GRID_COLUMNS < GRID_COLUMNS - 1 && i < last) {
                Collections.swap(grid, i, i + 1);
            } else if (code == KeyEvent.KEYCODE_DPAD_UP && i < GRID_COLUMNS) {
                grid.remove(i);
                docked.add(dockIndexAbove(movingTile), pkg);
                // Dock plein : la dernière tuile laisse sa place et descend là où était celle qui monte.
                if (docked.size() > DOCK_MAX) grid.add(i, docked.remove(DOCK_MAX));
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                grid.add(i - GRID_COLUMNS, grid.remove(i));
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && i / GRID_COLUMNS < last / GRID_COLUMNS) {
                int to = Math.min(i + GRID_COLUMNS, last);
                grid.add(to, grid.remove(i));
            } else return;
        }

        allAppsOverlay.setClipChildren(false); // laisse voir la tuile hors de la grille pendant son trajet
        Map<String, int[]> before = tilePositions();
        saveLayout(docked, grid);
        if (leavesDock) showAllApps();
        buildDock(false);
        buildAllApps();

        View tile = findTile(pkg);
        movingTile = tile;
        tile.animate().cancel();
        tile.setAlpha(1f);
        tile.setScaleX(TILE_FOCUS_SCALE);
        tile.setScaleY(TILE_FOCUS_SCALE);
        tile.setTranslationZ(px(8));
        tile.requestFocus();
        updateTileSmoothing(tile);
        animateTilesFrom(before);
    }

    private View findTile(String pkg) {
        for (View tile : allTiles()) if (pkg.equals(tile.getTag())) return tile;
        throw new IllegalStateException(pkg);
    }

    /** Rang où insérer dans le dock une app qui arrive de la grille, d'après sa position à l'écran. */
    private int dockIndexAbove(View tile) {
        int center = screenCenterX(tile), count = appTileCount();
        for (int i = 0; i < count; i++) if (screenCenterX(dock.getChildAt(i)) > center) return i;
        return count;
    }

    /** Colonne de la grille sous une tuile du dock (la grille n'est pas forcément mise en page à ce moment-là). */
    private int gridColumnUnder(View tile) {
        int step = px(GRID_TILE_W) + px(GRID_GAP);
        int gridWidth = GRID_COLUMNS * step - px(GRID_GAP);
        int left = (getResources().getDisplayMetrics().widthPixels - gridWidth) / 2;
        int column = Math.round((screenCenterX(tile) - left - px(GRID_TILE_W) / 2f) / step);
        return Math.max(0, Math.min(GRID_COLUMNS - 1, column));
    }

    // ---------- Toutes les apps ----------

    /** Toujours affichée : sur l'accueil, la grille attend en bas de l'écran, seul le haut de sa première ligne dépasse. */
    private FrameLayout buildAllAppsOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        allAppsScroll = new SmoothScroll(this, px(40));
        allAppsScroll.setVerticalScrollBarEnabled(false);
        allAppsScroll.setClipChildren(false);
        allAppsScroll.setClipToPadding(false);
        allAppsScroll.setPadding(0, px(GRID_PADDING_TOP), 0, 0);
        allAppsScroll.setTranslationY(gridPeekOffset());
        allAppsGrid = new FrameLayout(this);
        allAppsGrid.setClipChildren(false);
        allAppsGrid.setClipToPadding(false);
        allAppsGrid.setPadding(0, 0, 0, px(40));
        allAppsGrid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        int step = px(GRID_TILE_W) + px(GRID_GAP);
        allAppsScroll.addView(allAppsGrid, new FrameLayout.LayoutParams(
                GRID_COLUMNS * step - px(GRID_GAP), -2, Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams lp = match();
        lp.topMargin = px(DOCK_OPEN_TOP + DOCK_HEIGHT);
        overlay.addView(allAppsScroll, lp);
        return overlay;
    }

    private float gridPeekOffset() {
        int gridTop = px(DOCK_OPEN_TOP + DOCK_HEIGHT) + px(GRID_PADDING_TOP);
        return getResources().getDisplayMetrics().heightPixels - px(GRID_PEEK) - gridTop;
    }

    /** Les apps hors du dock, sans trou : chacune est placée d'après son rang. */
    private void buildAllApps() {
        allAppsGrid.removeAllViews();
        int stepX = px(GRID_TILE_W) + px(GRID_GAP), stepY = px(GRID_TILE_H) + px(GRID_ROW_GAP);
        int i = 0;
        for (String pkg : gridPackages()) {
            AppEntry e = find(pkg);
            View tile = appTile(e);
            tile.setTag(e.pkg);
            tile.setOnClickListener(v -> launch(e.pkg));
            tile.setOnLongClickListener(v -> {
                if (movingTile == null) startMove(v);
                return true;
            });
            tile.setOnKeyListener(this::onTileKey);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(GRID_TILE_W), px(GRID_TILE_H));
            lp.leftMargin = (i % GRID_COLUMNS) * stepX;
            lp.topMargin = (i / GRID_COLUMNS) * stepY;
            allAppsGrid.addView(tile, lp);
            i++;
        }
    }

    /**
     * Le dock est décalé à l'écran mais pas dans la mise en page : Android ne le trouve pas tout seul au-dessus
     * de la grille et pourrait y sauter depuis les bords. Les sorties de la grille sont donc gérées ici.
     */
    private boolean onGridEdgeKey(View tile, int code) {
        int i = allAppsGrid.indexOfChild(tile), last = allAppsGrid.getChildCount() - 1;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (i >= GRID_COLUMNS) return false;
                focusNearest(tile, dock, 0, dock.getChildCount());
                playNavigationSound(dock, View.FOCUS_UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return i / GRID_COLUMNS == last / GRID_COLUMNS;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return i % GRID_COLUMNS == 0;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return i % GRID_COLUMNS == GRID_COLUMNS - 1 || i == last;
            default:
                return false;
        }
    }

    /** Sélectionne la tuile de la première ligne sous from, ou la plus proche si la ligne est incomplète. */
    private void focusGridUnder(View from) {
        int count = allAppsGrid.getChildCount();
        if (count == 0) return;
        int column = from != null ? gridColumnUnder(from) : 0;
        View tile = allAppsGrid.getChildAt(Math.min(column, count - 1));
        if (tile.requestFocus()) playNavigationSound(tile, View.FOCUS_DOWN);
    }

    /** Donne le focus à l'enfant sélectionnable de group le plus proche de from, horizontalement. */
    private void focusNearest(View from, ViewGroup group, int start, int end) {
        View best = null;
        int bestDistance = Integer.MAX_VALUE, center = screenCenterX(from);
        for (int i = start; i < end; i++) {
            View c = group.getChildAt(i);
            int distance = Math.abs(screenCenterX(c) - center);
            if (c.isFocusable() && distance < bestDistance) {
                best = c;
                bestDistance = distance;
            }
        }
        if (best != null) best.requestFocus();
    }

    private int screenCenterX(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return loc[0] + Math.round(v.getWidth() * v.getScaleX() / 2);
    }

    private Map<String, int[]> tilePositions() {
        dockWidthBefore = dock.getWidth();
        Map<String, int[]> out = new HashMap<>();
        for (View tile : allTiles()) {
            int[] loc = new int[2];
            tile.getLocationOnScreen(loc);
            out.put((String) tile.getTag(), loc);
        }
        return out;
    }

    private List<View> allTiles() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) out.add(dock.getChildAt(i));
        for (int i = 0; i < allAppsGrid.getChildCount(); i++) out.add(allAppsGrid.getChildAt(i));
        return out;
    }

    /** Une fois la nouvelle disposition calculée, chaque élément glisse depuis son ancienne place, et le dock s'ajuste. */
    private void animateTilesFrom(Map<String, int[]> before) {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                for (View tile : allTiles()) {
                    int[] old = before.get(tile.getTag());
                    if (old == null) continue;
                    int[] loc = new int[2];
                    tile.getLocationOnScreen(loc);
                    if (tile == movingTile) {
                        slideMovingTile(old[0] - loc[0], old[1] - loc[1]);
                        continue;
                    }
                    tile.setTranslationX(old[0] - loc[0]);
                    tile.setTranslationY(old[1] - loc[1]);
                    tile.animate().translationX(0).translationY(0).setStartDelay(0)
                            .setDuration(slow(340)).setInterpolator(ease).start();
                }
                float grew = (dock.getWidth() - dockWidthBefore) / 2f;
                if (grew != 0) {
                    shelf.setInsetX(grew);
                    ValueAnimator a = ValueAnimator.ofFloat(grew, 0f).setDuration(slow(340));
                    a.setInterpolator(ease);
                    a.addUpdateListener(an -> shelf.setInsetX((float) an.getAnimatedValue()));
                    a.start();
                }
                return true;
            }
        });
    }

    /** La tuile en mode giggle gère sa translation elle-même : ce glissement s'ajoute à son oscillation. */
    private void slideMovingTile(float fromX, float fromY) {
        if (moveSlide != null) moveSlide.cancel();
        moveFromX = fromX;
        moveFromY = fromY;
        moveSlideLeft = 1f;
        movingTile.setTranslationX(fromX);
        movingTile.setTranslationY(fromY);
        moveSlide = ValueAnimator.ofFloat(1f, 0f).setDuration(slow(340));
        moveSlide.setInterpolator(ease);
        moveSlide.addUpdateListener(an -> moveSlideLeft = (float) an.getAnimatedValue());
        moveSlide.start();
    }

    private void showAllApps() {
        if (allAppsOpen) return;
        hideShelf();
        allAppsOpen = true;
        allAppsReturnFocus = dock.findFocus();
        loadApps();
        buildAllApps();
        allAppsGrid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        dockScroll.animate().translationY(px(DOCK_OPEN_TOP) - dockScroll.getTop())
                .setDuration(slow(554)).setInterpolator(ease).start();
        allAppsScroll.animate().translationY(0).setDuration(slow(554)).setInterpolator(ease).start();
        allAppsVeil.animate().alpha(1f).setDuration(slow(396)).setInterpolator(ease).start();
        clock.animate().alpha(0f).setStartDelay(0).setDuration(slow(150)).start();
        focusGridUnder(allAppsReturnFocus);
    }

    private void closeAllApps(boolean animated) {
        if (!allAppsOpen) return;
        allAppsOpen = false;
        shelfNoAnimation = animated;
        if (dock.findFocus() == null) {
            if (allAppsReturnFocus != null && allAppsReturnFocus.getParent() == dock) allAppsReturnFocus.requestFocus();
            else if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        }
        allAppsGrid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        allAppsScroll.scrollTo(0, 0);
        if (animated) {
            dockScroll.animate().translationY(0).setDuration(slow(554)).setInterpolator(ease).start();
            allAppsScroll.animate().translationY(gridPeekOffset()).setDuration(slow(554)).setInterpolator(ease).start();
            allAppsVeil.animate().alpha(0f).setDuration(slow(396)).setInterpolator(ease).start();
            clock.animate().alpha(1f).setStartDelay(100).setDuration(slow(300)).start();
            refreshShelf();
            shelfNoAnimation = false;
            return;
        }
        dockScroll.animate().cancel();
        dockScroll.setTranslationY(0);
        allAppsScroll.animate().cancel();
        allAppsScroll.setTranslationY(gridPeekOffset());
        allAppsVeil.animate().cancel();
        allAppsVeil.setAlpha(0f);
        clock.animate().cancel();
        clock.setAlpha(1f);
    }

    // ---------- Panneaux (entrées, réglages) ----------

    private boolean panelVisible() {
        return inputsOverlay.getVisibility() == View.VISIBLE || settingsOverlay.getVisibility() == View.VISIBLE;
    }

    /** Le dock reste derrière le panneau : on le bloque pour que le focus n'y retombe pas. */
    private void showPanel(FrameLayout panel) {
        hideShelf();
        panelReturnFocus = dock.findFocus();
        dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dockScroll.animate().alpha(0f).translationY(px(20)).setDuration(slow(180)).start();
        clock.animate().alpha(0f).setStartDelay(0).setDuration(slow(150)).start();
        allAppsOverlay.animate().alpha(0f).setDuration(slow(180)).start();
        panel.setAlpha(0f);
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1f).setDuration(slow(220)).setInterpolator(ease).start();
    }

    private void hidePanels() {
        if (!panelVisible()) return;
        inputsOverlay.setVisibility(View.GONE);
        hideAccessibilityPopup(false);
        settingsOverlay.setVisibility(View.GONE);
        inSourcesPage = false;
        inputsOverOtherApp = false;
        allAppsOverlay.animate().alpha(1f).setDuration(slow(300)).start();
        dock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        dockScroll.animate().alpha(1f).translationY(0).setDuration(slow(300)).setInterpolator(ease).start();
        clock.animate().alpha(1f).setStartDelay(0).setDuration(slow(300)).start();
        if (panelReturnFocus != null && panelReturnFocus.getParent() == dock) panelReturnFocus.requestFocus();
        else if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    private TextView panelTitle(int text) {
        TextView title = new TextView(this);
        title.setTypeface(fontRegular);
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(fontMedium);
        return title;
    }

    /**
     * Les entrées matérielles de la TV. Les tuners (souvent un par norme : ATV, DTV…) n'en font qu'une seule,
     * et les chaînes fournies par des applis, qui passent aussi pour des tuners, sont écartées.
     */
    private List<TvInputInfo> tvInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager m = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (m == null) return out;
        Set<String> hardwarePkgs = new HashSet<>();
        for (TvInputInfo info : m.getTvInputList()) {
            if (info.isPassthroughInput()) hardwarePkgs.add(info.getServiceInfo().packageName);
        }
        TvInputInfo tuner = null;
        for (TvInputInfo info : m.getTvInputList()) {
            if (info.isPassthroughInput()) out.add(info);
            else if (info.getType() == TvInputInfo.TYPE_TUNER
                    && (hardwarePkgs.isEmpty() || hardwarePkgs.contains(info.getServiceInfo().packageName))
                    && (tuner == null || info.loadLabel(this).toString().contains("DTV"))) tuner = info;
        }
        if (tuner != null) out.add(tuner);
        Collections.sort(out, (a, b) -> {
            boolean ta = a.getType() == TvInputInfo.TYPE_TUNER, tb = b.getType() == TvInputInfo.TYPE_TUNER;
            if (ta != tb) return ta ? -1 : 1;
            return inputLabel(a).toString().compareToIgnoreCase(inputLabel(b).toString());
        });
        // L'ordre choisi en mode giggle passe devant ; une nouvelle source arrive à la fin.
        List<String> order = Arrays.asList(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_INPUTS_ORDER, "").split(","));
        Collections.sort(out, (a, b) -> Integer.compare(orderRank(order, a), orderRank(order, b)));
        return out;
    }

    private int orderRank(List<String> order, TvInputInfo info) {
        int rank = order.indexOf(info.getId());
        return rank >= 0 ? rank : Integer.MAX_VALUE;
    }

    /** Enregistre l'ordre affiché ; les sources masquées gardent leur place. */
    private void saveInputOrder() {
        Set<String> hidden = hiddenInputs();
        List<String> order = new ArrayList<>();
        int shown = 0;
        for (TvInputInfo info : tvInputs()) {
            order.add(hidden.contains(info.getId()) ? info.getId() : (String) inputsRow.getChildAt(shown++).getTag());
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_INPUTS_ORDER, TextUtils.join(",", order)).apply();
    }

    private CharSequence inputLabel(TvInputInfo info) {
        if (info.getType() == TvInputInfo.TYPE_TUNER) return getString(R.string.tv_input);
        CharSequence custom = info.loadCustomLabel(this);
        return TextUtils.isEmpty(custom) ? info.loadLabel(this) : custom;
    }

    private Set<String> hiddenInputs() {
        return new HashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN_INPUTS, Collections.emptySet()));
    }

    private void setInputHidden(String id, boolean hidden) {
        Set<String> set = hiddenInputs();
        if (hidden) set.add(id);
        else set.remove(id);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN_INPUTS, set).apply();
    }

    // ---------- Entrées ----------

    private FrameLayout buildInputsOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(PANEL_BG);
        overlay.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        TextView title = panelTitle(R.string.inputs);
        inputsTitle = title;
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, px(28));
        col.addView(title);

        HorizontalScrollView scroll = new EdgeScroll(this, px(57)); // marge de la rangée + marge d'une carte
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        inputsRow = new LinearLayout(this);
        inputsRow.setClipChildren(false);
        inputsRow.setClipToPadding(false);
        inputsRow.setPadding(px(48), px(16), px(48), px(16));
        scroll.addView(inputsRow);
        col.addView(scroll, new LinearLayout.LayoutParams(-2, -2));
        overlay.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return overlay;
    }

    private void showInputs() {
        closeAllApps(false);
        fillInputsRow();
        if (inputsRow.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_LONG).show();
            showSettings();
            openSourcesPage();
            return;
        }
        showPanel(inputsOverlay);
        inputsRow.getChildAt(0).requestFocus();
    }

    private boolean onInputCardKey(View v, int code, KeyEvent ev) {
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (movingTile == null && center && down && (ev.isLongPress() || ev.getRepeatCount() > 0)) {
            startMove(v);
            return true;
        }
        if (movingTile != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) shiftInput(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                default:
                    return onMovingKey(code, ev);
            }
        }
        // La rangée est seule à l'écran : haut et bas ne mènent nulle part.
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** Échange la carte en mode giggle avec sa voisine ; les cartes glissent vers leur nouvelle place. */
    private void shiftInput(int direction) {
        View card = movingTile;
        int from = inputsRow.indexOfChild(card), to = from + direction;
        if (to < 0 || to >= inputsRow.getChildCount()) return;
        Map<View, Integer> before = new HashMap<>();
        for (int i = 0; i < inputsRow.getChildCount(); i++) {
            View c = inputsRow.getChildAt(i);
            int[] loc = new int[2];
            c.getLocationOnScreen(loc);
            before.put(c, loc[0]);
        }
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        inputsRow.removeViewAt(from);
        inputsRow.addView(card, to, lp);
        card.requestFocus();
        saveInputOrder();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                for (Map.Entry<View, Integer> en : before.entrySet()) {
                    View c = en.getKey();
                    int[] loc = new int[2];
                    c.getLocationOnScreen(loc);
                    int delta = en.getValue() - loc[0];
                    if (c == card) {
                        slideMovingTile(delta, 0);
                    } else if (delta != 0) {
                        c.setTranslationX(delta);
                        c.animate().translationX(0).setStartDelay(0).setDuration(slow(340)).setInterpolator(ease).start();
                    }
                }
                return true;
            }
        });
    }

    private void fillInputsRow() {
        inputsRow.removeAllViews();
        Set<String> hidden = hiddenInputs();
        for (TvInputInfo info : tvInputs()) {
            if (!hidden.contains(info.getId())) inputsRow.addView(inputCard(info));
        }
    }

    /**
     * Sur cette TV, une prise HDMI vide se déclare « en veille » : seul « connecté » signifie qu'un appareil répond.
     * La source reste sélectionnable, au cas où un appareil branché ne le signalerait pas.
     */
    private boolean inputConnected(TvInputInfo info) {
        TvInputManager m = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        return m == null || m.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
    }

    /** Touche Source pendant que le panneau est ouvert : on passe à la source suivante, en boucle. */
    private void focusNextInput() {
        View focused = inputsRow.findFocus();
        int next = (inputsRow.indexOfChild(focused) + 1) % inputsRow.getChildCount();
        View card = inputsRow.getChildAt(next);
        if (card.requestFocus()) playNavigationSound(card, View.FOCUS_RIGHT);
    }

    private View inputCard(TvInputInfo info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setSoundEffectsEnabled(navSound);
        CharSequence label = inputLabel(info);
        card.setContentDescription(label);
        boolean connected = inputConnected(info);
        int idleColor = connected ? 0x30FFFFFF : 0x18FFFFFF;
        Squircle bg = new Squircle(px(24), idleColor);
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(new Icons(info.getType() == TvInputInfo.TYPE_TUNER ? Icons.TV : Icons.INPUT, Color.WHITE));
        img.setAlpha(connected ? 1f : 0.4f);
        card.addView(img, new LinearLayout.LayoutParams(px(40), px(40)));
        TextView name = new TextView(this);
        name.setTypeface(fontRegular);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setAlpha(connected ? 1f : 0.4f);
        name.setTextSize(16);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(px(8), px(10), px(8), 0);
        card.addView(name);

        card.setOnClickListener(v -> {
            hidePanels();
            startSafe(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId())));
        });
        card.setTag(info.getId());
        card.setOnLongClickListener(v -> {
            if (movingTile == null) startMove(v);
            return true;
        });
        card.setOnKeyListener(this::onInputCardKey);
        card.setOnFocusChangeListener((v, has) -> {
            if (movingTile != null) return; // pendant un déplacement, le focus saute brièvement d'une carte à l'autre
            animateFocus(v, has, CARD_FOCUS_SCALE);
            bg.setColor(has ? 0xF2FFFFFF : idleColor);
            name.setTextColor(has ? 0xFF16181D : Color.WHITE);
            if (has) img.setColorFilter(0xFF16181D, PorterDuff.Mode.SRC_IN);
            else img.clearColorFilter();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(136), px(112));
        lp.leftMargin = px(9);
        lp.rightMargin = px(9);
        card.setLayoutParams(lp);
        return card;
    }

    // ---------- Réglages ----------

    private FrameLayout buildSettingsOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(PANEL_BG);
        overlay.setVisibility(View.GONE);
        overlay.setClipChildren(false);

        // Le titre fait partie de la liste : il remonte avec elle.
        settingsScroll = new SmoothScroll(this, px(72));
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.setClipChildren(false);
        settingsScroll.setClipToPadding(false);
        settingsScroll.setPadding(0, px(40), 0, 0);
        settingsList = new LinearLayout(this);
        settingsList.setOrientation(LinearLayout.VERTICAL);
        // Largeur en marges plutôt qu'en taille : pendant un fondu, la liste se dessine dans un calque à ses dimensions,
        // qui couperait la ligne agrandie. Le bas est aussi ici, car le défilement ignore la marge du conteneur.
        int side = (getResources().getDisplayMetrics().widthPixels - px(600)) / 2;
        settingsList.setPadding(side, 0, side, px(72));
        settingsList.setClipChildren(false);
        settingsList.setClipToPadding(false);
        settingsScroll.addView(settingsList);
        overlay.addView(settingsScroll, match());
        return overlay;
    }

    private void showSettings() {
        closeAllApps(false);
        inSourcesPage = false;
        View first = buildSettingsRoot(false);
        showPanel(settingsOverlay);
        first.requestFocus();
    }

    /** Remplit la page principale et renvoie la ligne à sélectionner. */
    private View buildSettingsRoot(boolean backFromSources) {
        settingsBuiltWithService = sleepAvailable();
        settingsList.removeAllViews();
        settingsScroll.scrollTo(0, 0);
        TextView title = panelTitle(R.string.settings);
        title.setPadding(0, 0, 0, px(16));
        settingsList.addView(title);

        View google = linkRow(getString(R.string.google_settings), () -> startSafe(new Intent(Settings.ACTION_SETTINGS)));
        settingsList.addView(google);
        settingsList.addView(languageRow());
        settingsList.addView(choiceRow(getString(R.string.wallpaper_change), WALLPAPER_MINUTES, wallpaperMinutes(), this::wallpaperMinutesLabel, minutes -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_WALLPAPER_MINUTES, minutes).apply();
            lastWallpaperChange = System.currentTimeMillis();
        }));
        settingsList.addView(choiceRow(getString(R.string.saver_delay), SAVER_MINUTES,
                getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SAVER_MINUTES, 2), this::wallpaperMinutesLabel, minutes -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SAVER_MINUTES, minutes).apply();
            armSaver();
        }));
        if (sleepAvailable()) settingsList.addView(sleepRow());
        settingsList.addView(toggleRow(getString(R.string.shelf_setting), shelfEnabled, on -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SHELF, on).apply();
            shelfEnabled = on;
            if (on && checkSelfPermission(TV_LISTINGS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{TV_LISTINGS_PERMISSION}, 1);
            }
        }));
        settingsList.addView(toggleRow(getString(R.string.dock_blur), dockBlur, on -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DOCK_BLUR, on).apply();
            setDockBlur(on);
        }));
        settingsList.addView(choiceRow(getString(R.string.tile_smoothing), new int[]{SMOOTH_OFF, SMOOTH_SELECTED, SMOOTH_ALL},
                tileSmoothing, this::tileSmoothingName, mode -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_TILE_SMOOTHING, mode).apply();
            tileSmoothing = mode;
            for (View tile : allTiles()) updateTileSmoothing(tile);
        }));
        settingsList.addView(toggleRow(getString(R.string.nav_sound), navSound, on -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_NAV_SOUND, on).apply();
            navSound = on;
            applySoundSetting(root);
        }));
        View sources = linkRow(getString(R.string.shown_inputs), this::openSourcesPage);
        settingsList.addView(sources);
        if (!sleepAvailable()) {
            settingsList.addView(linkRow(getString(R.string.a11y_row), this::showAccessibilityPopup));
        }
        return backFromSources ? sources : google;
    }

    /** Ligne qui ouvre quelque chose : OK ou la flèche droite, comme pour entrer dans un sous-menu. */
    private View linkRow(CharSequence label, Runnable action) {
        View row = settingsRow(label, settingsChevron());
        row.setOnClickListener(v -> action.run());
        row.setOnKeyListener((v, code, ev) -> {
            if (code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) v.performClick();
            return true;
        });
        return row;
    }

    private String tileSmoothingName(int mode) {
        return getString(mode == SMOOTH_OFF ? R.string.smoothing_off
                : mode == SMOOTH_SELECTED ? R.string.smoothing_selected : R.string.smoothing_all);
    }

    private View languageRow() {
        int[] choices = new int[LANGUAGES.length];
        for (int i = 0; i < choices.length; i++) choices[i] = i;
        String current = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANGUAGE, "");
        return choiceRow(getString(R.string.language), choices, Math.max(0, Arrays.asList(LANGUAGES).indexOf(current)),
                i -> i == 0 ? getString(R.string.language_auto) : LANGUAGE_NAMES[i], true, i -> {
            applyLanguage(LANGUAGES[i]);
            // Après le traitement de la touche : la ligne sélectionnée est remplacée avec les autres.
            handler.post(() -> rebuildSettingsKeepingFocus());
        });
    }

    private void rebuildSettingsKeepingFocus() {
        int focused = settingsList.indexOfChild(settingsList.findFocus());
        buildSettingsRoot(false);
        if (focused >= 0) settingsList.getChildAt(Math.min(focused, settingsList.getChildCount() - 1)).requestFocus();
    }

    // ---------- Recommandations : quand une tuile du dock est sélectionnée, ce que son appli propose apparaît au-dessus ----------

    private FrameLayout buildShelf() {
        FrameLayout shelf = new FrameLayout(this);
        shelf.setVisibility(View.GONE);
        // Mêmes marges et mêmes écarts que les colonnes du dock. La marge de défilement ramène toujours
        // les images sur ces colonnes, et laisse voir le début de la suivante au bord de l'écran.
        int left = (getResources().getDisplayMetrics().widthPixels
                - (GRID_COLUMNS * (px(GRID_TILE_W) + px(GRID_GAP)) - px(GRID_GAP))) / 2;
        HorizontalScrollView scroll = new EdgeScroll(this, left, slow(554));
        shelfScroll = scroll;
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        shelfRow = new LinearLayout(this);
        shelfRow.setClipChildren(false);
        shelfRow.setClipToPadding(false);
        // Fermé tant qu'on ne monte pas dedans : sinon, droite au bout du dock y atterrit, et la sélection repart à gauche.
        shelfRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        int halfGap = px(GRID_GAP / 2f);
        shelfRow.setPadding(left - halfGap, px(10), left - halfGap, px(10));
        scroll.addView(shelfRow);

        shelf.addView(scroll, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        return shelf;
    }

    /** Relance l'affichage pour la tuile sélectionnée. */
    private void refreshShelf() {
        handler.removeCallbacks(shelfShow);
        View focused = dock.findFocus();
        if (focused == null || !shelfAllowed()) {
            hideShelf();
            return;
        }
        if (shelfTile != focused) hideShelf();
        if (keyHeld) handler.postDelayed(shelfShow, SHELF_HELD_KEY_DELAY_MS);
        else showShelfForFocus();
    }

    private boolean shelfAllowed() {
        return shelfEnabled && !allAppsOpen && !panelVisible() && !saverActive && movingTile == null;
    }

    /**
     * Recharge les recommandations si elles datent, et prépare déjà les images des tuiles du dock, pour que le contenu
     * apparaisse sans attente. Renvoie vrai si un chargement démarre : l'affichage reprendra à sa fin.
     */
    private boolean refreshShelfData() {
        if (!shelfEnabled || checkSelfPermission(TV_LISTINGS_PERMISSION) != PackageManager.PERMISSION_GRANTED
                || System.currentTimeMillis() - shelfLoadedAt <= SHELF_REFRESH_MS) {
            return false;
        }
        shelfLoadedAt = System.currentTimeMillis();
        List<String> docked = visibleDock();
        Map<String, Bitmap> known = new HashMap<>(shelfImages);
        int w = shelfCardWidth(), h = shelfCardHeight();
        shelfWorker.execute(() -> {
            Map<String, List<TvPrograms.Program>> programs = TvPrograms.loadAll(this);
            Map<String, Bitmap> images = new HashMap<>();
            for (String pkg : docked) {
                List<TvPrograms.Program> list = programs.get(pkg);
                if (list == null) continue;
                for (TvPrograms.Program p : list) {
                    if (known.containsKey(p.image)) continue;
                    Bitmap bitmap = TvPrograms.loadImage(this, p.image, w, h);
                    if (bitmap != null) images.put(p.image, bitmap);
                }
            }
            handler.post(() -> {
                shelfPrograms = programs;
                shelfImages.putAll(images);
                refreshShelf();
            });
        });
        return true;
    }

    private void showShelfForFocus() {
        View focused = dock.findFocus();
        if (focused == null || !shelfAllowed() || !(focused.getTag() instanceof String)) return;
        if (checkSelfPermission(TV_LISTINGS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            askShelfPermissionOnce();
            return;
        }
        if (refreshShelfData()) return;
        String pkg = (String) focused.getTag();
        List<TvPrograms.Program> programs = shelfPrograms.get(pkg);
        if (programs == null || programs.isEmpty()) {
            hideShelf();
            return;
        }
        if (pkg.equals(shelfPkg) && shelfRoot.getVisibility() == View.VISIBLE) return;
        if (pkg.equals(shelfPreparing)) return;
        shelfPreparing = pkg;
        List<TvPrograms.Program> wanted = new ArrayList<>(programs);
        if (shelfImages.keySet().containsAll(imagesOf(wanted))) {
            shelfPreparing = null;
            displayShelf(pkg, focused, wanted);
            return;
        }
        Map<String, Bitmap> known = new HashMap<>(shelfImages);
        int w = shelfCardWidth(), h = shelfCardHeight();
        // Tout est prêt avant d'apparaître : titre et images arrivent ensemble.
        shelfWorker.execute(() -> {
            long deadline = SystemClock.uptimeMillis() + SHELF_IMAGES_MAX_WAIT_MS;
            Map<String, Bitmap> loaded = new HashMap<>();
            for (TvPrograms.Program p : wanted) {
                if (known.containsKey(p.image) || SystemClock.uptimeMillis() > deadline) continue;
                Bitmap bitmap = TvPrograms.loadImage(this, p.image, w, h);
                if (bitmap != null) loaded.put(p.image, bitmap);
            }
            handler.post(() -> {
                shelfPreparing = null;
                shelfImages.putAll(loaded);
                List<TvPrograms.Program> ready = new ArrayList<>();
                for (TvPrograms.Program p : wanted) if (shelfImages.containsKey(p.image)) ready.add(p);
                if (!ready.isEmpty() && dock.findFocus() == focused && shelfAllowed()) displayShelf(pkg, focused, ready);
            });
        });
    }

    /** Images centrées entre le bas des chiffres de l'heure et le haut du dock. */
    private void placeShelf() {
        int timeBottom = clock.getTop() + timeClock.getTop() + timeClock.getBaseline();
        int top = timeBottom + (dockScroll.getTop() - timeBottom - shelfCardHeight()) / 2;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shelfScroll.getLayoutParams();
        lp.topMargin = top - shelfRow.getPaddingTop();
        shelfScroll.setLayoutParams(lp);
    }

    private List<String> imagesOf(List<TvPrograms.Program> programs) {
        List<String> out = new ArrayList<>();
        for (TvPrograms.Program p : programs) out.add(p.image);
        return out;
    }

    private void displayShelf(String pkg, View tile, List<TvPrograms.Program> programs) {
        shelfPkg = pkg;
        shelfTile = tile;
        fillShelf(programs);
        placeShelf();
        dateClock.animate().alpha(0f).setStartDelay(0).setDuration(slow(160)).start();
        shelfRoot.animate().cancel();
        shelfRoot.setVisibility(View.VISIBLE);
        shelfScroll.animate().cancel();
        if (shelfNoAnimation) {
            shelfRoot.setAlpha(1f);
            shelfScroll.setTranslationX(0);
            shelfArrivalDirection = 0;
            return;
        }
        fadeIn(shelfRoot);
        // En arrivant de la gauche, les images viennent un peu de la droite, et inversement.
        shelfScroll.setTranslationX(shelfArrivalDirection * px(SHELF_ARRIVAL_SLIDE));
        shelfScroll.animate().translationX(0).setDuration(slow(480)).setInterpolator(ease).start();
        shelfArrivalDirection = 0;
    }

    private void askShelfPermissionOnce() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SHELF_ASKED, false)) return;
        prefs.edit().putBoolean(KEY_SHELF_ASKED, true).apply();
        requestPermissions(new String[]{TV_LISTINGS_PERMISSION}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        shelfLoadedAt = 0;
        refreshShelf();
    }

    private void fillShelf(List<TvPrograms.Program> programs) {
        shelfRow.removeAllViews();
        shelfScroll.scrollTo(0, 0);
        for (TvPrograms.Program p : programs) shelfRow.addView(shelfCard(p));
    }

    private int shelfCardWidth() {
        return SHELF_CARD_COLUMNS * px(GRID_TILE_W) + (SHELF_CARD_COLUMNS - 1) * px(GRID_GAP);
    }

    private int shelfCardHeight() {
        return shelfCardWidth() * 9 / 16;
    }

    private View shelfCard(TvPrograms.Program p) {
        Squircle.Frame card = new Squircle.Frame(this, px(24));
        card.setSmoothEdges(tileSmoothing == SMOOTH_ALL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setSoundEffectsEnabled(navSound);
        card.setContentDescription(p.title);
        View fill = new View(this);
        fill.setBackgroundColor(0xFF2A2D35);
        fill.setScaleX(1.03f);
        fill.setScaleY(1.03f);
        card.addView(fill, match());
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, match());
        image.setImageBitmap(shelfImages.get(p.image));
        card.setOnClickListener(v -> openProgram(p));
        card.setOnFocusChangeListener((v, has) -> {
            animateFocus(v, has, SHELF_CARD_FOCUS_SCALE);
            updateTileSmoothing(v);
        });
        card.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) focusShelfTile();
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_UP;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(shelfCardWidth(), shelfCardHeight());
        lp.leftMargin = lp.rightMargin = px(GRID_GAP / 2f);
        card.setLayoutParams(lp);
        return card;
    }

    private void focusShelfTile() {
        if (shelfTile != null && shelfTile.getParent() == dock && shelfTile.requestFocus()) {
            playNavigationSound(shelfTile, View.FOCUS_DOWN);
        } else if (dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
        shelfRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void openProgram(TvPrograms.Program p) {
        hideShelf();
        try {
            Intent intent = Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME);
            intent.setSelector(null);
            startSafe(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideShelf() {
        handler.removeCallbacks(shelfShow);
        if (shelfRoot.getVisibility() != View.VISIBLE) return;
        if (shelfRow.findFocus() != null) focusShelfTile();
        shelfPkg = null;
        shelfTile = null;
        dateClock.animate().alpha(1f).setStartDelay(0).setDuration(slow(200)).start();
        // Les images continuent dans le sens du déplacement : vers la gauche quand on part à droite.
        shelfScroll.animate().cancel();
        shelfScroll.animate().translationX(-shelfArrivalDirection * px(SHELF_ARRIVAL_SLIDE))
                .setDuration(slow(480)).setInterpolator(ease).start();
        shelfRoot.animate().alpha(0f).setDuration(slow(360)).setInterpolator(ease)
                .withEndAction(() -> {
                    if (shelfPkg == null) shelfRoot.setVisibility(View.GONE);
                }).start();
    }

    // ---------- Popup : comment activer le service d'accessibilité ----------

    private void showAccessibilityPopup() {
        if (accessibilityPopup != null) return;
        accessibilityPopupReturnFocus = settingsList.findFocus();
        settingsList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(36), px(32), px(36), px(28));
        card.setBackground(new Squircle(px(32), 0xF724262C));
        TextView title = panelTitle(R.string.a11y_title);
        title.setTextSize(24);
        card.addView(title);
        card.addView(popupText(getString(R.string.a11y_why), px(14), 0xDDFFFFFF));
        card.addView(popupText(getString(R.string.a11y_path), px(14), Color.WHITE));

        TextView ok = new TextView(this);
        ok.setTypeface(fontMedium);
        ok.setText(R.string.ok);
        ok.setTextSize(17);
        ok.setGravity(Gravity.CENTER);
        ok.setTextColor(0xFF16181D);
        ok.setBackground(new Squircle(px(16), 0xF2FFFFFF));
        ok.setFocusable(true);
        ok.setClickable(true);
        ok.setSoundEffectsEnabled(navSound);
        ok.setOnClickListener(v -> hideAccessibilityPopup(true));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(-1, px(50));
        okLp.topMargin = px(24);
        card.addView(ok, okLp);

        overlay.addView(card, new FrameLayout.LayoutParams(px(560), -2, Gravity.CENTER));
        root.addView(overlay, match());
        accessibilityPopup = overlay;

        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(slow(200)).setInterpolator(ease).start();
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(slow(320)).setInterpolator(Spring.LIVELY).start();
        ok.requestFocus();
    }

    private TextView popupText(CharSequence text, int top, int color) {
        TextView t = new TextView(this);
        t.setTypeface(fontRegular);
        t.setText(text);
        t.setTextSize(16);
        t.setLineSpacing(px(3), 1f);
        t.setTextColor(color);
        t.setPadding(0, top, 0, 0);
        return t;
    }

    private void hideAccessibilityPopup(boolean animated) {
        FrameLayout popup = accessibilityPopup;
        if (popup == null) return;
        accessibilityPopup = null;
        settingsList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (accessibilityPopupReturnFocus != null) accessibilityPopupReturnFocus.requestFocus();
        if (!animated) {
            root.removeView(popup);
            return;
        }
        popup.animate().alpha(0f).setDuration(slow(180)).setInterpolator(ease).withEndAction(() -> root.removeView(popup)).start();
        View card = popup.getChildAt(0);
        card.animate().scaleX(0.94f).scaleY(0.94f).setDuration(slow(180)).setInterpolator(ease).start();
    }

    private void openSourcesPage() {
        inSourcesPage = true;
        settingsList.removeAllViews();
        settingsScroll.scrollTo(0, 0);
        settingsList.addView(panelTitle(R.string.shown_inputs));
        TextView hint = new TextView(this);
        hint.setTypeface(fontRegular);
        hint.setText(R.string.shown_inputs_hint);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(14);
        hint.setPadding(0, px(2), 0, px(12));
        settingsList.addView(hint);
        Set<String> hidden = hiddenInputs();
        for (TvInputInfo info : tvInputs()) {
            settingsList.addView(
                    toggleRow(inputLabel(info), !hidden.contains(info.getId()), on -> setInputHidden(info.getId(), !on)));
        }
        if (settingsList.getChildCount() > 2) settingsList.getChildAt(2).requestFocus();
        slideSettingsPage(1);
    }

    private void closeSourcesPage() {
        inSourcesPage = false;
        buildSettingsRoot(true).requestFocus();
        slideSettingsPage(-1);
    }

    private void slideSettingsPage(int direction) {
        settingsList.setAlpha(0f);
        settingsList.setTranslationX(direction * px(60));
        settingsList.animate().alpha(1f).translationX(0).setDuration(slow(260)).setInterpolator(ease).start();
    }

    private TextView settingsChevron() {
        TextView chevron = new TextView(this);
        chevron.setTypeface(fontRegular);
        chevron.setText("›");
        chevron.setTextSize(24);
        return chevron;
    }

    private int wallpaperMinutes() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_WALLPAPER_MINUTES, 1);
    }

    private String wallpaperMinutesLabel(int minutes) {
        if (minutes == 0) return getString(R.string.wallpaper_never);
        if (minutes < 60) return getString(R.string.wallpaper_minutes, minutes);
        int rest = minutes % 60;
        return minutes / 60 + "h" + (rest > 0 ? String.valueOf(rest) : "");
    }

    /** Gauche et droite changent la valeur ; sur la ligne sélectionnée, les chevrons indiquent de quel côté il en reste. */
    private View choiceRow(CharSequence label, int[] choices, int initial, IntFunction<String> names, IntConsumer onChange) {
        return choiceRow(label, choices, initial, names, false, onChange);
    }

    /** Avec loop, on passe du dernier choix au premier et inversement. */
    private View choiceRow(CharSequence label, int[] choices, int initial, IntFunction<String> names, boolean loop,
            IntConsumer onChange) {
        int[] index = {Math.max(0, indexOf(choices, initial))};
        TextView value = new TextView(this);
        value.setTypeface(fontRegular);
        value.setTextSize(17);
        value.setText(names.apply(choices[index[0]]));
        View row = settingsRow(label, value);
        View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, has) -> {
            base.onFocusChange(v, has);
            value.setText(has ? choiceText(choices, index[0], names, loop) : names.apply(choices[index[0]]));
        });
        row.setOnKeyListener((v, code, ev) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
            int next = index[0] + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (loop) next = (next + choices.length) % choices.length;
            else if (next < 0 || next >= choices.length) return true;
            index[0] = next;
            onChange.accept(choices[next]);
            value.setText(choiceText(choices, next, names, loop));
            playNavigationSound(v, code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
            return true;
        });
        return row;
    }

    private boolean sleepAvailable() {
        return HomeKeyService.isRunning();
    }

    /** Au retour de la page Accessibilité d'Android, la ligne reflète le nouvel état du service. */
    private void refreshSettingsAfterAccessibility() {
        if (settingsOverlay.getVisibility() != View.VISIBLE || inSourcesPage) return;
        if (settingsBuiltWithService == sleepAvailable()) return;
        hideAccessibilityPopup(false);
        rebuildSettingsKeepingFocus();
    }

    /** Proposée seulement si le service d'accessibilité tourne, car c'est lui qui compte l'inactivité et éteint la TV. */
    private View sleepRow() {
        int current = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SLEEP_MINUTES, 0);
        return choiceRow(getString(R.string.sleep_timer), SLEEP_MINUTES, current, this::wallpaperMinutesLabel, minutes -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SLEEP_MINUTES, minutes).apply();
            HomeKeyService.sleepDelayChanged();
        });
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }

    private String choiceText(int[] choices, int i, IntFunction<String> names, boolean loop) {
        return (loop || i > 0 ? "‹  " : "") + names.apply(choices[i]) + (loop || i < choices.length - 1 ? "  ›" : "");
    }

    /** Le son du clic se règle vue par vue. */
    private void applySoundSetting(View v) {
        v.setSoundEffectsEnabled(navSound);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applySoundSetting(g.getChildAt(i));
        }
    }

    private View toggleRow(CharSequence label, boolean shown, Consumer<Boolean> onChange) {
        final int on = 0xFF34C759, off = 0xFF8E8E93;
        final int trackW = 58, trackH = 26, thumbW = 35, thumbH = 22, inset = 2;
        FrameLayout track = new FrameLayout(this);
        track.setClipChildren(false);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(px(trackH / 2f));
        trackBg.setColor(shown ? on : off);
        track.setBackground(trackBg);
        View thumb = new View(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setCornerRadius(px(thumbH / 2f));
        thumbBg.setColor(Color.WHITE);
        thumb.setBackground(thumbBg);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(px(thumbW), px(thumbH), Gravity.CENTER_VERTICAL);
        tlp.leftMargin = px(inset);
        track.addView(thumb, tlp);
        track.setLayoutParams(new LinearLayout.LayoutParams(px(trackW), px(trackH)));
        int travel = px(trackW - thumbW - 2 * inset);
        thumb.setTranslationX(shown ? travel : 0);

        boolean[] state = {shown};
        View row = settingsRow(label, track);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            onChange.accept(state[0]);
            thumb.animate().translationX(state[0] ? travel : 0).setStartDelay(0)
                    .setDuration(slow(320)).setInterpolator(Spring.LIVELY).start();
            ValueAnimator color = ValueAnimator.ofObject(new ArgbEvaluator(), state[0] ? off : on, state[0] ? on : off);
            color.setDuration(slow(220));
            color.addUpdateListener(an -> trackBg.setColor((int) an.getAnimatedValue()));
            color.start();
        });
        return row;
    }

    private View settingsRow(CharSequence label, View end) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setSoundEffectsEnabled(navSound);
        row.setContentDescription(label);
        row.setPadding(px(20), 0, px(20), 0);
        Squircle bg = new Squircle(px(16), 0x1FFFFFFF);
        row.setBackground(bg);
        TextView name = new TextView(this);
        name.setTypeface(fontRegular);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(end);
        TextView endText = end instanceof TextView ? (TextView) end : null;
        if (endText != null) endText.setTextColor(Color.WHITE);
        row.setOnFocusChangeListener((v, has) -> {
            animateFocus(v, has, 1.03f);
            bg.setColor(has ? 0xF2FFFFFF : 0x1FFFFFFF);
            name.setTextColor(has ? 0xFF16181D : Color.WHITE);
            if (endText != null) endText.setTextColor(has ? 0xFF16181D : Color.WHITE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(54));
        lp.topMargin = px(5);
        lp.bottomMargin = px(5);
        row.setLayoutParams(lp);
        return row;
    }

    // ---------- Tuiles ----------

    private View appTile(AppEntry e) {
        Squircle.Frame tile = new Squircle.Frame(this, px(18));
        tile.setSmoothEdges(tileSmoothing == SMOOTH_ALL);
        // Fond à l'intérieur du découpage : dessiné dessous, il laisserait un liseré sombre autour des bannières.
        View fill = new View(this);
        if (e.bannerBackdrop != null) fill.setBackground(new BitmapDrawable(getResources(), e.bannerBackdrop));
        else fill.setBackgroundColor(e.isBanner ? 0xFF1E1F24 : 0xFF2A2D35);
        // Il déborde un peu : quand la tuile tourne, c'est lui qu'on voit au ras des bords droits de la bannière, crénelés.
        fill.setScaleX(1.03f);
        fill.setScaleY(1.03f);
        tile.addView(fill, match());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setSoundEffectsEnabled(navSound);
        tile.setContentDescription(e.label);

        ImageView img = new ImageView(this);
        img.setImageDrawable(e.banner);
        if (e.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, match());
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(img, new FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER));
        }

        tile.setOnFocusChangeListener((v, has) -> {
            if (movingTile != null) return; // pendant un déplacement, le focus saute brièvement d'une tuile à l'autre
            animateFocus(v, has, TILE_FOCUS_SCALE);
            if (has) {
                updateTileSmoothing(v);
                if (v.getParent() == dock) {
                    int index = dock.indexOfChild(v);
                    if (lastDockFocusIndex >= 0) shelfArrivalDirection = Integer.signum(index - lastDockFocusIndex);
                    lastDockFocusIndex = index;
                    refreshShelf();
                } else {
                    hideShelf();
                }
            } else {
                handler.postDelayed(() -> updateTileSmoothing(v), slow(110) + Math.round(slow(200) * Spring.SOFT_SLOW_PEAK.stretch));
            }
        });
        return tile;
    }

    /**
     * Au bout du dock, une tuile agrandie se décale vers l'intérieur juste assez pour garder
     * la même distance au bord du dock sur ses trois côtés.
     */
    private float dockEdgeShift(View tile) {
        if (tile.getParent() != dock || dock.getChildCount() < 2) return 0;
        int i = dock.indexOfChild(tile);
        if (i != 0 && i != dock.getChildCount() - 1) return 0;
        // Connu même avant la mise en page.
        float overflowDifference = (px(GRID_TILE_W) - px(GRID_TILE_H)) * (TILE_FOCUS_SCALE - 1) / 2;
        float shift = overflowDifference - (px(DOCK_SIDE_INSET) - px(DOCK_INSET));
        return i == 0 ? shift : -shift;
    }

    private void animateFocus(View v, boolean has, float scale) {
        // Passage rapide : la tuile finit d'abord son élan jusqu'à une bonne partie du zoom, puis retombe.
        float momentum = 1f + (scale - 1f) * FOCUS_MOMENTUM;
        if (!has && v.getScaleX() < momentum) {
            v.animate().scaleX(momentum).scaleY(momentum).setStartDelay(0).setDuration(slow(110))
                    .setInterpolator(MOMENTUM_EASE)
                    .withEndAction(() -> {
                        if (!v.hasFocus()) animateFocus(v, false, scale);
                    }).start();
            return;
        }
        float s = has ? scale : 1f;
        v.animate().scaleX(s).scaleY(s).translationX(has ? dockEdgeShift(v) : 0)
                .setStartDelay(0)
                .setDuration(has ? slow(260) : Math.round(slow(200) * Spring.SOFT_SLOW_PEAK.stretch))
                .setInterpolator(has ? Spring.LIVELY : Spring.SOFT_SLOW_PEAK)
                .start();
    }

    private void fadeIn(View v) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(slow(360)).setInterpolator(ease).start();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int px(float dpValue) {
        return Math.round(dpValue * dp);
    }
}
