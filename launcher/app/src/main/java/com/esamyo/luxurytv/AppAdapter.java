package com.esamyo.luxurytv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * App tiles: frameless logos. The first {@link MainActivity#COLUMNS} positions are the main row (dock_apps first,
 * then the owner's saved order); the rest follow alphabetically in the rows below.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Hold OK on a tile: the activity picks it up to move it. */
        void onTileLongPress(int position, View card);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable icon;
        Entry(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;
    /** item_app.xml: 8dp padding above and below, a 7dp gap and the 22dp name label under the tile. */
    static final float ITEM_EXTRA_DP = 45;
    /** DockDecoration's offsets above and below the main row. */
    static final float DOCK_EXTRA_DP = 6 + 12;
    /** Brightness of a logo that does not have the focus. */
    static final float REST_ICON_ALPHA = 0.72f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glow,
     * capped at the design size so 4 columns on a 1080p TV keep the design size.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(ITEM_EXTRA_DP * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** The owner's saved order (Order): key -> rank. Empty when there is none or it no longer applies. */
    private Map<String, Integer> savedRanks = Collections.emptyMap();
    /** Main-row order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (config.xml hidden_apps). */
    private final Set<String> hidden;
    /** Packages with a leanback entry at the last reload(): their tiles launch with the leanback category. */
    private Set<String> leanbackPackages = new HashSet<>();
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /** Rebuilds the tiles from the TV apps (leanback launcher entries). */
    void reload() {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        Set<String> tvPackages = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) tvPackages.add(ri.activityInfo.packageName);
        leanbackPackages = tvPackages;
        List<Entry> fresh = new ArrayList<>();
        // Some system apps declare two leanback entry points under one name: keep the first per package and label.
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !ri.activityInfo.exported || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            String label = cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
            if (!seen.add(pkg + '\n' + label)) continue;
            int logo = customLogo(ctx, pkg);
            Drawable icon = logo != 0 ? ctx.getDrawable(logo) : ri.loadIcon(pm);
            fresh.add(new Entry(label, component, icon));
        }
        Collections.sort(fresh, (a, b) -> {
            // The owner's own order first; tiles it does not know (new installs) follow by the rule below.
            int saved = Order.compare(savedRanks, a.component.flattenToShortString(), b.component.flattenToShortString());
            if (saved != 0) return saved;
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    /** An app label as one plain line of at most 40 characters: a bidi override or a line separator must not
     *  redraw the tile name as something else. */
    static String cleanLabel(String s, String fallback) {
        if (s == null) return fallback;
        StringBuilder b = new StringBuilder(Math.min(s.length(), 48));
        boolean space = true;   // leading spaces are dropped too
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            boolean blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SPACE_SEPARATOR;
            if (blank) {
                if (!space) b.append(' ');
                space = true;
                continue;
            }
            boolean pair = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
            if (b.length() + (pair ? 2 : 1) > 40) break;
            b.append(c);
            if (pair) b.append(s.charAt(++i));
            space = false;
        }
        String out = b.toString().trim();
        return out.isEmpty() ? fallback : out;
    }

    /** Lets go of every tile and its artwork (Home is in the background and memory is short). */
    void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        // A logo of any shape fits this box, centred; no card is drawn around it.
        View icon = v.findViewById(R.id.icon);
        FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
        iconLp.width = Math.round(w * 0.62f);
        iconLp.height = Math.round(h * 0.5f);
        icon.setLayoutParams(iconLp);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        // The main row has no names; the rows below show the focused tile's name.
        boolean inDock = position < dockSize;
        h.name.setVisibility(inDock ? View.INVISIBLE : View.VISIBLE);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.icon.setAlpha(focused ? 1f : REST_ICON_ALPHA);
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        h.icon.setImageDrawable(e.icon);
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onTileLongPress(pos, v);
            return true;
        });
    }

    /** Opens the tile's app the way the stock launcher does, so an app already running resumes. */
    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (leanbackPackages.contains(e.pkg)) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    int appCount() { return items.size(); }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Uses this saved order from the next reload() (Order.ranks). */
    void setSavedOrder(Map<String, Integer> ranks) {
        savedRanks = ranks;
    }

    /** Keys of the tiles in their current order, for Order. */
    List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Entry e : items) keys.add(e.component.flattenToShortString());
        return keys;
    }

    /** Moves one tile; the others shift by one. Stable ids keep the moved tile's view (and its focus). */
    void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
        // The main row hides names by position: rebind the tiles whose side of the row edge changed.
        if (from < dockSize || to < dockSize) {
            int lo = Math.min(from, to), hi = Math.max(from, to);
            notifyItemRangeChanged(lo, hi - lo + 1);
        }
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String packageAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : null;
    }

    /**
     * An own logo for an app, instead of its icon: a PNG in res/drawable-nodpi named logo_ plus the package with
     * dots as underscores (logo_com_example_app.png for com.example.app). 0 when there is none.
     */
    private static int customLogo(Context ctx, String pkg) {
        String name = "logo_" + pkg.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView icon;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                // Logos rest a little dimmed; the focused one comes up to full brightness and grows.
                icon.animate().cancel();
                icon.animate().alpha(focused ? 1f : REST_ICON_ALPHA).setDuration(160).start();
                view.animate().cancel();
                name.animate().cancel();
                float s = focused ? FOCUS_SCALE : 1f;
                view.animate().scaleX(s).scaleY(s).translationY(0f).translationZ(0f)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .withLayer().start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
