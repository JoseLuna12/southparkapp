package com.unofficial.southparktv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One accessible grid item. Presentation only: its caller owns requests and activation. */
final class EpisodeCard extends LinearLayout {
    private final ImageView artwork;
    private final TextView metadata;
    private final TextView title;
    private String artworkKey = "";

    EpisodeCard(Context context) {
        super(context);
        setId(R.id.episode_card);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_episode_card);
        int padding = getResources().getDimensionPixelSize(R.dimen.tv_space_1);
        setPaddingRelative(padding, padding, padding, padding);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (android.os.Build.VERSION.SDK_INT >= 28) setScreenReaderFocusable(true);
        artwork = new ImageView(context);
        artwork.setId(R.id.episode_artwork);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundResource(R.drawable.bg_artwork);
        artwork.setClipToOutline(true);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(artwork, new LayoutParams(-1, 0));
        metadata = TvUi.text(context, "", R.dimen.tv_metadata_type, R.color.tv_text_secondary);
        metadata.setSingleLine(true);
        metadata.setEllipsize(TextUtils.TruncateAt.END);
        metadata.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams metaParams = new LayoutParams(-1, -2);
        metaParams.topMargin = padding;
        addView(metadata, metaParams);
        title = TvUi.text(context, "", R.dimen.tv_card_title_type, R.color.tv_text_primary);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setMinLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(title, new LayoutParams(-1, -2));
    }

    void bind(Catalog.Episode episode, int columnWidth, String key) {
        artworkKey = key;
        artwork.setImageResource(R.drawable.artwork_placeholder);
        LayoutParams imageParams = (LayoutParams) artwork.getLayoutParams();
        imageParams.height = Math.round(Math.max(1, columnWidth - getPaddingStart() - getPaddingEnd()) * 9f / 16f);
        artwork.setLayoutParams(imageParams);
        metadata.setText(getResources().getString(episode.locked() ? R.string.episode_locked_metadata
                : R.string.episode_metadata, episode.label(), episode.duration()));
        title.setText(episode.title());
        setContentDescription(getResources().getString(episode.locked() ? R.string.episode_locked_accessibility
                : R.string.episode_card_accessibility, episode.label(), episode.title(), episode.duration()));
    }

    /** A recycled card must never receive another episode's delayed result. UI thread only. */
    void showArtwork(String key, Bitmap bitmap) {
        if (artworkKey.equals(key)) artwork.setImageBitmap(bitmap);
    }
}
