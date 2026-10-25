package net.memezforbeanz.starminerodyssey.blocks.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.blocks.AbstractGravityFieldBlockEntity;
import net.memezforbeanz.starminerodyssey.blocks.GravityGeneratorBlockEntity;
import net.memezforbeanz.starminerodyssey.gravity.GeneratorFieldMode;
import net.memezforbeanz.starminerodyssey.network.ModNetwork;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Settings window for the Gravity Generator, all field shapes in one screen: a mode row, then
 * that mode's rows; target gravity and visibility apply to every mode. Steppers follow Amethyst
 * Gravity (click 1 block, shift 0.1, ctrl 10; gravity 0.1 g, shift 0.01, ctrl 1). Client-side
 * only: Apply sends the settings to the server.
 */
public class GravityGeneratorFragment extends TerminalFragment {

    private static final String PREFIX = "screen." + StarminerAdditions.MOD_ID + ".gravity_generator.";

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Direction[] FACES = {Direction.UP, Direction.DOWN,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final DecimalFormat BLOCKS_FORMAT = new DecimalFormat("0.0");
    private static final DecimalFormat GRAVITY_FORMAT = new DecimalFormat("0.00");

    private final GravityGeneratorBlockEntity blockEntity;

    // Edited settings; dimensions in tenths of a block, target gravity in hundredths of a g
    private GeneratorFieldMode mode;
    private int radiusTenths;
    private int heightTenths;
    private int widthTenths;
    private int depthTenths;
    private double angleDegrees;
    private int sideIndex;
    private Direction face;
    private Direction.Axis axis;
    private boolean repel;
    private int gravityHundredths;
    private int visibility;

    /** Modifier state of the most recent press on a stepper button. */
    private int lastModifiers;

    private LinearLayout modeSection;
    private TextView gravityHint;
    private TextView costLabel;
    private TextView energyLabel;

    public GravityGeneratorFragment(GravityGeneratorBlockEntity blockEntity) {
        super(I18n.get(PREFIX + "title"));
        this.blockEntity = blockEntity;
        GravityGeneratorBlockEntity.Settings settings = blockEntity.getSettings();
        this.mode = settings.mode();
        this.radiusTenths = toUnits(settings.radius(), 10);
        this.heightTenths = toUnits(settings.height(), 10);
        this.widthTenths = toUnits(settings.width(), 10);
        this.depthTenths = toUnits(settings.depth(), 10);
        this.angleDegrees = settings.angleDegrees();
        this.face = settings.face();
        this.axis = settings.axis();
        this.repel = settings.repel();
        this.gravityHundredths = toUnits(settings.targetGravity(), 100);
        this.visibility = settings.visibility();
        this.sideIndex = 0;
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i] == settings.side()) {
                this.sideIndex = i;
            }
        }
    }

    @Override
    protected void onCreateTerminalContent(LinearLayout content) {
        Context context = content.getContext();

        Button modeButton = cycleButton(context, modeName(), v -> {
            mode = mode.next();
            ((Button) v).setText(modeName());
            rebuildModeSection();
        });
        addSettingRow(content, I18n.get(PREFIX + "mode"), modeButton, 0);

        modeSection = new LinearLayout(context);
        modeSection.setOrientation(LinearLayout.VERTICAL);
        content.addView(modeSection, matchWrap());

        addDivider(content, 12);

        int minGravity = toUnits(GravityGeneratorBlockEntity.MIN_TARGET_GRAVITY, 100);
        int maxGravity = toUnits(GravityGeneratorBlockEntity.MAX_TARGET_GRAVITY, 100);
        addStepperRow(content, "target_gravity", () -> gravityHundredths, v -> gravityHundredths = v,
                minGravity, maxGravity, 1, 10, 100, units -> GRAVITY_FORMAT.format(units / 100.0) + " g", 10);
        gravityHint = new TextView(context);
        gravityHint.setTextSize(11);
        gravityHint.setTextColor(OneDark.COMMENT);
        content.addView(gravityHint, matchWrapWithTop(3));

        Button visibilityButton = cycleButton(context, visibilityName(), v -> {
            visibility = (visibility + 1) % AbstractGravityFieldBlockEntity.VISIBILITY_MODES;
            ((Button) v).setText(visibilityName());
        });
        addSettingRow(content, I18n.get(PREFIX + "visibility"), visibilityButton, 8);

        addDivider(content, 12);

        energyLabel = new TextView(context);
        energyLabel.setTypeface(Typeface.MONOSPACED);
        energyLabel.setTextSize(12);
        content.addView(energyLabel, matchWrapWithTop(10));

        EnergyGaugeView gauge = new EnergyGaugeView(context,
                () -> blockEntity.getEnergyStorage().getStoredEnergy(),
                GravityGeneratorBlockEntity.ENERGY_CAPACITY,
                blockEntity::isPowered,
                this::updateEnergyReadouts);
        LinearLayout.LayoutParams gaugeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        gaugeParams.setMargins(0, dp(6), 0, 0);
        content.addView(gauge, gaugeParams);

        costLabel = new TextView(context);
        costLabel.setTextSize(11);
        costLabel.setTextColor(OneDark.COMMENT);
        content.addView(costLabel, matchWrapWithTop(5));

        // Built after the readouts: the mode rows refresh them as they're created
        rebuildModeSection();
        updateEnergyReadouts();

        addFooter(content, accentButton(context, I18n.get(PREFIX + "apply"), v ->
                applyAndClose(() -> ModNetwork.sendUpdateGravityGenerator(blockEntity.getBlockPos(),
                        currentSettings()))), 12);
    }

    // ------------------------------------------------------------------
    // Mode-specific rows
    // ------------------------------------------------------------------

    private void rebuildModeSection() {
        modeSection.removeAllViews();
        Context context = modeSection.getContext();
        int minRadius = toUnits(GravityGeneratorBlockEntity.MIN_FIELD_RADIUS, 10);
        int maxRadius = toUnits(GravityGeneratorBlockEntity.MAX_FIELD_RADIUS, 10);
        int minExtent = toUnits(GravityGeneratorBlockEntity.MIN_EXTENT, 10);
        int maxExtent = toUnits(GravityGeneratorBlockEntity.MAX_EXTENT, 10);

        switch (mode) {
            case DIRECTIONAL -> {
                addDimensionRow(modeSection, "radius", () -> radiusTenths, v -> radiusTenths = v,
                        minRadius, maxRadius, 8);
                addSettingRow(modeSection, I18n.get(PREFIX + "angle"), angleField(context), 8);
                Button sideButton = cycleButton(context, sideName(), v -> {
                    sideIndex = (sideIndex + 1) % SIDES.length;
                    ((Button) v).setText(sideName());
                });
                addSettingRow(modeSection, I18n.get(PREFIX + "side"), sideButton, 8);
            }
            case WALL -> {
                Button faceButton = cycleButton(context, directionName(face), v -> {
                    face = FACES[(indexOf(FACES, face) + 1) % FACES.length];
                    ((Button) v).setText(directionName(face));
                });
                addSettingRow(modeSection, I18n.get(PREFIX + "face"), faceButton, 8);
                addDimensionRow(modeSection, "height", () -> heightTenths, v -> heightTenths = v,
                        minExtent, maxExtent, 8);
                addDimensionRow(modeSection, "width", () -> widthTenths, v -> widthTenths = v,
                        minExtent, maxExtent, 6);
                addDimensionRow(modeSection, "depth", () -> depthTenths, v -> depthTenths = v,
                        minExtent, maxExtent, 6);
            }
            case CYLINDER -> {
                Button axisButton = cycleButton(context, axisName(), v -> {
                    axis = Direction.Axis.values()[(axis.ordinal() + 1) % Direction.Axis.values().length];
                    ((Button) v).setText(axisName());
                });
                addSettingRow(modeSection, I18n.get(PREFIX + "axis"), axisButton, 8);
                addDimensionRow(modeSection, "radius", () -> radiusTenths, v -> radiusTenths = v,
                        minRadius, maxRadius, 8);
                addDimensionRow(modeSection, "length", () -> widthTenths, v -> widthTenths = v,
                        minExtent, maxExtent, 6);
            }
            case PLANET -> addDimensionRow(modeSection, "radius", () -> radiusTenths, v -> radiusTenths = v,
                    minRadius, maxRadius, 8);
        }

        if (mode.hasPolarity()) {
            Button polarityButton = cycleButton(context, polarityName(), v -> {
                repel = !repel;
                ((Button) v).setText(polarityName());
            });
            addSettingRow(modeSection, I18n.get(PREFIX + "polarity"), polarityButton, 8);
        }
        onSettingsChanged();
    }

    private EditText angleField(Context context) {
        EditText field = termField(context, String.valueOf((int) angleDegrees));
        field.setFilters(DigitsInputFilter.getInstance(Locale.ROOT), new InputFilter.LengthFilter(3));
        field.setMinimumWidth(dp(64));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    angleDegrees = Double.parseDouble(s.toString());
                } catch (NumberFormatException ignored) {
                    // Keep the last valid angle while the field is empty
                }
            }
        });
        return field;
    }

    // ------------------------------------------------------------------
    // Steppers
    // ------------------------------------------------------------------

    /** A dimension stepper in tenths of a block (click = 1.0, shift = 0.1, ctrl = 10). */
    private void addDimensionRow(LinearLayout content, String labelKey, IntSupplier getTenths,
                                 IntConsumer setTenths, int minTenths, int maxTenths, int topDp) {
        addStepperRow(content, labelKey, getTenths, setTenths, minTenths, maxTenths, 1, 10, 100,
                tenths -> BLOCKS_FORMAT.format(tenths / 10.0), topDp);
    }

    /**
     * One stepper row: label left, a [−][value][+] stepper right. The click's modifiers
     * pick the step like amethyst: shift = fine, ctrl = coarse, plain = normal.
     */
    private void addStepperRow(LinearLayout content, String labelKey, IntSupplier get, IntConsumer set,
                               int min, int max, int fineStep, int normalStep, int coarseStep,
                               IntFunction<String> format, int topDp) {
        Context context = content.getContext();
        LinearLayout stepper = new LinearLayout(context);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        TextView value = new TextView(context);
        value.setTypeface(Typeface.MONOSPACED);
        value.setTextSize(13);
        value.setTextColor(OneDark.ORANGE);
        value.setGravity(Gravity.CENTER);
        value.setText(format.apply(get.getAsInt()));

        Button decrease = stepButton(context, I18n.get(PREFIX + "decrease"), () -> {
            set.accept(Math.max(get.getAsInt() - step(fineStep, normalStep, coarseStep), min));
            value.setText(format.apply(get.getAsInt()));
            onSettingsChanged();
        });
        Button increase = stepButton(context, I18n.get(PREFIX + "increase"), () -> {
            set.accept(Math.min(get.getAsInt() + step(fineStep, normalStep, coarseStep), max));
            value.setText(format.apply(get.getAsInt()));
            onSettingsChanged();
        });

        stepper.addView(decrease, wrapWrap());
        stepper.addView(value, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        stepper.addView(increase, wrapWrap());

        addSettingRow(content, I18n.get(PREFIX + labelKey), stepper, topDp);
    }

    private Button stepButton(Context context, String glyph, Runnable action) {
        Button button = termButton(context, glyph, v -> action.run());
        button.setPadding(0, dp(2), 0, dp(2));
        button.setMinimumWidth(dp(26));
        button.setMinimumHeight(dp(22));
        // Click listeners carry no modifier state, so capture it from the press itself
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastModifiers = event.getModifiers();
            }
            return false;
        });
        return button;
    }

    private int step(int fine, int normal, int coarse) {
        if ((lastModifiers & KeyEvent.META_SHIFT_ON) != 0) {
            return fine;
        }
        if ((lastModifiers & KeyEvent.META_CTRL_ON) != 0) {
            return coarse;
        }
        return normal;
    }

    private Button cycleButton(Context context, String text, View.OnClickListener onClick) {
        Button button = termButton(context, text, onClick);
        button.setMinimumWidth(dp(96));
        return button;
    }

    // ------------------------------------------------------------------
    // Readouts
    // ------------------------------------------------------------------

    private GravityGeneratorBlockEntity.Settings currentSettings() {
        return new GravityGeneratorBlockEntity.Settings(mode,
                radiusTenths / 10.0, heightTenths / 10.0, widthTenths / 10.0, depthTenths / 10.0,
                SIDES[sideIndex], angleDegrees, face, axis, repel, gravityHundredths / 100.0, visibility);
    }

    private void onSettingsChanged() {
        double volume = GravityGeneratorBlockEntity.fieldVolume(mode,
                radiusTenths / 10.0, heightTenths / 10.0, widthTenths / 10.0, depthTenths / 10.0);
        costLabel.setText(I18n.get(PREFIX + "cost", GravityGeneratorBlockEntity.energyCostPerTick(volume)));
        gravityHint.setText(I18n.get(PREFIX + "target_gravity.hint",
                GRAVITY_FORMAT.format(gravityHundredths / 100.0 * GravityGeneratorBlockEntity.STANDARD_GRAVITY)));
    }

    /** Refresh the energy line; driven by the gauge's ticker. */
    private void updateEnergyReadouts() {
        energyLabel.setText(I18n.get(PREFIX + "energy",
                blockEntity.getEnergyStorage().getStoredEnergy(), GravityGeneratorBlockEntity.ENERGY_CAPACITY));
        energyLabel.setTextColor(blockEntity.isPowered() ? OneDark.GREEN : OneDark.RED);
    }

    // ------------------------------------------------------------------
    // Names + helpers
    // ------------------------------------------------------------------

    private static int toUnits(double value, int unitsPerOne) {
        return (int) Math.round(value * unitsPerOne);
    }

    private static int indexOf(Direction[] directions, Direction direction) {
        for (int i = 0; i < directions.length; i++) {
            if (directions[i] == direction) {
                return i;
            }
        }
        return 0;
    }

    private String modeName() {
        return I18n.get(PREFIX + "mode." + mode.getSerializedName());
    }

    private String sideName() {
        return directionName(SIDES[sideIndex]);
    }

    private String directionName(Direction direction) {
        return I18n.get(PREFIX + "side." + direction.getName());
    }

    private String axisName() {
        return I18n.get(PREFIX + "axis." + axis.getName());
    }

    private String polarityName() {
        return I18n.get(PREFIX + (repel ? "repel" : "attract"));
    }

    private String visibilityName() {
        return I18n.get(PREFIX + switch (visibility) {
            case AbstractGravityFieldBlockEntity.VISIBILITY_ALWAYS -> "visibility.always";
            case AbstractGravityFieldBlockEntity.VISIBILITY_NEVER -> "visibility.never";
            default -> "visibility.with_glasses";
        });
    }
}
