package com.cleantv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the remote's Home button (and, if power_on_home is set, power-on) to this launcher.
 * Needed on Google TV builds where the stock launcher can't be disabled: there the setup
 * wizard re-enables itself and takes Home back.
 * Side effect: while it filters keys, Android may stop repeating held buttons (volume ramp).
 */
public class HomeKeyService extends AccessibilityService {
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { goHome(); }
    };
    private boolean powerOnHome;

    @Override
    protected void onServiceConnected() {
        powerOnHome = getResources().getBoolean(R.bool.power_on_home);
        if (powerOnHome) {
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
            goHome(); // connected at boot
        }
    }

    @Override
    public void onDestroy() {
        if (powerOnHome) unregisterReceiver(screenOn);
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) goHome();
        return true; // swallow both down and up so the stock Home never sees it
    }

    private void goHome() {
        Intent home = new Intent(this, MainActivity.class);
        ComponentName other = ComponentName.unflattenFromString(getString(R.string.home_component));
        if (other != null) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(other);
            if (i.resolveActivity(getPackageManager()) != null) home = i;
        }
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}
}
