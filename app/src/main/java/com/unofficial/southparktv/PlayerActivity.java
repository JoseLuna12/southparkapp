package com.unofficial.southparktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Full-screen native player, including Google's native IMA DAI integration. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity {
    private final SiteClient site = new SiteClient();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Catalog.Episode episode;
    private PlayerView view;
    private android.widget.ProgressBar resolving;
    private ExoPlayer player;
    private androidx.media3.common.TrackSelectionParameters captionPreferences;
    private ImaServerSideAdInsertionMediaSource.AdsLoader ads;
    private Future<?> request;
    private int generation;
    private boolean active;
    private boolean errorShown;
    private long resumePosition;
    private int season;
    private String seasonPath;
    private boolean hasRendered;
    private boolean controlsVisible;
    private boolean advanceWhenReady;
    private boolean nextResolved;
    private boolean nextFailed;
    private SiteClient.NextEpisode next;
    private Future<?> nextRequest;
    private Button nextButton;
    private TextView episodeTitle;
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            if (player.isPlaying()) {
                savePosition();
                Log.d("SouthParkTV", "NATIVE_PROGRESS episode=" + episode.id() + " contentMs=" + player.getContentPosition()
                        + " ad=" + player.isPlayingAd());
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent intent = getIntent();
        String id = intent.getStringExtra("id"), path = intent.getStringExtra("path"), title = intent.getStringExtra("title");
        if (id == null || path == null || title == null || !path.startsWith("/episodes/")) { finish(); return; }
        episode = new Catalog.Episode(id, title, intent.getStringExtra("label"),
                intent.getStringExtra("description"), intent.getStringExtra("duration"), "", path, false);
        season = intent.getIntExtra("season", 0);
        seasonPath = intent.getStringExtra("seasonPath");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        resumePosition = getSharedPreferences("playback", MODE_PRIVATE).getLong(id, 0);
        view = new PlayerView(this);
        view.setId(R.id.player_view);
        styleTransport();
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        view.setControllerAutoShow(true);
        view.setControllerShowTimeoutMs(4000);
        view.setShowSubtitleButton(true);
        // Episodes use separate official sessions, not an ExoPlayer playlist.
        // Hide its inactive playlist arrows; the toolbar provides the real Next action.
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setContentDescription(getString(R.string.player_loading_episode, title));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(getColor(R.color.tv_video_background));
        frame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout toolbar = new LinearLayout(this);
        int inset = getResources().getDimensionPixelSize(R.dimen.tv_safe_horizontal);
        toolbar.setPaddingRelative(inset, getResources().getDimensionPixelSize(R.dimen.tv_safe_vertical), inset, dp(24));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundResource(R.drawable.bg_player_scrim);
        Button back = new Button(this);
        back.setId(R.id.player_back);
        back.setText(R.string.player_back);
        TvUi.styleButton(this, back);
        back.setNextFocusDownId(R.id.player_view);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);
        episodeTitle = TvUi.text(this, "", R.dimen.tv_heading_type, R.color.tv_text_primary);
        episodeTitle.setId(R.id.player_title);
        episodeTitle.setMaxLines(2);
        episodeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        episodeTitle.setPaddingRelative(dp(24), 0, dp(24), 0);
        toolbar.addView(episodeTitle, new LinearLayout.LayoutParams(0, -2, 1));
        nextButton = new Button(this);
        nextButton.setId(R.id.player_next);
        showNextState(R.string.player_loading_next);
        TvUi.styleButton(this, nextButton);
        nextButton.setNextFocusDownId(R.id.player_view);
        nextButton.setEnabled(false);
        nextButton.setOnClickListener(v -> advance());
        toolbar.addView(nextButton);
        frame.addView(toolbar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        view.setNextFocusUpId(back.getId());
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controlsVisible = visibility == View.VISIBLE;
            toolbar.setVisibility(visibility);
            insetSubtitles(controlsVisible);
        });
        android.view.View.OnFocusChangeListener keepControls = (v, focused) -> {
            view.setControllerShowTimeoutMs(focused || toolbar.hasFocus() ? 0 : 4000);
            if (focused) view.showController();
        };
        back.setOnFocusChangeListener(keepControls);
        nextButton.setOnFocusChangeListener(keepControls);
        resolving = new android.widget.ProgressBar(this);
        android.widget.FrameLayout.LayoutParams spinner = new android.widget.FrameLayout.LayoutParams(dp(48), dp(48), android.view.Gravity.CENTER);
        frame.addView(resolving, spinner);
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
                -> insetSubtitles(controlsVisible));
        setContentView(frame);
        view.requestFocus();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void styleTransport() {
        // Inset controls, never the video/IMA surface. Keep Media3's existing control layout and actions.
        int horizontal = getResources().getDimensionPixelSize(R.dimen.tv_safe_horizontal);
        int vertical = getResources().getDimensionPixelSize(R.dimen.tv_safe_vertical);
        for (int id : new int[]{androidx.media3.ui.R.id.exo_bottom_bar, androidx.media3.ui.R.id.exo_progress}) {
            View control = view.findViewById(id);
            if (control != null && control.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params) {
                params.setMarginStart(horizontal);
                params.setMarginEnd(horizontal);
                params.bottomMargin += vertical;
                control.setLayoutParams(params);
            }
        }
        View subtitles = view.getSubtitleView();
        if (subtitles != null && subtitles.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params) {
            params.setMarginStart(horizontal);
            params.setMarginEnd(horizontal);
            params.topMargin += vertical;
            params.bottomMargin += vertical;
            subtitles.setLayoutParams(params);
        }
        View progress = view.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (progress instanceof androidx.media3.ui.DefaultTimeBar bar) {
            bar.setPlayedColor(getColor(R.color.tv_accent));
            bar.setScrubberColor(getColor(R.color.tv_accent));
            bar.setBufferedColor(getColor(R.color.tv_text_tertiary));
            bar.setUnplayedColor(getColor(R.color.tv_outline));
        }
    }

    private void insetSubtitles(boolean visible) {
        View subtitles = view.getSubtitleView();
        if (subtitles == null || !(subtitles.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params)) return;
        int bottom = getResources().getDimensionPixelSize(R.dimen.tv_safe_vertical);
        View progress = view.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (visible && progress != null
                && progress.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams barParams) {
            // End the caption viewport above the visible transport chrome.
            bottom = Math.max(bottom, barParams.bottomMargin + progress.getHeight() + dp(8));
        }
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            subtitles.setLayoutParams(params);
        }
    }

    private void styleSubtitles() {
        androidx.media3.ui.SubtitleView subtitles = view.getSubtitleView();
        if (subtitles == null) return;
        android.view.accessibility.CaptioningManager preferences =
                (android.view.accessibility.CaptioningManager) getSystemService(CAPTIONING_SERVICE);
        if (preferences != null && preferences.isEnabled()) {
            // Explicit Android accessibility preferences take priority over our softer default.
            subtitles.setApplyEmbeddedStyles(true);
            subtitles.setUserDefaultStyle();
            subtitles.setUserDefaultTextSize();
            return;
        }
        subtitles.setApplyEmbeddedStyles(false);
        subtitles.setStyle(new androidx.media3.ui.CaptionStyleCompat(
                getColor(R.color.tv_text_primary), android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                getColor(R.color.tv_video_background), android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)));
        subtitles.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.tv_subtitle_type));
    }

    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (episode != null) {
            styleSubtitles();
            load();
        }
    }

    private void load() {
        if (request != null) request.cancel(true);
        if (nextRequest != null) nextRequest.cancel(true);
        release();
        next = null;
        nextResolved = false;
        nextFailed = false;
        advanceWhenReady = false;
        nextButton.setEnabled(false);
        showNextState(R.string.player_loading_next);
        episodeTitle.setText(getString(R.string.player_metadata,
                episode.label() == null || episode.label().isEmpty() ? getString(R.string.season_label, season) : episode.label(),
                episode.title()));
        hasRendered = false;
        errorShown = false;
        int ticket = ++generation;
        view.setContentDescription(getString(R.string.player_loading_episode, episode.title()));
        resolving.setVisibility(android.view.View.VISIBLE);
        request = network.submit(() -> {
            try {
                SiteClient.Playback config = site.playback(episode);
                runOnUiThread(() -> {
                    if (!active || isDestroyed() || ticket != generation) return;
                    try { play(config); }
                    catch (Exception e) { error(getString(R.string.player_initialization_error, e.getClass().getSimpleName())); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (active && !isDestroyed() && ticket == generation) error(e.getMessage()); });
            }
        });
    }

    private void play(SiteClient.Playback config) {
        resolving.setVisibility(android.view.View.GONE);
        ads = new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, view).build();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(SiteClient.USER_AGENT).setConnectTimeoutMs(15000).setReadTimeoutMs(20000);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(this).setDataSourceFactory(http);
        sources.setServerSideAdInsertionMediaSourceFactory(new ImaServerSideAdInsertionMediaSource.Factory(ads, sources));
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(sources)
                .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        if (captionPreferences != null) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, captionPreferences.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                    .setPreferredTextLanguages(captionPreferences.preferredTextLanguages.toArray(new String[0]))
                    .setPreferredTextRoleFlags(captionPreferences.preferredTextRoleFlags)
                    .setSelectUndeterminedTextLanguage(captionPreferences.selectUndeterminedTextLanguage).build());
        }
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setVolume(1f);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {
                Log.i("SouthParkTV", "NATIVE_AUDIO_STARTED episode=" + episode.id());
            }
            @Override public void onAudioSinkError(EventTime eventTime, Exception error) {
                Log.w("SouthParkTV", "NATIVE_AUDIO_ERROR type=" + error.getClass().getSimpleName());
            }
        });
        view.setPlayer(player);
        ads.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                hasRendered = true;
                savePosition();
                Log.i("SouthParkTV", "NATIVE_FIRST_FRAME episode=" + episode.id());
                view.setContentDescription(getString(R.string.player_playing_episode, episode.title()));
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                Log.d("SouthParkTV", "NATIVE_PLAYING episode=" + episode.id() + " value=" + playing);
            }
            @Override public void onVideoSizeChanged(VideoSize size) {
                Log.d("SouthParkTV", "NATIVE_VIDEO_SIZE width=" + size.width + " height=" + size.height
                        + " pixelRatio=" + size.pixelWidthHeightRatio);
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    savePosition();
                    advance();
                }
            }
            @Override public void onPlayerError(PlaybackException exception) {
                Log.w("SouthParkTV", "NATIVE_ERROR episode=" + episode.id() + " code=" + exception.getErrorCodeName());
                // Do not log exceptions or stream URIs: they can contain session credentials.
                error(getString(R.string.player_error_code, exception.getErrorCodeName()));
            }
        });
        Uri stream = new ImaServerSideAdInsertionUriBuilder().setContentSourceId(config.cmsId())
                .setVideoId(config.videoId()).setAuthToken(config.authToken())
                .setAdTagParameters(config.adParameters()).setFormat(C.CONTENT_TYPE_HLS).build();
        MediaItem item = new MediaItem.Builder().setMediaId(episode.id()).setUri(stream)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(episode.title()).build()).build();
        player.setMediaItem(item, resumePosition);
        player.prepare();
        player.play();
        handler.post(progress);
        resolveNext();
    }

    private void showNextState(int text) {
        nextButton.setText(text);
        nextButton.setContentDescription(getString(text));
    }

    private void resolveNext() {
        int ticket = generation;
        Catalog.Episode current = episode;
        Catalog.Season currentSeason = new Catalog.Season(season, seasonPath);
        nextRequest = network.submit(() -> {
            try {
                SiteClient.NextEpisode result = site.next(current, currentSeason, null, site.seasons());
                runOnUiThread(() -> {
                    if (!active || isDestroyed() || ticket != generation) return;
                    next = result;
                    nextResolved = true;
                    nextButton.setText(result == null ? R.string.player_no_next : result.episode().locked()
                            ? R.string.player_next_unavailable : R.string.player_next);
                    nextButton.setEnabled(result != null && !result.episode().locked());
                    nextButton.setContentDescription(result == null ? getString(R.string.next_none)
                            : (result.episode().locked() ? getString(R.string.player_next_unavailable) + ": " : "")
                            + getString(R.string.player_next_description, result.episode().label(), result.episode().title()));
                    Log.d("SouthParkTV", "NATIVE_NEXT episode=" + current.id() + " next="
                            + (result == null ? "none" : result.episode().id()));
                    if (advanceWhenReady) advance();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!active || isDestroyed() || ticket != generation) return;
                    nextResolved = true;
                    nextFailed = true;
                    showNextState(R.string.player_retry_next);
                    nextButton.setEnabled(true);
                    if (advanceWhenReady) {
                        advanceWhenReady = false;
                        Toast.makeText(this, R.string.player_retry_toast, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void advance() {
        if (!active || player == null
                || (player.isPlayingAd() && player.getPlaybackState() != Player.STATE_ENDED)) return;
        if (nextFailed) {
            nextFailed = false;
            nextResolved = false;
            advanceWhenReady = true;
            showNextState(R.string.player_loading_next);
            nextButton.setEnabled(false);
            resolveNext();
            return;
        }
        if (!nextResolved) { advanceWhenReady = true; return; }
        advanceWhenReady = false;
        if (next == null || next.episode().locked()) return;
        SiteClient.NextEpisode target = next;
        // Save/release against the OLD episode before changing the current identity.
        release();
        episode = target.episode();
        season = target.season().number();
        seasonPath = target.season().path();
        resumePosition = 0;
        setIntent(getIntent().putExtra("id", episode.id()).putExtra("title", episode.title())
                .putExtra("label", episode.label()).putExtra("path", episode.path())
                .putExtra("description", episode.description()).putExtra("duration", episode.duration())
                .putExtra("season", season).putExtra("seasonPath", seasonPath));
        Log.i("SouthParkTV", "NATIVE_ADVANCE episode=" + episode.id());
        view.requestFocus();
        load();
    }

    private void error(String message) {
        if (!active || isFinishing() || errorShown) return;
        errorShown = true;
        resolving.setVisibility(android.view.View.GONE);
        if (player != null) player.pause();
        view.setContentDescription(getString(R.string.player_unavailable));
        TvUi.showDialog(new AlertDialog.Builder(this).setTitle(R.string.player_error_title)
                .setMessage(message == null ? getString(R.string.player_error_fallback) : message)
                .setPositiveButton(R.string.player_error_retry, (d, w) -> load())
                .setNegativeButton(R.string.player_error_back, (d, w) -> finish())
                .setNeutralButton(R.string.player_error_official, (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.officialPage(episode.path())))); }
                    catch (android.content.ActivityNotFoundException e) { errorShown = false; error(getString(R.string.player_no_browser)); }
                }).setOnCancelListener(d -> finish()));
    }

    private void savePosition() {
        if (player == null || episode == null || !hasRendered
                || player.getCurrentTimeline().isEmpty() || player.getPlaybackState() == Player.STATE_IDLE) return;
        WatchHistory.Entry entry = WatchHistory.snapshot(episode, season, seasonPath,
                player.getContentPosition(), player.getContentDuration(), player.getPlaybackState() == Player.STATE_ENDED);
        resumePosition = entry.positionMs();
        if (season > 0 && seasonPath != null) WatchHistory.save(getSharedPreferences("playback", MODE_PRIVATE), entry);
        else getSharedPreferences("playback", MODE_PRIVATE).edit().putLong(episode.id(), resumePosition).apply();
    }

    private void rememberCaptions() {
        var parameters = player.getTrackSelectionParameters();
        var remembered = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT);
        // Native menus select episode-specific groups. Carry their language/role, never the old group.
        for (var override : parameters.overrides.values()) {
            if (override.getType() != C.TRACK_TYPE_TEXT || override.trackIndices.isEmpty()) continue;
            var format = override.mediaTrackGroup.getFormat(override.trackIndices.get(0));
            remembered.setPreferredTextLanguage(format.language)
                    .setPreferredTextRoleFlags(format.roleFlags)
                    .setSelectUndeterminedTextLanguage(true);
            break;
        }
        captionPreferences = remembered.build();
    }

    private void release() {
        handler.removeCallbacks(progress);
        if (player != null) {
            savePosition();
            rememberCaptions();
            view.setPlayer(null);
            player.release();
            player = null;
        }
        if (ads != null) { ads.release(); ads = null; }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) onBackPressed();
            return true;
        }
        boolean media = key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_MEDIA_PLAY
                || key == KeyEvent.KEYCODE_MEDIA_PAUSE || key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || key == KeyEvent.KEYCODE_MEDIA_REWIND || key == KeyEvent.KEYCODE_MEDIA_STOP;
        // Let normal focus dispatch reach the toolbar instead of giving PlayerView every D-pad event first.
        if (media && view != null && view.dispatchKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if (controlsVisible) view.hideController();
        else finish();
    }
    @Override protected void onPause() {
        if (player != null) { player.pause(); savePosition(); }
        super.onPause();
    }
    @Override protected void onStop() {
        active = false;
        ++generation;
        if (request != null) request.cancel(true);
        if (nextRequest != null) nextRequest.cancel(true);
        release();
        super.onStop();
    }
    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
}
