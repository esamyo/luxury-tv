package com.esamyo.luxurytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ChangedPackages;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;

/**
 * Luxury TV's home: the header (clock, greeting with the weather, date, Wi-Fi and Settings), the main row of app
 * logos and the rows below it, and one soft glow that springs between the logos.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    /** Tiles per row, and the main row's length. */
    static final int COLUMNS = 4;
    private boolean rtlAppGrid;
    private static final long MINUTE_MS = 60_000L;
    /**
     * 2025-01-01. TVs without a clock battery boot at the epoch and keep it until network time arrives; the clock,
     * date and greeting stay hidden before then.
     */
    private static final long TIME_VALID_AFTER_MS = 1_735_689_600_000L;
    /** Back on Home opens ClockActivity (config.xml, back_opens_clock). */
    private boolean backOpensClock;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private WallpaperPlayer wallpaper;
    private GlassView glass;
    private TextView greeting;
    private View clockBlock;
    private TextView btnWifi;
    /** The network icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private int wifiIcon;
    /** The Wi-Fi icon has been filled in once; later resumes refresh it after the first frame. */
    private boolean pillsShown;
    private int greetingColor;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glow. springY works without it, in grid content space, so a
    // scroll moves the glow in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private int glassCardW;   // width of the tile the glow was last fitted to
    /**
     * The focused tile's rect in root coordinates as {@link #onTileFocused} last fitted the glow to it. The size
     * alone cannot say whether the glow is on its tile: a cold start can run onTileFocused once the tile has its
     * final width but before the grid has its top padding, which leaves the right size at the old Y.
     */
    private final Rect glassCardRect = new Rect();
    private boolean glassShown;
    private final Rect tileRect = new Rect();
    private final Rect revealRect = new Rect();
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Between onStop and onStart: Home was fully covered by an app. */
    private boolean stopped;
    /** Between onStart and onStop: Home is (at least partly) on screen. */
    private boolean started;
    /** Rebuild the tiles on the way back to Home only when apps changed meanwhile (TileRefresh). */
    private final TileRefresh tileRefresh = new TileRefresh();
    /** PackageManager's change counter at the last rebuild, to catch a package broadcast that never arrived. */
    private int packageSequence;
    /** An app is picked up (hold OK): the arrows move it, OK puts it down. */
    private boolean reordering;
    /** The hint shown in the middle of Home while an app is picked up, or null. */
    private View moveHint;
    /** Key of the tile being carried, or null. */
    private String movingKey;
    /** Tile order when the move started, to save only when something moved. */
    private List<String> orderAtStart;
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private List<String> dockApps;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network icon when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            // In the background this only marks the tiles stale; onResume rebuilds them once.
            if (!tileRefresh.onPackagesChanged()) return;
            reloadApps(false);
        }
    };
    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClockVisibility();
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        ClockFormat.apply(getWindow().getDecorView(), cfg.clockFormat);
        backOpensClock = cfg.backOpensClock;

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        // root fills the window with an opaque colour, so the window's own black under it is a second full-screen
        // fill on every frame for nothing. The starting window still uses the theme's background.
        getWindow().setBackgroundDrawable(null);
        wallpaper = new WallpaperPlayer(this, root, cfg);
        glass = findViewById(R.id.glass);
        greeting = findViewById(R.id.greeting);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);   // Arabic runs large: 14/13 sit within the clock's height
        greeting.setAllCaps(false);
        // Room under the line for Arabic letters that dip below it: without it the bowl of the ح in صباح is cut off
        // and what is left reads as a ج. Same for the date.
        greeting.setIncludeFontPadding(true);
        if (Build.VERSION.SDK_INT >= 28) greeting.setFallbackLineSpacing(true);
        greeting.setPadding(greeting.getPaddingLeft(), greeting.getPaddingTop(), greeting.getPaddingRight(),
                Math.round(5 * density));
        TextView dateView = findViewById(R.id.date);
        dateView.setIncludeFontPadding(true);
        if (Build.VERSION.SDK_INT >= 28) dateView.setFallbackLineSpacing(true);
        dateView.setPadding(dateView.getPaddingLeft(), dateView.getPaddingTop(), dateView.getPaddingRight(),
                Math.round(5 * density));
        ((TextView) findViewById(R.id.clock)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 41f);
        findViewById(R.id.ampm).setVisibility(View.GONE);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        clockBlock = findViewById(R.id.leftBlock);
        greetingColor = greeting.getCurrentTextColor();
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            // Center Hebrew greetings over the numeric time, independent of a visible AM/PM label.
            View clock = findViewById(R.id.clock);
            View clockRow = (View) clock.getParent();
            View.OnLayoutChangeListener centerGreeting = (v, l, t, right, bottom, ol, ot, or, ob) -> {
                ViewGroup.LayoutParams params = greeting.getLayoutParams();
                if (params.width != clock.getWidth()) {
                    params.width = clock.getWidth();
                    greeting.setLayoutParams(params);
                }
                greeting.setTranslationX(clockRow.getLeft() + clock.getLeft());
            };
            clock.addOnLayoutChangeListener(centerGreeting);
            clockRow.addOnLayoutChangeListener(centerGreeting);
        }
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        rtlAppGrid = getResources().getBoolean(R.bool.rtl_app_grid);
        // Mirror only the app grid; the header and other locales keep their own layouts.
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS, RecyclerView.VERTICAL, false) {
            @Override
            protected boolean isLayoutRTL() {
                return rtlAppGrid;
            }

            /**
             * Every "bring this tile on screen" scroll lands here, both RecyclerView's own on focus and
             * onTileFocused's. The grid's top padding only pushes the main row down the screen: rows below scroll
             * up into it. Tiles below the main row scroll just enough to show the rect between the grid's top edge
             * and its bottom; main-row tiles are left to onTileFocused, which parks the row on the padding line.
             */
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                if (getPosition(child) < COLUMNS) return false;
                int top = child.getTop() + rect.top;
                int bottom = top + rect.height();
                // A row already fully on screen stays put; a row that has to scroll in stops at the padding line.
                int limitBottom = getHeight() - getPaddingBottom();
                int dy;
                if (top < 0) dy = top;
                else if (bottom <= getHeight()) dy = 0;
                else dy = Math.min(top, bottom - limitBottom);
                if (dy == 0) return false;
                if (immediate) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        // Tiles that scroll out while one is carried come back from the view cache without a rebind; set their
        // dimming again whenever one attaches, so none stays dim after the drop.
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View v) {
                v.setAlpha(tileAlpha(grid.getChildAdapterPosition(v)));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View v) {
            }
        });
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        dockApps = cfg.dockApps;
        Order savedOrder = Order.load(Order.file(this));
        adapter.setSavedOrder(Order.ranks(savedOrder, dockApps));
        grid.setAdapter(adapter);

        // Push the main row towards the bottom of the screen; the rows below stay out of sight until Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int tileW = AppAdapter.tileWidthPx(inner, COLUMNS, density);
            int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), density);
            int dockRow = itemH + (int) (AppAdapter.DOCK_EXTRA_DP * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (6 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
            // Cold start: the first focus can run before the grid has its final width or padding, leaving the glow
            // at the XML design size until a key is pressed.
            grid.post(this::refitGlass);
        });

        // Keep the glow glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        // Soft springs on the glow's position, swinging a little past the target before settling.
        springX = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentX") {
            @Override
            public float getValue(View v) {
                return v.getTranslationX();
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationX(value);
            }
        });
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
        btnWifi.setOnLongClickListener(v -> {
            open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        makeIconOnly(btnWifi);
        makeIconOnly((TextView) btnSettings);

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        boolean rtlMenuBar = getResources().getBoolean(R.bool.rtl_menu_bar);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the main-row tile Up came from, not wherever the focus finder lands.
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            int leftButton = rtlMenuBar ? R.id.btnSettings : R.id.btnWifi;
            int rightButton = rtlMenuBar ? R.id.btnWifi : R.id.btnSettings;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == leftButton;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == rightButton;
        };
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glow when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        // Registered for the activity's whole life, not only while it is started: an app installed or updated while
        // another app is in front must still mark the tiles stale, or Home would have to rebuild on every return.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        grid.scrollToPosition(0);
        lastFocused = 0;
        lastFocusedKey = adapter.getItemCount() > 0 ? adapter.keyAt(0) : null;
        if (stopped) {
            tileRefresh.onHomeAgain();
        } else {
            // Still on screen: the tiles are current, so only go back to the first one; rebuilding would replay
            // the entrance animation for nothing.
            grid.post(() -> focusTile(0));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
        started = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timeReceiver, timeFilter);
        }
        updateClockVisibility();
        tileRefresh.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AirportTemp.init(this);
        AirportTemp.start(this::updateGreeting);
        if (grid.getAdapter() == null) grid.setAdapter(adapter);   // dropped by onTrimMemory
        switch (tileRefresh.onResume(packagesChangedSinceRebuild())) {
            case REBUILD:
                reloadApps(true);
                break;
            case REFOCUS:
                // The same tiles: only the focus goes back, as after a rebuild, without replaying the entrance.
                reloadApps(true, false, false);
                break;
            default:
                break;
        }
        updateGreeting();
        // The network icon keeps its state while Home is in the background and the callback updates it, so this
        // refresh only catches anything missed; after the first resume it runs once the first frame is drawn.
        if (pillsShown) {
            refreshPillsAfterNextDraw();
        } else {
            updateWifiLabel();
            pillsShown = true;
        }
        wallpaper.start();
        handler.postDelayed(minuteTick, MINUTE_MS);
    }

    /** Rebuilds the tiles, then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        reloadApps(animate, true, true);
    }

    /**
     * @param rebuild  false keeps the tiles as they are and only puts focus back (TileRefresh.Action.REFOCUS).
     * @param entrance false puts focus back as an animated reload would, without replaying the tiles' entrance.
     */
    private void reloadApps(boolean animate, boolean rebuild, boolean entrance) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        if (restoreFocus) {
            glassShown = false;
            glass.setAlpha(0f);
        }
        if (rebuild) reloadTiles();
        if (animate && entrance) {
            grid.scheduleLayoutAnimation();
            // A rebuild redraws the grid anyway; without one the entrance needs a draw to start.
            if (!rebuild) grid.invalidate();
        }
        if (restoreFocus) {
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.appCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
        }
    }

    /** True when PackageManager reports apps changed since the last rebuild (one binder call). */
    private boolean packagesChangedSinceRebuild() {
        ChangedPackages changed = getPackageManager().getChangedPackages(packageSequence);
        return changed != null && !changed.getPackageNames().isEmpty();
    }

    private int currentPackageSequence(int since) {
        ChangedPackages changed = getPackageManager().getChangedPackages(since);
        return changed != null ? changed.getSequenceNumber() : since;
    }

    private void reloadTiles() {
        if (reordering) endReorder();   // an app installed or removed while moving: keep the order so far
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        packageSequence = currentPackageSequence(packageSequence);
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        AirportTemp.stop();
        cancelHoldOk();
        endReorder();   // Home or another app while moving keeps the order so far
        wallpaper.stop();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        unregisterReceiver(timeReceiver);
        handler.removeCallbacks(clockCheck);
        stopped = true;
        tileRefresh.onStop();
    }

    /**
     * Another app needs the memory while Home is in the background: let go of every tile's artwork, and
     * rebuild it when Home comes back. Not at UI_HIDDEN, the ordinary trip to an app, where keeping the
     * tiles is what makes the return instant.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (!MemoryTrimPolicy.shouldRelease(level, started)) return;
        adapter.clear();
        // Detach the adapter too: with Home stopped no layout pass runs, so the tile views (and the bitmaps
        // they hold) would stay attached until Home comes back. setAdapter(null) recycles them now.
        grid.setAdapter(null);
        grid.getRecycledViewPool().clear();
        wallpaper.releaseForMemoryPressure();
        tileRefresh.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) cancelHoldOk();   // a dialog or another window took the remote while OK was held
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelHoldOk();
        unregisterReceiver(packageReceiver);
        wallpaper.close();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    // ---- moving an app -------------------------------------------------------------------------------

    /**
     * Every key comes here first. Holding OK (or the remote's Menu key) on a tile picks its app up; while it is
     * carried the arrows move it and OK or Back puts it down, which saves the order.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (holdOk(event)) return true;
        if (!reordering && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            int position = focusedTilePosition();
            if (position >= 0 && position < adapter.appCount()) {
                if (menuReleased(event.getAction(), event.getFlags())) {
                    onTileLongPress(position, getCurrentFocus());
                }
                return true;
            }
        }
        if (!reordering || movingKey == null) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) moveBy(code);
                return true;
            default:
                if (ok || back) {
                    if (!down) endReorder();   // one OK places it and finishes
                    return true;
                }
                return super.dispatchKeyEvent(event);
        }
    }

    /**
     * The flags on the KEYCODE_MENU pair that Xiaomi's China firmware injects for a short press of the remote's Menu
     * key, after keeping the real press for itself. They include FLAG_CANCELED, so the pair reads as a cancelled
     * press, and the undocumented 0x1000, 0x2000 and 0x4000, which no real cancelled press carries.
     */
    static final int XIAOMI_SHORT_MENU_FLAGS = 0x7531;

    /** True for the UP that ends a Menu press: not cancelled, or cancelled only as Xiaomi's injected short press is. */
    static boolean menuReleased(int action, int flags) {
        if (action != KeyEvent.ACTION_UP) return false;
        return (flags & KeyEvent.FLAG_CANCELED) == 0
                || (flags & XIAOMI_SHORT_MENU_FLAGS) == XIAOMI_SHORT_MENU_FLAGS;
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    private int focusedTilePosition() {
        View f = getCurrentFocus();
        if (f == null || f.getId() != R.id.card || !(f.getParent() instanceof View)) return -1;
        return grid.getChildAdapterPosition((View) f.getParent());
    }

    /** Hold OK on a tile: picks the app up to move it, anywhere on Home, and shows the move hint. */
    @Override
    public void onTileLongPress(int position, View card) {
        if (reordering || adapter.appCount() < 2) return;
        String key = adapter.keyAt(position);
        if (key == null) return;
        reordering = true;
        orderAtStart = adapter.keys();
        focusTile(position);
        movingKey = key;
        dimOthers();
        showMoveHint();
    }

    /** While a tile is carried, every other tile is dimmed so the carried one stands out; otherwise all are full. */
    private void dimOthers() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            item.setAlpha(tileAlpha(grid.getChildAdapterPosition(item)));
        }
    }

    /** A tile's alpha: dim unless it is the carried one. */
    private float tileAlpha(int pos) {
        if (movingKey != null) {
            return pos != RecyclerView.NO_POSITION && movingKey.equals(adapter.keyAt(pos)) ? 1f : 0.45f;
        }
        return 1f;
    }

    /** Puts the carried app down and saves the order if it changed. */
    private void endReorder() {
        hideMoveHint();
        if (!reordering) return;
        movingKey = null;
        dimOthers();
        reordering = false;
        List<String> keys = adapter.keys();
        List<String> before = orderAtStart;
        orderAtStart = null;
        if (!keys.equals(before)) {
            Order order = new Order(Order.dockKey(dockApps), keys, -1);
            if (Order.save(Order.file(this), order)) {
                adapter.setSavedOrder(Order.ranks(order, dockApps));
            } else {
                Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Moves the carried tile one step. Left and Right wrap across rows, like a list; Up and Down move a whole row,
     * and Down into a short last row lands on its last tile. Nothing moves past either end or up into the top bar.
     */
    private void moveBy(int keyCode) {
        int from = adapter.positionOf(movingKey);
        int n = adapter.appCount();
        if (from < 0 || from >= n) {
            endReorder();
            return;
        }
        int to;
        // rtl_app_grid (Hebrew) mirrors the grid: position 0 is on the right, so Left moves towards the end.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) to = rtlAppGrid ? from + 1 : from - 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) to = rtlAppGrid ? from - 1 : from + 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) to = from - COLUMNS;
        else if (from + COLUMNS < n) to = from + COLUMNS;
        else to = from / COLUMNS < (n - 1) / COLUMNS ? n - 1 : from;
        if (to < 0 || to >= n || to == from) return;
        keepScroll(() -> adapter.move(from, to));
        refocusAfterLayout(to);
    }

    /**
     * Applies an adapter change without the grid jumping. After a move the layout manager anchors on the focused
     * tile, keeping it at its old place on screen and shifting every other row; pinning the first visible row at its
     * current offset as the next layout's anchor keeps the scroll where it was. onTileFocused still scrolls
     * afterwards if the moved tile itself ends up off screen.
     */
    private void keepScroll(Runnable change) {
        GridLayoutManager lm = (GridLayoutManager) grid.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        View firstView = first != RecyclerView.NO_POSITION ? lm.findViewByPosition(first) : null;
        int offset = firstView == null ? 0 : lm.getDecoratedTop(firstView) - grid.getPaddingTop();
        change.run();
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset);
    }

    /**
     * Once the grid has applied the change, put the glass (and focus, if the move dropped it) on the tile at pos.
     * The grid has a fixed size, so it applies adapter changes on the next animation frame rather than in a layout
     * pass; until then it cannot find the tile by position. Wait frame by frame (at most 10) for that. No focus
     * change fires for a moved tile, so focusTile's already-focused path calls onTileFocused itself.
     */
    private void refocusAfterLayout(int pos) {
        grid.postOnAnimation(new Runnable() {
            int frames;

            @Override
            public void run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested()) && ++frames < 10) {
                    grid.postOnAnimation(this);
                    return;
                }
                focusTile(pos);
                dimOthers();   // views that came into view or were rebound during the move
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Home screen: Back goes nowhere, unless back_opens_clock is on.
        if (backOpensClock) startActivity(new Intent(this, ClockActivity.class));
    }

    // ---- focus glow ------------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glow
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.getWidth() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;
        float scale = AppAdapter.FOCUS_SCALE;
        float w = card.getWidth() * scale + 2 * pad;
        float h = card.getHeight() * scale + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h || card.getWidth() != glassCardW) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
        }
        glassCardW = card.getWidth();
        glassCardRect.set(r);
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y - glassScrollShift);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Main row: park it at the bottom of the screen. A 1px mismatch used to start a scroll on every move;
            // only scroll when the row is actually off.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > (int) (8 * density)) grid.smoothScrollBy(0, dy);
        } else {
            // Scroll just enough so the whole glow (not only the tile) is visible.
            revealRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, revealRect, false);
        }
    }

    /** Refits the glow to the focused holder once the grid layout and tile dimensions settle. */
    private void refitGlass() {
        if (isDestroyed() || adapter.appCount() == 0) return;
        int pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() <= 0 || !card.isFocused()) return;
        float pad = 6f * density;
        float scale = AppAdapter.FOCUS_SCALE;
        int w = (int) (card.getWidth() * scale + 2 * pad);
        int h = (int) (card.getHeight() * scale + 2 * pad);
        // The tile's rect, not only its width: the grid's top padding lands after the tile has its width, so a
        // cold start can leave the glow at the right size and the wrong place. Comparing the rect catches that.
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        if (!glassShown || card.getWidth() != glassCardW || glass.getWidth() != w || glass.getHeight() != h
                || !glassCardRect.equals(r)) {
            onTileFocused(pos, card);
        }
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().cancel();
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.appCount();
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int rowCount = position < COLUMNS ? Math.min(count, COLUMNS) : count;
            return GridNavigation.atHorizontalEdge(position, rowCount, COLUMNS,
                    rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
        }
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // main row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        // The default focus search clicks for Left/Right; Up/Down are handled here, so click for them too.
        grid.playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP
                ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
        return true;
    }

    /** A pending "focus once the scroll settles", so a newer focus request can cancel it. */
    private RecyclerView.OnScrollListener pendingFocusScroll;

    private void cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll);
            pendingFocusScroll = null;
            grid.stopScroll();
        }
    }

    private void focusTile(int position) {
        cancelPendingFocusScroll();
        if (adapter.appCount() == 0) {
            btnWifi.requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.appCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glow by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(pos);
    }

    // ---- header ----------------------------------------------------------------------------------

    /** Some TVs set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private final Runnable clockCheck = this::updateClockVisibility;

    private void updateClockVisibility() {
        boolean valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS;
        clockBlock.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);
        handler.removeCallbacks(clockCheck);
        if (valid) {
            updateGreeting();
        } else {
            handler.postDelayed(clockCheck, 1_000L);
        }
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        // The way the TV's language greets at this hour (Greeting), shared with the clock screensaver.
        int res = Greeting.res(getResources().getConfiguration().getLocales().get(0), hour);
        greeting.setText(getString(res));
        greeting.append(AirportTemp.suffix(greeting));
        greeting.setTextColor(greetingColor);
    }

    private void refreshPillsAfterNextDraw() {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                // Posted from inside the frame's traversal, so it runs after that frame is drawn.
                handler.post(() -> {
                    if (!isDestroyed()) updateWifiLabel();
                });
                return true;
            }
        });
        root.invalidate();   // a resume that changes nothing on screen still draws once, so the refresh runs
    }

    /** The network icon: Wi-Fi, or Ethernet when the TV is wired; the state is read out by TalkBack. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean online = false, ethernet = false;
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
            }
            if (!online) {
                text = getString(R.string.offline);
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            }
        } catch (Exception ignored) {
        }
        if (!TextUtils.equals(btnWifi.getContentDescription(), text)) btnWifi.setContentDescription(text);
        if (icon != wifiIcon) {
            wifiIcon = icon;
            btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        }
    }

    /** Brightness of the Wi-Fi and Settings icons while they are not focused. */
    private static final float ICON_REST_ALPHA = Dim.REST;

    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    private void makeIconOnly(TextView pill) {
        pill.setContentDescription(pill.getText());
        pill.setText(null);
        pill.setMaxWidth(Integer.MAX_VALUE);
        int pad = Math.round(10 * density);
        pill.setPaddingRelative(pad, 0, pad, 0);
        pill.setCompoundDrawablePadding(0);
        pill.getLayoutParams().width = Math.round(42 * density);
        if (pill.getId() == R.id.btnWifi || pill.getId() == R.id.btnSettings) {
            // Bare white icon, close to its neighbour; a soft white glow behind it only while focused.
            pill.setBackgroundResource(R.drawable.icon_glass_bg);
            ((ViewGroup.MarginLayoutParams) pill.getLayoutParams()).setMarginStart(Math.round(2 * density));
            pill.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            // Like the logos: resting a little dimmed, growing and lighting up when focused.
            pill.setAlpha(ICON_REST_ALPHA);
            View.OnFocusChangeListener previous = pill.getOnFocusChangeListener();
            pill.setOnFocusChangeListener((v, focused) -> {
                if (previous != null) previous.onFocusChange(v, focused);
                v.animate().alpha(focused ? 1f : ICON_REST_ALPHA)
                        .scaleX(focused ? 1.15f : 1f).scaleY(focused ? 1.15f : 1f)
                        .setDuration(160).start();
            });
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- hold OK ---------------------------------------------------------------------------------
    // View's own long click misses on some remotes: an IR remote that sends its repeat frames as separate
    // presses, or a Bluetooth one whose repeats land after a tile rebind, never holds one DOWN long enough.
    // So the launcher times the hold itself: OK down on a tile, still down (or still repeating) after
    // HOLD_OK_MS picks the app up to move it, and the UP that follows is eaten so it doesn't also open the app.

    private static final long HOLD_OK_MS = 550;
    private long holdOkDown = -1;
    private int holdOkPosition = -1;
    private String holdOkKey;    // the app under OK when it went down; a tile rebuild can move another app to that slot
    private boolean holdOkFired;
    private final Runnable holdOkTimer = this::fireHoldOk;

    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    /** True when the event belongs to a hold that picked an app up and must not reach the tile. */
    private boolean holdOk(KeyEvent event) {
        if (!isOkKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                holdOkFired = false;
                handler.removeCallbacks(holdOkTimer);
                holdOkPosition = reordering ? -1 : focusedTilePosition();
                holdOkKey = holdOkPosition < 0 || holdOkPosition >= adapter.appCount() ? null : adapter.keyAt(holdOkPosition);
                if (holdOkKey == null) {
                    holdOkDown = -1;
                    return false;
                }
                holdOkDown = event.getDownTime();
                handler.postDelayed(holdOkTimer, HOLD_OK_MS);
                return false;
            }
            if (holdOkFired) return true;
            if (holdOkDown >= 0 && event.getEventTime() - holdOkDown >= HOLD_OK_MS) {
                fireHoldOk();
                return true;
            }
            return false;
        }
        // ACTION_UP
        handler.removeCallbacks(holdOkTimer);
        holdOkDown = -1;
        holdOkKey = null;
        if (holdOkFired) {
            holdOkFired = false;
            View f = getCurrentFocus();
            if (f != null) f.setPressed(false);
            return true;
        }
        return false;
    }

    /**
     * Stops timing a held OK when the launcher is paused, loses its window or goes away, so a move can't start
     * behind another screen. An interrupted press still counts as handled: its UP, if it comes back here, is eaten
     * rather than opening the tile.
     */
    private void cancelHoldOk() {
        handler.removeCallbacks(holdOkTimer);
        if (holdOkDown >= 0) holdOkFired = true;
        holdOkDown = -1;
        holdOkPosition = -1;
        holdOkKey = null;
    }

    private void fireHoldOk() {
        handler.removeCallbacks(holdOkTimer);
        if (holdOkDown < 0 || holdOkFired) return;
        holdOkFired = true;
        if (reordering) return;   // View's own long click got there first
        int position = holdOkPosition;
        if (position < 0 || position >= adapter.appCount() || position != focusedTilePosition()) return;
        // Same slot is not enough: tiles may have been rebuilt during the hold, so the app must match too.
        if (!holdOkKey.equals(adapter.keyAt(position))) return;
        View card = getCurrentFocus();
        if (card != null) card.setPressed(false);
        onTileLongPress(position, card);
    }

    /**
     * Holding OK picked an app up: in the middle of the screen the word "Move" between two thin arrows fades in, the
     * arrows breathe outwards twice, and with "OK to place" under it the whole hint fades away. Three text views and a
     * few short property animations, removed when done.
     */
    private void showMoveHint() {
        hideMoveHint();
        float d = density;
        int ink = 0xFFE6E6E6;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView left = hintText("\u2039", 58, ink, R.string.font_extralight);
        TextView word = hintText(getString(R.string.move_hint), 30, ink, R.string.font_light);
        TextView right = hintText("\u203A", 58, ink, R.string.font_extralight);
        LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        side.leftMargin = side.rightMargin = Math.round(26 * d);
        row.addView(left, new LinearLayout.LayoutParams(side));
        row.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(right, new LinearLayout.LayoutParams(side));
        TextView okLine = hintText(getString(R.string.move_hint_ok), 15, 0xFF8C8C8C, R.string.font_regular);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ol.topMargin = Math.round(10 * d);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(okLine, ol);
        // Full width, centred: a wrap-content box here was measured too narrow and broke the word over two lines.
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        moveHint = box;
        box.setAlpha(0f);
        box.setScaleX(0.96f);
        box.setScaleY(0.96f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        float reach = 12 * d;
        for (int i = 0; i < 2; i++) {
            // One animator per view runs one animation at a time, so each breath is started on its own.
            handler.postDelayed(() -> {
                breathe(left, -reach);
                breathe(right, reach);
            }, 380 + i * 780L);
        }
        handler.postDelayed(fadeMoveHint, 2600);
    }

    private final Runnable fadeMoveHint = () -> {
        View hint = moveHint;
        if (hint == null) return;
        hint.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            if (moveHint == hint) hideMoveHint();
        }).start();
    };

    /** An arrow drifts out and dims, then comes back. */
    private static void breathe(View arrow, float dx) {
        arrow.animate().translationX(dx).alpha(0.4f).setDuration(380).withEndAction(() ->
                arrow.animate().translationX(0f).alpha(1f).setDuration(380).start()).start();
    }

    private void hideMoveHint() {
        handler.removeCallbacks(fadeMoveHint);
        if (moveHint == null) return;
        moveHint.animate().cancel();
        root.removeView(moveHint);
        moveHint = null;
    }

    private TextView hintText(CharSequence text, float sp, int color, int font) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(UiFont.get(this, font));
        t.setIncludeFontPadding(true);
        t.setSingleLine(true);
        t.setShadowLayer(18 * density, 0, 0, 0xCC000000);   // stays legible over a logo
        return t;
    }
}
