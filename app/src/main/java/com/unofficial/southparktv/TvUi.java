package com.unofficial.southparktv;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

final class TvUi {
    private TvUi() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, CharSequence value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(size));
        view.setTextColor(context.getColor(color));
        view.setFontFeatureSettings("kern");
        return view;
    }

    static void styleButton(Context context, Button button) {
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.bg_action_button);
        button.setTextColor(context.getColorStateList(R.color.tv_button_text));
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.tv_body_type));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setMinHeight(Math.round(context.getResources().getDimension(R.dimen.tv_button_min_height)));
        int horizontalPadding = Math.round(context.getResources().getDimension(R.dimen.tv_space_2));
        int verticalPadding = Math.round(context.getResources().getDimension(R.dimen.tv_space_1));
        button.setPaddingRelative(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        button.setStateListAnimator(null);
    }

    static android.app.AlertDialog showDialog(android.app.AlertDialog.Builder builder) {
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        Context context = dialog.getContext();
        int available = context.getResources().getDisplayMetrics().widthPixels
                - 2 * context.getResources().getDimensionPixelSize(R.dimen.tv_safe_horizontal);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                Math.min(dp(context, 640), available), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        for (int role : new int[]{android.app.AlertDialog.BUTTON_POSITIVE,
                android.app.AlertDialog.BUTTON_NEGATIVE, android.app.AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(role);
            if (button != null) {
                styleButton(context, button);
                button.setGravity(Gravity.CENTER);
                if (button.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams params) {
                    params.setMarginEnd(dp(context, 8));
                    button.setLayoutParams(params);
                }
            }
        }
        Button primary = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        if (primary != null) primary.requestFocus();
        return dialog;
    }
}
