package com.pomelo.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Google TV garde son launcher prioritaire pour HOME ; ce service intercepte la touche Home
 * de la télécommande et ouvre Pomelo TV à la place. Si Pomelo TV ne peut pas s'ouvrir,
 * la touche est rendue au système (accueil Google TV).
 */
public class HomeKeyService extends AccessibilityService {

    private static final long BOOT_WINDOW_MS = 4 * 60_000;
    private static final long SLEEP_WARNING_MS = 60_000;

    /**
     * L'inactivité se compte ici : ce service voit les touches de la télécommande dans toutes les applis et sur toutes
     * les entrées. Une lecture vidéo sans aucune touche compte donc comme de l'inactivité.
     */
    private static HomeKeyService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Le compte repart de zéro au rallumage et s'arrête quand la TV est éteinte. */
    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) restartIdleCount();
            else handler.removeCallbacksAndMessages(null);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(HomeActivity.withLanguage(base));
    }

    /** Le message d'avertissement suit la langue choisie dans les réglages. */
    static void languageChanged(Configuration config) {
        if (instance != null) {
            instance.getResources().updateConfiguration(config, instance.getResources().getDisplayMetrics());
        }
    }

    static boolean isRunning() {
        return instance != null;
    }

    /** À appeler quand le délai change dans les réglages. */
    static void sleepDelayChanged() {
        if (instance != null) instance.restartIdleCount();
    }

    private void restartIdleCount() {
        handler.removeCallbacksAndMessages(null);
        int minutes = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE).getInt(HomeActivity.KEY_SLEEP_MINUTES, 0);
        if (minutes <= 0) return;
        long delay = minutes * 60_000L;
        handler.postDelayed(() -> Toast.makeText(this, R.string.sleep_warning, Toast.LENGTH_LONG).show(),
                delay - SLEEP_WARNING_MS);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN), delay);
    }

    /** Juste après le démarrage, le service se connecte après l'accueil Google TV : on affiche Pomelo TV. */
    @Override
    protected void onServiceConnected() {
        instance = this;
        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screen.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenState, screen);
        restartIdleCount();
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openHome();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) restartIdleCount();
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_TV_INPUT) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) return true;
        if (event.isCanceled()) return true;
        return code == KeyEvent.KEYCODE_HOME ? openHome() : openInputs();
    }

    /** La touche Source ouvre le panneau de Pomelo TV plutôt que celui de la TV. */
    private boolean openInputs() {
        try {
            startActivity(new Intent(this, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_SHOW_INPUTS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean openHome() {
        try {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        instance = null;
        unregisterReceiver(screenState);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
