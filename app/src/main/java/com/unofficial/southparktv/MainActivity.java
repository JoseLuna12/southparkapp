package com.unofficial.southparktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Native catalog. Website HTML is fetched as data, never rendered in a WebView. */
public final class MainActivity extends Activity {
    private final SiteClient site = new SiteClient();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor images = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(48), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String, Bitmap> imageCache = new LruCache<>(12 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount() / 1024; }
    };
    private final List<Catalog.Season> seasons = new ArrayList<>();
    private List<Catalog.Episode> episodes = new ArrayList<>();
    private Catalog.Season selected;
    private ListView seasonList;
    private GridView grid;
    private TextView status, heading;
    private Button retryButton;
    private Button reloadButton;
    private LinearLayout historyRow;
    private TextView historyLabel;
    private Button continueWatching;
    private Button nextEpisode;
    private Runnable retryAction;
    private Future<?> catalogRequest;
    private int generation;
    private boolean loaded;
    private String recommendationId = "";
    private int recommendationGeneration;
    private boolean restoreGridFocus;
    private String returnEpisodeId = "";
    private int returnSeason;
    private int returnFocusId = R.id.episode_grid;
    private int returnPosition = -1;
    private int returnOffset;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout page = column();
        page.setBackgroundColor(getColor(R.color.tv_background));
        int horizontalInset = dimension(R.dimen.tv_safe_horizontal);
        int verticalInset = dimension(R.dimen.tv_safe_vertical);
        page.setPaddingRelative(horizontalInset, verticalInset, horizontalInset, verticalInset);
        setContentView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = TvUi.text(this, getString(R.string.brand_name), R.dimen.tv_header_type, R.color.tv_text_primary);
        brand.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        header.addView(brand);
        TextView brandTv = TvUi.text(this, getString(R.string.brand_tv), R.dimen.tv_header_type, R.color.tv_accent);
        brandTv.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        LinearLayout.LayoutParams brandTvParams = wrap();
        brandTvParams.setMarginStart(dp(8));
        header.addView(brandTv, brandTvParams);
        TextView context = TvUi.text(this, getString(R.string.brand_context), R.dimen.tv_eyebrow_type, R.color.tv_text_secondary);
        context.setSingleLine(true);
        context.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(0, -2, 1);
        contextParams.setMarginStart(dp(20));
        header.addView(context, contextParams);
        reloadButton = new Button(this);
        reloadButton.setId(R.id.catalog_reload);
        reloadButton.setText(R.string.reload);
        reloadButton.setContentDescription(getString(R.string.reload));
        TvUi.styleButton(this, reloadButton);
        reloadButton.setNextFocusDownId(R.id.episode_grid);
        reloadButton.setOnClickListener(v -> reloadSelectedSeason());
        header.addView(reloadButton, wrap());
        page.addView(header);

        historyRow = new LinearLayout(this);
        historyRow.setGravity(Gravity.CENTER_VERTICAL);
        historyRow.setBaselineAligned(false);
        historyRow.setVisibility(View.GONE);
        historyLabel = TvUi.text(this, "", R.dimen.tv_metadata_type, R.color.tv_text_secondary);
        historyLabel.setMaxLines(2);
        historyLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams historyLabelParams = new LinearLayout.LayoutParams(dp(176), -2);
        historyLabelParams.setMarginEnd(dp(8));
        historyRow.addView(historyLabel, historyLabelParams);
        continueWatching = historyButton(R.id.continue_watching);
        nextEpisode = historyButton(R.id.next_episode);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, -2, 1);
        actionParams.setMargins(dp(6), 0, dp(6), 0);
        historyRow.addView(continueWatching, actionParams);
        historyRow.addView(nextEpisode, new LinearLayout.LayoutParams(actionParams));
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, -2);
        historyParams.topMargin = dp(12);
        page.addView(historyRow, historyParams);

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.topMargin = dp(16);
        bodyParams.bottomMargin = dp(8);
        page.addView(body, bodyParams);

        LinearLayout sidebar = column();
        sidebar.setPaddingRelative(0, 0, dp(12), 0);
        body.addView(sidebar, new LinearLayout.LayoutParams(dimension(R.dimen.tv_rail_width), -1));
        TextView seasonsHeading = TvUi.text(this, getString(R.string.seasons_heading), R.dimen.tv_body_type, R.color.tv_text_primary);
        seasonsHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        seasonsHeading.setPaddingRelative(dp(8), 0, 0, dp(8));
        sidebar.addView(seasonsHeading, wrap());
        seasonList = new ListView(this);
        seasonList.setId(R.id.season_list);
        seasonList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        seasonList.setDivider(new android.graphics.drawable.ColorDrawable(getColor(R.color.tv_background)));
        seasonList.setDividerHeight(dp(8));
        seasonList.setSelector(R.drawable.bg_list_focus);
        seasonList.setDrawSelectorOnTop(true);
        seasonList.setNextFocusUpId(R.id.catalog_reload);
        sidebar.addView(seasonList, new LinearLayout.LayoutParams(-1, 0, 1));
        seasonList.setOnItemClickListener((parent, view, position, id) -> loadSeason(seasons.get(position), true));

        LinearLayout content = column();
        body.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        heading = TvUi.text(this, getString(R.string.section_episodes), R.dimen.tv_heading_type,
                R.color.tv_text_primary);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading, wrap());

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        status = TvUi.text(this, getString(R.string.status_loading_seasons), R.dimen.tv_metadata_type,
                R.color.tv_text_secondary);
        status.setId(R.id.catalog_status);
        status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, -2, 1);
        statusParams.setMargins(0, dp(4), dp(8), dp(4));
        statusRow.addView(status, statusParams);
        retryButton = new Button(this);
        retryButton.setId(R.id.catalog_retry);
        retryButton.setText(R.string.retry);
        retryButton.setVisibility(View.GONE);
        retryButton.setContentDescription(getString(R.string.retry));
        TvUi.styleButton(this, retryButton);
        retryButton.setNextFocusDownId(R.id.episode_grid);
        retryButton.setNextFocusUpId(R.id.catalog_reload);
        retryButton.setOnClickListener(v -> { if (retryAction != null) retryAction.run(); });
        statusRow.addView(retryButton, wrap());
        content.addView(statusRow, wrap());

        grid = new GridView(this);
        grid.setId(R.id.episode_grid);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dimension(R.dimen.tv_card_gutter));
        grid.setVerticalSpacing(dimension(R.dimen.tv_card_gutter));
        grid.setPadding(dp(5), dp(5), dp(5), dp(5));
        grid.setClipToPadding(false);
        grid.setSelector(R.drawable.bg_grid_focus);
        grid.setDrawSelectorOnTop(true);
        grid.setNextFocusUpId(R.id.catalog_reload);
        continueWatching.setNextFocusDownId(grid.getId());
        nextEpisode.setNextFocusDownId(grid.getId());
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        grid.setOnItemClickListener((parent, view, position, id) -> details(episodes.get(position)));

        TextView footer = TvUi.text(this, getString(R.string.footer_hint), R.dimen.tv_footer_type,
                R.color.tv_text_tertiary);
        page.addView(footer, wrap());
        loadSeasons();
    }

    private int dp(float value) { return TvUi.dp(this, value); }
    private int dimension(int id) { return Math.round(getResources().getDimension(id)); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private Button historyButton(int id) {
        Button button = new Button(this);
        button.setId(id);
        button.setMaxLines(3);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TvUi.styleButton(this, button);
        return button;
    }

    private void setStatus(CharSequence message, Runnable retry) {
        status.setText(message);
        status.setTextColor(getColor(retry == null ? R.color.tv_text_secondary : R.color.tv_error));
        retryAction = retry;
        retryButton.setVisibility(retry == null ? View.GONE : View.VISIBLE);
        grid.setNextFocusUpId(retry != null ? R.id.catalog_retry
                : historyRow.getVisibility() == View.VISIBLE ? R.id.continue_watching : R.id.catalog_reload);
    }

    private void loadSeasons() {
        setStatus(getString(R.string.status_loading_seasons), null);
        int ticket = ++generation;
        catalogRequest = network.submit(() -> {
            try {
                List<Catalog.Season> result = site.seasons();
                runOnUiThread(() -> {
                    if (isDestroyed() || ticket != generation) return;
                    seasons.clear();
                    seasons.addAll(result);
                    if (result.isEmpty()) {
                        setStatus(getString(R.string.status_empty_catalog), this::loadSeasons);
                        return;
                    }
                    seasonList.setAdapter(seasonAdapter());
                    int number = getPreferences(MODE_PRIVATE).getInt("season", 12);
                    Catalog.Season initial = seasons.stream().filter(s -> s.number() == number).findFirst()
                            .orElse(seasons.get(0));
                    int index = seasons.indexOf(initial);
                    seasonList.setItemChecked(index, true);
                    seasonList.setSelection(index);
                    seasonList.requestFocus();
                    loadSeason(initial, false);
                });
            } catch (Exception e) {
                failure(ticket, getString(R.string.status_seasons_error), e, this::loadSeasons);
            }
        });
    }

    private BaseAdapter seasonAdapter() {
        return new BaseAdapter() {
            @Override public int getCount() { return seasons.size(); }
            @Override public Object getItem(int position) { return seasons.get(position); }
            @Override public long getItemId(int position) { return seasons.get(position).number(); }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView label = recycled instanceof TextView ? (TextView) recycled
                        : TvUi.text(MainActivity.this, "", R.dimen.tv_body_type, R.color.tv_text_primary);
                label.setText(getString(R.string.season_label, seasons.get(position).number()));
                label.setPaddingRelative(dp(14), dp(8), dp(8), dp(8));
                label.setMinHeight(dimension(R.dimen.tv_rail_row_height));
                label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                label.setBackgroundResource(R.drawable.bg_season_row);
                label.setActivated(selected != null && selected.number() == seasons.get(position).number());
                return label;
            }
        };
    }

    private void loadSeason(Catalog.Season season, boolean focusEpisodes) {
        if (selected != null && selected.number() == season.number() && loaded) {
            grid.requestFocus();
            return;
        }
        if (catalogRequest != null) catalogRequest.cancel(true);
        int ticket = ++generation;
        loaded = false;
        selected = season;
        heading.setText(getString(R.string.season_label, season.number()));
        int seasonIndex = seasons.indexOf(season);
        if (seasonIndex >= 0) {
            seasonList.setItemChecked(seasonIndex, true);
            ((BaseAdapter) seasonList.getAdapter()).notifyDataSetChanged();
        }
        setStatus(getString(R.string.status_loading_episodes), null);
        episodes = new ArrayList<>();
        grid.setAdapter(new EpisodeAdapter());
        catalogRequest = network.submit(() -> {
            try {
                List<Catalog.Episode> result = site.episodes(season);
                runOnUiThread(() -> {
                    if (isDestroyed() || ticket != generation) return;
                    loaded = true;
                    episodes = result;
                    getPreferences(MODE_PRIVATE).edit().putInt("season", season.number()).apply();
                    setStatus(result.isEmpty() ? getString(R.string.status_empty_season)
                            : getString(R.string.status_counts,
                                    getResources().getQuantityString(R.plurals.episode_count, result.size(), result.size()),
                                    getResources().getQuantityString(R.plurals.season_count, seasons.size(), seasons.size())), null);
                    grid.setAdapter(new EpisodeAdapter());
                    refreshHistory();
                    if (focusEpisodes && !result.isEmpty() && (seasonList.hasFocus() || grid.hasFocus())) {
                        grid.setSelection(0);
                        grid.requestFocus();
                    }
                });
            } catch (Exception e) {
                failure(ticket, getString(R.string.status_season_error), e,
                        () -> loadSeason(season, seasonList.hasFocus()));
            }
        });
    }

    private void failure(int ticket, String message, Exception error, Runnable retry) {
        runOnUiThread(() -> {
            if (isDestroyed() || ticket != generation) return;
            setStatus(message, retry);
        });
    }

    private void reloadSelectedSeason() {
        if (selected == null) loadSeasons();
        else {
            loaded = false;
            loadSeason(selected, false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHistory();
        if (restoreGridFocus) {
            restoreGridFocus = false;
            grid.post(() -> {
                if (isDestroyed()) return;
                if (returnFocusId != R.id.episode_grid) {
                    View target = findViewById(returnFocusId);
                    if (target != null && target.isShown() && target.isEnabled()) { target.requestFocus(); return; }
                }
                if (selected == null || selected.number() != returnSeason || episodes.isEmpty()) return;
                int position = findEpisode(returnEpisodeId);
                if (position < 0) position = Math.max(0, Math.min(returnPosition, episodes.size() - 1));
                grid.setSelectionFromTop(position, returnOffset);
                grid.requestFocus();
            });
        }
    }

    private void rememberGridFocus(Catalog.Episode episode) {
        restoreGridFocus = true;
        returnFocusId = continueWatching.hasFocus() ? R.id.continue_watching
                : nextEpisode.hasFocus() ? R.id.next_episode : R.id.episode_grid;
        returnSeason = selected == null ? 0 : selected.number();
        returnEpisodeId = episode.id();
        int position = findEpisode(episode.id());
        if (position < 0) position = grid.getSelectedItemPosition();
        returnPosition = position;
        int childIndex = position - grid.getFirstVisiblePosition();
        View child = childIndex >= 0 && childIndex < grid.getChildCount() ? grid.getChildAt(childIndex) : null;
        returnOffset = child == null ? 0 : child.getTop();
    }

    private int findEpisode(String id) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).id().equals(id)) return i;
        return -1;
    }

    private void refreshHistory() {
        WatchHistory.Entry last = WatchHistory.last(getSharedPreferences("playback", MODE_PRIVATE));
        historyRow.setVisibility(last == null ? View.GONE : View.VISIBLE);
        seasonList.setNextFocusUpId(last == null ? R.id.catalog_reload : R.id.continue_watching);
        grid.setNextFocusUpId(retryAction != null ? R.id.catalog_retry
                : last == null ? R.id.catalog_reload : R.id.continue_watching);
        if (last == null) {
            recommendationId = "";
            ++recommendationGeneration;
            return;
        }
        Catalog.Season lastSeason = new Catalog.Season(last.season(), last.seasonPath());
        historyLabel.setText(getString(R.string.history_last_watched, last.episode().label(), last.episode().title()));
        long seconds = last.positionMs() / 1000;
        String resume = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        continueWatching.setText(last.completed()
                ? getString(R.string.watch_again_action, last.episode().label(), last.episode().title())
                : getString(R.string.continue_action, last.episode().label(), last.episode().title(), resume));
        continueWatching.setOnClickListener(v -> startEpisode(last.episode(), lastSeason));
        if (last.episode().id().equals(recommendationId)) return;
        nextEpisode.setEnabled(false);
        nextEpisode.setText(R.string.next_finding);
        nextEpisode.setContentDescription(getString(R.string.next_finding));
        if (seasons.isEmpty()) return;
        recommendationId = last.episode().id();
        int ticket = ++recommendationGeneration;
        List<Catalog.Season> availableSeasons = List.copyOf(seasons);
        List<Catalog.Episode> cached = loaded && selected != null && selected.number() == last.season()
                ? List.copyOf(episodes) : null;
        network.submit(() -> {
            try {
                SiteClient.NextEpisode suggestion = site.next(last.episode(), lastSeason, cached, availableSeasons);
                runOnUiThread(() -> {
                    if (isDestroyed() || ticket != recommendationGeneration) return;
                    if (suggestion == null) {
                        nextEpisode.setEnabled(false);
                        nextEpisode.setText(R.string.next_none);
                        nextEpisode.setContentDescription(getString(R.string.next_none));
                        return;
                    }
                    nextEpisode.setEnabled(true);
                    nextEpisode.setText(getString(R.string.next_action, suggestion.episode().label(),
                            suggestion.episode().title()));
                    nextEpisode.setContentDescription(getString(R.string.next_action, suggestion.episode().label(),
                            suggestion.episode().title()));
                    nextEpisode.setOnClickListener(v -> details(suggestion.episode(), suggestion.season()));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || ticket != recommendationGeneration) return;
                    recommendationId = "";
                    nextEpisode.setText(R.string.next_unavailable);
                    nextEpisode.setContentDescription(getString(R.string.next_unavailable));
                    nextEpisode.setEnabled(true);
                    nextEpisode.setOnClickListener(v -> refreshHistory());
                });
            }
        });
    }

    private void startEpisode(Catalog.Episode episode, Catalog.Season season) {
        if (episode.locked()) { external(episode.path()); return; }
        rememberGridFocus(episode);
        startActivity(new Intent(this, PlayerActivity.class).putExtra("id", episode.id())
                .putExtra("title", episode.title()).putExtra("label", episode.label()).putExtra("path", episode.path())
                .putExtra("description", episode.description()).putExtra("duration", episode.duration())
                .putExtra("season", season.number()).putExtra("seasonPath", season.path()));
    }

    private void details(Catalog.Episode episode) { details(episode, selected); }

    private void details(Catalog.Episode episode, Catalog.Season season) {
        String note = episode.locked() ? getString(R.string.locked_details_note) : "";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.episode_details_title, episode.label(), episode.title()))
                .setMessage(getString(R.string.episode_details_message, episode.description(), episode.duration(), note))
                .setPositiveButton(episode.locked() ? R.string.dialog_official_site : R.string.dialog_watch_episode,
                        (dialog, which) -> startEpisode(episode, season))
                .setNegativeButton(R.string.dialog_back, null);
        TvUi.showDialog(builder);

    }

    private void external(String path) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.officialPage(path)))); }
        catch (Exception e) {
            TvUi.showDialog(new AlertDialog.Builder(this).setMessage(R.string.status_browser_unavailable)
                    .setPositiveButton(android.R.string.ok, null));
        }
    }

    @Override public boolean onKeyUp(int key, android.view.KeyEvent event) {
        if (key == android.view.KeyEvent.KEYCODE_MENU) {
            if (seasons.isEmpty()) loadSeasons(); else reloadSelectedSeason();
            return true;
        }
        return super.onKeyUp(key, event);
    }

    @Override public void onBackPressed() {
        if (grid.hasFocus()) { seasonList.requestFocus(); return; }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        ++generation;
        ++recommendationGeneration;
        network.shutdownNow();
        images.shutdownNow();
        imageCache.evictAll();
        super.onDestroy();
    }

    private final class EpisodeAdapter extends BaseAdapter {
        @Override public int getCount() { return episodes.size(); }
        @Override public Object getItem(int position) { return episodes.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Catalog.Episode episode = episodes.get(position);
            EpisodeCard card = recycled instanceof EpisodeCard ? (EpisodeCard) recycled : new EpisodeCard(MainActivity.this);
            String address = artworkAddress(episode.image());
            card.bind(episode, grid.getColumnWidth() > 0 ? grid.getColumnWidth() : dp(220), address);
            loadArtwork(card, address);
            return card;
        }
    }

    private void loadArtwork(EpisodeCard card, String address) {
        if (address.isEmpty()) return;
        Bitmap cached = imageCache.get(address);
        if (cached != null) { card.showArtwork(address, cached); return; }
        images.execute(() -> {
            try {
                byte[] bytes = SiteClient.download(address, 2 * 1024 * 1024);
                Bitmap bitmap = decodeArtwork(bytes);
                if (bitmap == null || isDestroyed()) return;
                imageCache.put(address, bitmap);
                runOnUiThread(() -> { if (!isDestroyed()) card.showArtwork(address, bitmap); });
            } catch (Exception ignored) { /* Missing artwork must not block browsing. */ }
        });
    }

    private String artworkAddress(String source) {
        if (source == null || source.isEmpty()) return "";
        return Uri.parse(source).buildUpon().appendQueryParameter("width", "480")
                .appendQueryParameter("height", "270").appendQueryParameter("crop", "true").build().toString();
    }

    private Bitmap decodeArtwork(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        int target = 480; // Requested image pixels, not display-independent layout units.
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target * 9 / 16)
            sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
