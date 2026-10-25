package net.memezforbeanz.starminerodyssey.blocks.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

/**
 * Base fragment for the generator settings windows, a One Dark styled dialog: title bar, rows
 * (label left, control right) and a primary action. Subclasses build rows in
 * {@link #onCreateTerminalContent}.
 */
public abstract class TerminalFragment extends Fragment {

    private final String titleText;

    protected TerminalWindowView window;

    protected TerminalFragment(String titleText) {
        this.titleText = titleText;
    }

    /** Build the settings rows below the title bar. */
    protected abstract void onCreateTerminalContent(LinearLayout content);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);

        window = new TerminalWindowView(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView titleBar = new TextView(context);
        titleBar.setTextSize(13);
        titleBar.setTextColor(OneDark.MUTED);
        titleBar.setGravity(Gravity.CENTER_HORIZONTAL);
        titleBar.setSingleLine();
        titleBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        titleBar.setBackground(new ColorDrawable(OneDark.BG_DARK));
        titleBar.setText(titleText);
        column.addView(titleBar, matchWrap());

        View separator = new View(context);
        separator.setBackground(new ColorDrawable(OneDark.BORDER));
        column.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(12));
        onCreateTerminalContent(content);
        column.addView(content, matchWrap());

        window.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(window, new FrameLayout.LayoutParams(
                dp(340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    // ------------------------------------------------------------------
    // Settings-page building blocks
    // ------------------------------------------------------------------

    /** One settings row: label taking the free space, the control on the right. */
    protected void addSettingRow(LinearLayout content, String label, View control, int topDp) {
        Context context = content.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setTextSize(13);
        labelView.setTextColor(OneDark.FG);
        labelView.setText(label);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.addView(control, wrapWrap());
        content.addView(row, matchWrapWithTop(topDp));
    }

    /** A full-width hairline between sections. */
    protected void addDivider(LinearLayout content, int topDp) {
        View divider = new View(content.getContext());
        divider.setBackground(new ColorDrawable(OneDark.BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(topDp), 0, 0);
        content.addView(divider, params);
    }

    /** The right-aligned primary action closing out the window. */
    protected void addFooter(LinearLayout content, Button primary, int topDp) {
        LinearLayout.LayoutParams params = wrapWrap();
        params.gravity = Gravity.END;
        params.setMargins(0, dp(topDp), 0, 0);
        content.addView(primary, params);
    }

    // ------------------------------------------------------------------
    // Styled widget helpers
    // ------------------------------------------------------------------

    /** A secondary button (cycle controls): flat fill, brightens under the ripple. */
    protected Button termButton(Context context, String text, View.OnClickListener onClick) {
        Button button = new Button(context);
        button.setTextSize(13);
        button.setTextColor(OneDark.TEXT_BRIGHT);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x80528BFF),
                fill(OneDark.BORDER), null));
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        button.setMinimumHeight(dp(24));
        return withClick(button, onClick);
    }

    /** The primary (Apply) button: accent fill, bright text. */
    protected Button accentButton(Context context, String text, View.OnClickListener onClick) {
        Button button = new Button(context);
        button.setTextSize(13);
        button.setTextColor(0xFFFFFFFF);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x80FFFFFF),
                fill(OneDark.ACCENT), null));
        button.setPadding(dp(14), dp(5), dp(14), dp(5));
        button.setMinimumHeight(dp(26));
        button.setMinimumWidth(dp(88));
        return withClick(button, onClick);
    }

    private Button withClick(Button button, View.OnClickListener onClick) {
        button.setOnClickListener(v -> {
            if (window.isFullyOpen()) {
                onClick.onClick(v);
            }
        });
        return button;
    }

    /**
     * A single-line monospaced input well; the border lights up in the focus blue while
     * it has the caret.
     */
    protected EditText termField(Context context, String initialValue) {
        EditText field = new EditText(context);
        field.setTypeface(Typeface.MONOSPACED);
        field.setTextSize(13);
        field.setTextColor(OneDark.FG);
        field.setSingleLine();
        field.setGravity(Gravity.CENTER_HORIZONTAL);
        field.setPadding(dp(6), dp(4), dp(6), dp(4));
        field.setBackground(well(OneDark.INPUT, OneDark.BORDER));
        field.setText(initialValue);
        field.setOnFocusChangeListener((v, hasFocus) ->
                v.setBackground(well(OneDark.INPUT, hasFocus ? OneDark.FOCUS : OneDark.BORDER)));
        return field;
    }

    private GradientDrawable fill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(2));
        return drawable;
    }

    private GradientDrawable well(int fill, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), border);
        drawable.setCornerRadius(dp(2));
        return drawable;
    }

    // ------------------------------------------------------------------
    // Layout + platform helpers
    // ------------------------------------------------------------------

    protected int dp(float value) {
        return window.dp(value);
    }

    protected LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(topDp), 0, 0);
        return params;
    }

    /** Run {@code apply} on the game thread (fragment callbacks run on the ModernUI thread), then close the screen. */
    protected void applyAndClose(Runnable apply) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            apply.run();
            minecraft.setScreen(null);
        });
    }
}
