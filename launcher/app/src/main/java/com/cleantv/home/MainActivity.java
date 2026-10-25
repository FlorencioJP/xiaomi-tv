package com.cleantv.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int HEADER = 0, TILE = 1, GAP = 2;

    private static final class App {
        final String pkg, label;
        final Intent launch;
        final Drawable banner;
        final boolean isBanner;
        int glow; // banner colour, sampled on first dock focus

        App(String pkg, String label, Intent launch, Drawable banner, boolean isBanner) {
            this.pkg = pkg; this.label = label; this.launch = launch; this.banner = banner; this.isBanner = isBanner;
        }
    }

    private final List<App> apps = new ArrayList<>();
    private int cols, dock, tile, dockShown;
    private String lastPkg;
    private boolean inTopBar;
    private float dp;
    private int bannerH, tileH, headerH, gridTop;
    private Typeface thin, light, regular;

    private RecyclerView list;
    private GridLayoutManager layout;
    private Highlight highlight;
    private View topBar;
    private TextView greeting, dockName;
    private View glow;
    private View settingsButton;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateGreeting(); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        cols = getResources().getInteger(R.integer.columns);

        thin = font(200);
        light = font(300);
        regular = font(400);

        topBar = findViewById(R.id.top_bar);
        greeting = findViewById(R.id.greeting);
        greeting.setTypeface(light);
        ((TextView) findViewById(R.id.clock)).setTypeface(thin);
        ((TextView) findViewById(R.id.date)).setTypeface(light);
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(this::showMenu);

        DisplayMetrics m = getResources().getDisplayMetrics();
        int tileW = (m.widthPixels - px(192)) / cols;
        bannerH = (tileW - px(24)) * 9 / 16;
        tileH = px(22) + bannerH + px(34 + 4);
        headerH = m.heightPixels - tileH - px(56);
        gridTop = px(48);

        list = findViewById(R.id.apps);
        layout = new GridLayoutManager(this, cols) {
            // focus is moved and scrolled by us (focusTile), never by RecyclerView
            @Override public boolean requestChildRectangleOnScreen(@NonNull RecyclerView p, @NonNull View c,
                    @NonNull android.graphics.Rect r, boolean immediate, boolean focusedChildVisible) {
                return false;
            }

            // lay out the next screen of tiles too, so Down can scroll smoothly to them
            @Override protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State s, @NonNull int[] extra) {
                extra[0] = extra[1] = m.heightPixels;
            }
        };
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) { return adapter.getItemViewType(pos) == TILE ? 1 : cols; }
        });
        list.setLayoutManager(layout);
        list.setItemAnimator(null);
        list.setAdapter(adapter);
        highlight = new Highlight();
        list.addItemDecoration(highlight);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { fadeTopBar(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateGreeting();
        loadApps();
        adapter.notifyDataSetChanged();
        list.post(() -> {
            if (inTopBar) settingsButton.requestFocus();
            else focusTile(tile);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        backToDock(); // Home pressed while already home
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        boolean dpad = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (dpad) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) move(key);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) backToDock();
            return true; // Home never closes
        }
        return super.dispatchKeyEvent(e);
    }

    private void move(int key) {
        if (inTopBar) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !apps.isEmpty()) focusTile(tile);
            return;
        }
        if (apps.isEmpty()) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) enterTopBar();
            return;
        }
        int next = Nav.next(tile, dock, apps.size(), cols, key);
        if (next == Nav.TOP_BAR) enterTopBar();
        else if (next != tile) focusTile(next);
    }

    private void backToDock() {
        if (inTopBar && !apps.isEmpty()) focusTile(tile);
        else if (tile >= dock && dock > 0) focusTile(Math.min((tile - dock) % cols, dock - 1));
    }

    private void enterTopBar() {
        inTopBar = true;
        highlight.hide();
        scrollToTop();
        settingsButton.requestFocus();
    }

    private void focusTile(int i) {
        if (apps.isEmpty()) return;
        tile = Math.max(0, Math.min(i, apps.size() - 1));
        inTopBar = false;
        int pos = adapterPos(tile);
        View v = layout.findViewByPosition(pos);
        if (v == null) { // not laid out yet: jump there, then focus
            if (tile < dock) layout.scrollToPositionWithOffset(0, 0);
            else layout.scrollToPositionWithOffset(pos, gridTop);
            list.post(() -> {
                View w = layout.findViewByPosition(pos);
                if (w != null) w.requestFocus();
            });
            return;
        }
        if (tile < dock) scrollToTop();
        else list.smoothScrollBy(0, v.getTop() - gridTop);
        v.requestFocus();
    }

    private void scrollToTop() {
        View header = layout.findViewByPosition(0);
        if (header != null) list.smoothScrollBy(0, header.getTop());
        else list.smoothScrollToPosition(0);
    }

    private void fadeTopBar() {
        View header = layout.findViewByPosition(0);
        float scrolled = header == null ? Float.MAX_VALUE : -header.getTop();
        topBar.setAlpha(Math.max(0f, 1f - scrolled / px(220)));
    }

    // ---- apps ----

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            boolean isBanner = banner != null;
            if (!isBanner) banner = ri.loadIcon(pm);
            Intent launch = new Intent(query).setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            byPkg.put(pkg, new App(pkg, ri.loadLabel(pm).toString(), launch, banner, isBanner));
        }
        apps.clear();
        dock = 0;
        for (String pkg : getResources().getStringArray(R.array.dock_apps)) {
            App a = byPkg.get(pkg);
            if (a != null && dock < cols) {
                byPkg.remove(pkg);
                apps.add(a);
                dock++;
            }
        }
        List<App> rest = new ArrayList<>(byPkg.values());
        Collator collator = Collator.getInstance();
        rest.sort((x, y) -> collator.compare(x.label, y.label));
        apps.addAll(rest);

        tile = 0;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(lastPkg)) tile = i;
        dockShown = Math.min(dockShown, Math.max(0, dock - 1));
    }

    private int adapterPos(int i) { return i < dock ? i + 1 : i + 2; }

    private int tileAt(int pos) { return pos <= dock ? pos - 1 : pos - 2; }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<>() {
        @Override public int getItemCount() { return apps.size() + 2; }

        @Override public int getItemViewType(int pos) {
            return pos == 0 ? HEADER : pos == dock + 1 ? GAP : TILE;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            View v;
            if (type == HEADER) {
                v = inf.inflate(R.layout.item_header, parent, false);
                v.getLayoutParams().height = headerH;
            } else if (type == GAP) {
                v = new View(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)));
            } else {
                v = inf.inflate(R.layout.item_tile, parent, false);
                ImageView banner = v.findViewById(R.id.banner);
                banner.getLayoutParams().height = bannerH;
                banner.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline o) {
                        o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(12));
                    }
                });
                banner.setClipToOutline(true);
                ((TextView) v.findViewById(R.id.label)).setTypeface(regular);
            }
            RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(v) {};
            if (type == TILE) {
                v.setOnClickListener(x -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) open(apps.get(tileAt(pos)).launch);
                });
                v.setOnFocusChangeListener((x, has) -> {
                    applyFocus(x, has, true);
                    int pos = holder.getBindingAdapterPosition();
                    if (!has || pos == RecyclerView.NO_POSITION) return;
                    tile = tileAt(pos);
                    lastPkg = apps.get(tile).pkg;
                    inTopBar = false;
                    highlight.moveTo(pos);
                    if (tile < dock) {
                        dockShown = tile;
                        showDockName();
                    }
                });
            }
            return holder;
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            int type = getItemViewType(pos);
            if (type == HEADER) {
                dockName = v.findViewById(R.id.dock_name);
                glow = v.findViewById(R.id.glow);
                dockName.setTypeface(light);
                showDockName();
            } else if (type == TILE) {
                App a = apps.get(tileAt(pos));
                ImageView banner = v.findViewById(R.id.banner);
                banner.setImageDrawable(a.banner);
                banner.setScaleType(a.isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
                banner.setBackgroundColor(a.isBanner ? Color.TRANSPARENT : 0xFF1C1C1E);
                ((TextView) v.findViewById(R.id.label)).setText(a.label);
                applyFocus(v, v.hasFocus(), false);
            }
        }
    };

    private void applyFocus(View v, boolean focused, boolean animate) {
        View banner = v.findViewById(R.id.banner);
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(banner.getTop() + banner.getHeight() / 2f);
        v.setTranslationZ(focused ? 1 : 0);
        float s = focused ? 1.1f : 1f;
        if (animate) {
            v.animate().scaleX(s).scaleY(s).setDuration(180).setUpdateListener(a -> list.invalidate()).start();
        } else {
            v.setScaleX(s);
            v.setScaleY(s);
        }
        v.findViewById(R.id.label).setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
    }

    private void showDockName() {
        if (dockName == null || dock == 0 || dockShown >= apps.size()) return;
        App a = apps.get(dockShown);
        int color = glowColor(a);
        dockName.setText(a.label);
        dockName.setShadowLayer(px(24), 0, 0, color);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(px(480));
        g.setColors(new int[]{(color & 0xFFFFFF) | 0x55000000, color & 0xFFFFFF});
        glow.setBackground(g);
        dockName.setAlpha(0f);
        dockName.animate().alpha(1f).setDuration(220).start();
    }

    /** Average of the banner's visible pixels, pushed bright and saturated enough to glow. */
    private static int glowColor(App a) {
        if (a.glow != 0) return a.glow;
        Drawable.ConstantState cs = a.banner.getConstantState();
        Drawable d = cs != null ? cs.newDrawable().mutate() : a.banner;
        Bitmap bm = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 32, 18);
        d.draw(new Canvas(bm));
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 32; x++) {
                int p = bm.getPixel(x, y);
                int max = Math.max(Color.red(p), Math.max(Color.green(p), Color.blue(p)));
                if (Color.alpha(p) < 128 || max < 40) continue;
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
            }
        }
        bm.recycle();
        if (n == 0) return a.glow = 0xFF8E9BFF;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
        hsv[1] = Math.max(hsv[1], 0.45f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        return a.glow = Color.HSVToColor(hsv);
    }

    // ---- top bar ----

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? R.string.morning : h >= 12 && h < 18 ? R.string.afternoon : R.string.evening);
    }

    @SuppressWarnings("deprecation") // getConnectionInfo is the only SSID source below API 31
    private String networkName() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        if (nc == null) return getString(R.string.offline);
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        String ssid = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo().getSSID();
        if (ssid != null && ssid.length() > 2 && ssid.startsWith("\"")) return ssid.substring(1, ssid.length() - 1);
        return "Wi-Fi";
    }

    /** Glass dropdown under the settings button: Inputs, Network, Settings. */
    private void showMenu(View anchor) {
        LinearLayout box = newMenu();
        PopupWindow popup = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        addRow(box, popup, getString(R.string.inputs) + "  ›", () -> showInputs(anchor));
        addRow(box, popup, networkName(), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addRow(box, popup, getString(R.string.settings), () -> open(new Intent(Settings.ACTION_SETTINGS)));
        showUnder(popup, box, anchor);
    }

    /** HDMI ports, AV and Live TV from the TV input framework. */
    private void showInputs(View anchor) {
        LinearLayout box = newMenu();
        PopupWindow popup = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TvInputManager tim = getSystemService(TvInputManager.class);
        boolean liveTv = false;
        // HDMI 1, HDMI 2, ... then AV
        Map<String, Intent> ports = new java.util.TreeMap<>((a, b) -> a.startsWith("HDMI") != b.startsWith("HDMI")
                ? (a.startsWith("HDMI") ? -1 : 1) : a.compareTo(b));
        for (TvInputInfo info : tim == null ? new ArrayList<TvInputInfo>() : tim.getTvInputList()) {
            if (info.getParentId() != null) continue; // HDMI-CEC devices hang off their port
            int type = info.getType();
            if (type == TvInputInfo.TYPE_TUNER) {
                liveTv = true;
            } else if (type == TvInputInfo.TYPE_HDMI || type == TvInputInfo.TYPE_COMPOSITE
                    || type == TvInputInfo.TYPE_COMPONENT || type == TvInputInfo.TYPE_SCART || type == TvInputInfo.TYPE_SVIDEO) {
                CharSequence custom = info.loadCustomLabel(this);
                String label = custom != null ? custom.toString()
                        : type == TvInputInfo.TYPE_HDMI ? info.loadLabel(this).toString() : getString(R.string.av);
                ports.put(label, new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId())));
            }
        }
        for (Map.Entry<String, Intent> port : ports.entrySet()) {
            addRow(box, popup, port.getKey(), () -> watch(port.getValue()));
        }
        if (liveTv) {
            addRow(box, popup, getString(R.string.live_tv),
                    () -> watch(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)));
        }
        showUnder(popup, box, anchor);
    }

    private void watch(Intent intent) {
        // several TV-app activities claim channel URIs; pick the first instead of showing a chooser
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!handlers.isEmpty()) {
            intent.setClassName(handlers.get(0).activityInfo.packageName, handlers.get(0).activityInfo.name);
        }
        open(intent);
    }

    private LinearLayout newMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(10), px(10), px(10), px(10));
        box.setBackgroundResource(R.drawable.glass);
        return box;
    }

    private void addRow(LinearLayout box, PopupWindow popup, String label, Runnable action) {
        TextView row = new TextView(this, null, 0, R.style.Pill);
        row.setText(label);
        row.setTypeface(regular);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(300), px(52));
        lp.topMargin = box.getChildCount() == 0 ? 0 : px(6);
        row.setBackgroundResource(R.drawable.pill);
        box.addView(row, lp);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
    }

    private void showUnder(PopupWindow popup, LinearLayout box, View anchor) {
        popup.showAsDropDown(anchor, 0, px(12), android.view.Gravity.END); // right-aligned with the button
        if (box.getChildCount() > 0) box.getChildAt(0).requestFocus();
    }

    // ---- glass highlight and dock band ----

    /** One translucent highlight that springs from tile to tile, drawn over the list; the dock band under it. */
    private final class Highlight extends RecyclerView.ItemDecoration {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF from = new RectF(), drawn = new RectF(), target = new RectF(), bandRect = new RectF();
        private int targetPos = -1;
        private float t = 1f;
        private boolean visible;
        private ValueAnimator anim;

        Highlight() {
            fill.setColor(0x1FFFFFFF);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(px(2));
            stroke.setColor(0x80FFFFFF);
            band.setColor(0xFF161618);
        }

        void moveTo(int pos) {
            if (visible && !drawn.isEmpty()) from.set(drawn);
            else from.setEmpty();
            targetPos = pos;
            visible = true;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(420);
            anim.setInterpolator(new OvershootInterpolator(1.1f));
            anim.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                list.invalidate();
            });
            anim.start();
        }

        void hide() {
            visible = false;
            drawn.setEmpty();
            list.invalidate();
        }

        @Override public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            if (dock == 0) return;
            View ref = layout.findViewByPosition(1);
            if (ref == null) return;
            bandRect.set(parent.getPaddingLeft() - px(28), ref.getTop() + px(6),
                    parent.getWidth() - parent.getPaddingRight() + px(28), ref.getBottom() + px(14));
            c.drawRoundRect(bandRect, px(32), px(32), band);
        }

        @Override public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            if (!visible) return;
            View v = layout.findViewByPosition(targetPos);
            if (v == null) return;
            View b = v.findViewById(R.id.banner);
            float sc = v.getScaleX(), px = v.getLeft() + v.getPivotX(), py = v.getTop() + v.getPivotY();
            target.set(px + (v.getLeft() + b.getLeft() - px) * sc, py + (v.getTop() + b.getTop() - py) * sc,
                    px + (v.getLeft() + b.getRight() - px) * sc, py + (v.getTop() + b.getBottom() - py) * sc);
            target.inset(-px(5), -px(5));
            if (from.isEmpty()) {
                drawn.set(target);
            } else {
                drawn.set(from.left + (target.left - from.left) * t, from.top + (target.top - from.top) * t,
                        from.right + (target.right - from.right) * t, from.bottom + (target.bottom - from.bottom) * t);
            }
            c.drawRoundRect(drawn, px(16), px(16), fill);
            c.drawRoundRect(drawn, px(16), px(16), stroke);
        }
    }

    private Typeface font(int weight) {
        return new Typeface.Builder(getAssets(), "outfit.ttf").setFontVariationSettings("'wght' " + weight).build();
    }

    private int px(float dips) { return Math.round(dips * dp); }
}
