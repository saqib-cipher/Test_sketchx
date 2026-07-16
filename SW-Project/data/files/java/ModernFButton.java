package com.my.Mbutton;
// change to your own package
//Made by SaqibCipher #GrafixLab
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;

public class ModernFButton extends Button {

	// ---- Hardcoded defaults (no resource lookups required) ----
	private static final int DEFAULT_BUTTON_COLOR = Color.parseColor("#4CAF50");
	private static final float DEFAULT_SHADOW_HEIGHT_DP = 4f;
	private static final float DEFAULT_CORNER_RADIUS_DP = 8f;
	private static final int DEFAULT_RIPPLE_COLOR = Color.argb(70, 255, 255, 255);

	// ---- Icon gravity (single source of truth — merged with the old IconPosition enum) ----
	public static final int ICON_GRAVITY_TEXT_START = 0; // icon+text centered together, icon before text
	public static final int ICON_GRAVITY_TEXT_END = 1;   // icon+text centered together, icon after text
	public static final int ICON_GRAVITY_TOP = 2;
	public static final int ICON_GRAVITY_BOTTOM = 3;
	public static final int ICON_GRAVITY_EDGE_START = 4; // icon pinned to left edge, text stays centered
	public static final int ICON_GRAVITY_EDGE_END = 5;   // icon pinned to right edge, text stays centered

	private int iconGravity = ICON_GRAVITY_TEXT_START;

	private int buttonColor = DEFAULT_BUTTON_COLOR;
	private int shadowColor;
	private boolean shadowColorDefined = false;
	private int shadowHeightPx;
	private int cornerRadiusPx;
	private boolean shadowEnabled = true;
	private boolean rippleEnabled = true;
	private int rippleColor = DEFAULT_RIPPLE_COLOR;
	private int iconExtraPadding = 0; // extra left/right padding to center icon+text group
	private int basePaddingLeft, basePaddingRight, basePaddingTop, basePaddingBottom;

	// Precomputed press-state drawables, rebuilt in refresh()
	private Drawable normalBackground; // full shadow
	private Drawable pressedBackground; // shadow collapsed to zero
	private boolean isPressed = false;

	// ---- Icon support ----
	private Drawable iconDrawable;
	private int iconSizePx = 0; // 0 = use the drawable's own intrinsic size
	private int iconPaddingPx;
	private Integer iconTintColor = null; // null = no tint applied
	private Drawable edgeIconDrawable; // set only when gravity is EDGE_START/EDGE_END; drawn manually in onDraw

	// ---- Loading indicator ----
	private boolean loading = false;
	private CharSequence textBeforeLoading;
	private SpinnerDrawable spinnerDrawable;
	private ValueAnimator loadingAnimator;
	private int loadingColor = Color.WHITE;
	private int loadingSizePx;
	private int loadingStrokeWidthPx;

	public ModernFButton(Context context) {
		super(context);
		init();
	}

	public ModernFButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public ModernFButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		float density = getResources().getDisplayMetrics().density;
		shadowHeightPx = Math.round(DEFAULT_SHADOW_HEIGHT_DP * density);
		cornerRadiusPx = Math.round(DEFAULT_CORNER_RADIUS_DP * density);
		iconPaddingPx = Math.round(8f * density); // 8dp default gap between icon and text
		loadingSizePx = Math.round(20f * density);
		loadingStrokeWidthPx = Math.round(2.5f * density);

		basePaddingLeft = getPaddingLeft();
		basePaddingRight = getPaddingRight();
		basePaddingTop = getPaddingTop();
		basePaddingBottom = getPaddingBottom();

		setAllCaps(false);

		// Classic FButton press effect: shadow collapses to zero on press,
		// button "sinks" down flush with the surface, then pops back up on release.
		setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (loading) return false; // no press feedback while loading
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
					press(true);
					break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
					case MotionEvent.ACTION_OUTSIDE:
					press(false);
					break;
				}
				return false;
			}
		});

		refresh();
	}

	/** Rebuilds normal/pressed background drawables and padding from current field values. */
	public void refresh() {
		if (!shadowColorDefined) {
			float[] hsv = new float[3];
			Color.colorToHSV(buttonColor, hsv);
			hsv[2] *= 0.8f; // 80% brightness for auto shadow color
			shadowColor = Color.HSVToColor(Color.alpha(buttonColor), hsv);
		}

		boolean enabled = isEnabled();
		int mainColor = enabled ? buttonColor : disabledTint(buttonColor);
		boolean showShadow = shadowEnabled && enabled;

		Drawable face = buildFace(mainColor);
		normalBackground = wrapRipple(showShadow ? buildShadowed(face, mainColor) : face);

		// Pressed state: same face, shadow collapsed to zero -> button sits flush.
		Drawable pressedFace = buildFace(mainColor);
		pressedBackground = wrapRipple(pressedFace);

		setBackground(isPressed ? pressedBackground : normalBackground);

		applyPadding();

		if (!loading) {
			applyIcon();
		}
	}

	/** Rebuilds and positions the icon based on current icon gravity. */
	private void applyIcon() {
		setCompoundDrawablePadding(iconPaddingPx);

		Drawable icon = iconDrawable;
		if (icon != null) {
			icon = icon.mutate();
			if (iconTintColor != null) {
				icon.setTint(iconTintColor);
			}
			int w = iconSizePx > 0 ? iconSizePx : icon.getIntrinsicWidth();
			int h = iconSizePx > 0 ? iconSizePx : icon.getIntrinsicHeight();
			icon.setBounds(0, 0, w, h);
		}

		boolean isEdgeStart = iconGravity == ICON_GRAVITY_EDGE_START;
		boolean isEdgeEnd = iconGravity == ICON_GRAVITY_EDGE_END;

		if (isEdgeStart || isEdgeEnd) {
			// Edge mode: icon is pinned to the button's edge and drawn manually in onDraw().
			// Text is NOT pushed by a compound drawable, so it stays truly centered.
			setCompoundDrawablesRelative(null, null, null, null);
			edgeIconDrawable = icon;
			setGravity(Gravity.CENTER);
			iconExtraPadding = 0;
			applyPadding();
			invalidate();
			return;
		}

		edgeIconDrawable = null;

		boolean isStart = iconGravity == ICON_GRAVITY_TEXT_START;
		boolean isEnd = iconGravity == ICON_GRAVITY_TEXT_END;
		boolean isTop = iconGravity == ICON_GRAVITY_TOP;
		boolean isBottom = iconGravity == ICON_GRAVITY_BOTTOM;

		Drawable start = icon != null && isStart ? icon : null;
		Drawable end = icon != null && isEnd ? icon : null;
		Drawable top = icon != null && isTop ? icon : null;
		Drawable bottom = icon != null && isBottom ? icon : null;

		setCompoundDrawablesRelative(start, top, end, bottom);

		final Drawable finalIcon = icon;
		if (finalIcon != null && (isStart || isEnd)) {
			setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			post(new Runnable() {
				@Override public void run() {
					int viewWidth = getWidth();
					CharSequence text = getText();
					if (viewWidth <= 0 || text == null) return;

					int textWidth = (int) Math.ceil(getPaint().measureText(text, 0, text.length()));
					int iconWidth = finalIcon.getBounds().width();
					int contentWidth = textWidth + iconWidth + iconPaddingPx;
					int available = viewWidth - basePaddingLeft - basePaddingRight;
					iconExtraPadding = Math.max(0, (available - contentWidth) / 2);

					applyPadding(); // re-apply combined padding now that we know the extra
				}
			});
		} else {
			setGravity(Gravity.CENTER);
			iconExtraPadding = 0;
			applyPadding();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (loading && spinnerDrawable != null) {
			int iconW = spinnerDrawable.getBounds().width();
			int iconH = spinnerDrawable.getBounds().height();
			int contentTop = getPaddingTop();
			int contentBottom = getHeight() - getPaddingBottom();
			int centerX = getWidth() / 2;
			int centerY = contentTop + (contentBottom - contentTop) / 2;

			canvas.save();
			canvas.translate(centerX - iconW / 2f, centerY - iconH / 2f);
			spinnerDrawable.draw(canvas);
			canvas.restore();
			return;
		}

		if (edgeIconDrawable == null) return;

		int iconW = edgeIconDrawable.getBounds().width();
		int iconH = edgeIconDrawable.getBounds().height();

		// Vertically center within the current content box (naturally excludes
		// the bottom shadow sliver since that's already baked into padding).
		int contentTop = getPaddingTop();
		int contentBottom = getHeight() - getPaddingBottom();
		int top = contentTop + ((contentBottom - contentTop) - iconH) / 2;

		int left = iconGravity == ICON_GRAVITY_EDGE_START
			? basePaddingLeft
			: getWidth() - basePaddingRight - iconW;

		canvas.save();
		canvas.translate(left, top);
		edgeIconDrawable.draw(canvas);
		canvas.restore();
	}

	/** Central place that combines base padding + icon centering extra + shadow bottom extra. */
	private void applyPadding() {
		boolean showShadow = shadowEnabled && isEnabled();
		int bottomExtra = showShadow && !isPressed ? shadowHeightPx : 0;
		setPadding(
			basePaddingLeft + iconExtraPadding,
			basePaddingTop,
			basePaddingRight + iconExtraPadding,
			basePaddingBottom + bottomExtra
		);
	}

	private GradientDrawable buildFace(int color) {
		GradientDrawable face = new GradientDrawable();
		face.setShape(GradientDrawable.RECTANGLE);
		face.setCornerRadius(cornerRadiusPx);
		face.setColor(color);
		return face;
	}

	private Drawable buildShadowed(Drawable face, int unusedColor) {
		GradientDrawable shadow = new GradientDrawable();
		shadow.setShape(GradientDrawable.RECTANGLE);
		shadow.setCornerRadius(cornerRadiusPx);
		shadow.setColor(shadowColor);

		LayerDrawable layered = new LayerDrawable(new Drawable[]{shadow, face});
		layered.setLayerInset(1, 0, 0, 0, shadowHeightPx); // lift the face off the shadow
		return layered;
	}

	private Drawable wrapRipple(Drawable base) {
		if (!rippleEnabled) return base;
		return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null);
	}

	/** Toggles between the full-shadow and shadow-collapsed (pressed) look. */
	private void press(boolean pressed) {
		if (this.isPressed == pressed) return;
		this.isPressed = pressed;

		setBackground(pressed ? pressedBackground : normalBackground);
		applyPadding();
	}

	private int disabledTint(int color) {
		float[] hsv = new float[3];
		Color.colorToHSV(color, hsv);
		hsv[1] *= 0.25f; // desaturate
		return Color.HSVToColor(Color.alpha(color), hsv);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		refresh();
	}

	// ---------------- Chainable setters ----------------

	public ModernFButton setButtonColor(int color) {
		this.buttonColor = color;
		refresh();
		return this;
	}

	public ModernFButton setShadowColor(int color) {
		this.shadowColor = color;
		this.shadowColorDefined = true;
		refresh();
		return this;
	}

	public ModernFButton setShadowHeightPx(int px) {
		this.shadowHeightPx = px;
		refresh();
		return this;
	}

	public ModernFButton setShadowHeightDp(float dp) {
		this.shadowHeightPx = Math.round(dp * getResources().getDisplayMetrics().density);
		refresh();
		return this;
	}

	public ModernFButton setShadowEnabled(boolean enabled) {
		this.shadowEnabled = enabled;
		refresh();
		return this;
	}

	public ModernFButton setCornerRadiusPx(int px) {
		this.cornerRadiusPx = px;
		refresh();
		return this;
	}

	public ModernFButton setCornerRadiusDp(float dp) {
		this.cornerRadiusPx = Math.round(dp * getResources().getDisplayMetrics().density);
		refresh();
		return this;
	}

	public ModernFButton setRippleEnabled(boolean enabled) {
		this.rippleEnabled = enabled;
		refresh();
		return this;
	}

	public ModernFButton setRippleColor(int color) {
		this.rippleColor = color;
		refresh();
		return this;
	}

	// ---------------- Icon setters ----------------

	/** Set the icon from a Drawable directly. Pass null to remove the icon. */
	public ModernFButton setIcon(Drawable drawable) {
		this.iconDrawable = drawable;
		if (!loading) applyIcon();
		return this;
	}

	/** Set the icon from a drawable resource id. Pass 0 to remove the icon. */
	public ModernFButton setIcon(int drawableResId) {
		this.iconDrawable = drawableResId == 0 ? null : getContext().getDrawable(drawableResId);
		if (!loading) applyIcon();
		return this;
	}

	public ModernFButton setIconSizePx(int px) {
		this.iconSizePx = px;
		if (!loading) applyIcon();
		return this;
	}

	public ModernFButton setIconSizeDp(float dp) {
		this.iconSizePx = Math.round(dp * getResources().getDisplayMetrics().density);
		if (!loading) applyIcon();
		return this;
	}

	public ModernFButton setIconPaddingPx(int px) {
		this.iconPaddingPx = px;
		if (!loading) applyIcon();
		return this;
	}

	public ModernFButton setIconPaddingDp(float dp) {
		this.iconPaddingPx = Math.round(dp * getResources().getDisplayMetrics().density);
		if (!loading) applyIcon();
		return this;
	}

	/** Tints the icon with the given color. Call clearIconTint() to remove. */
	public ModernFButton setIconTint(int color) {
		this.iconTintColor = color;
		if (!loading) applyIcon();
		return this;
	}

	public ModernFButton clearIconTint() {
		this.iconTintColor = null;
		if (!loading) applyIcon();
		return this;
	}

	/** Single, merged control for icon placement — replaces the old separate IconPosition enum. */
	public ModernFButton setIconGravity(int gravity) {
		this.iconGravity = gravity;
		if (!loading) applyIcon();
		return this;
	}

	public int getIconGravity() {
		return iconGravity;
	}

	public ModernFButton setFButtonPadding(int left, int top, int right, int bottom) {
		this.basePaddingLeft = left;
		this.basePaddingTop = top;
		this.basePaddingRight = right;
		this.basePaddingBottom = bottom;
		refresh();
		return this;
	}

	// ---------------- Loading indicator ----------------

	/** Optional: customize the spinner color before calling setLoading(true). */
	public ModernFButton setLoadingColor(int color) {
		this.loadingColor = color;
		if (spinnerDrawable != null) spinnerDrawable.setColor(color);
		return this;
	}

	public ModernFButton setLoadingSizeDp(float dp) {
		this.loadingSizePx = Math.round(dp * getResources().getDisplayMetrics().density);
		if (spinnerDrawable != null) spinnerDrawable.setBounds(0, 0, loadingSizePx, loadingSizePx);
		return this;
	}

	/**
	 * Toggles a spinning loading indicator in place of the button's text/icon.
	 * The button becomes non-clickable while loading; original text and icon
	 * are restored automatically when you call setLoading(false).
	 */
	public ModernFButton setLoading(boolean loading) {
		if (this.loading == loading) return this;
		this.loading = loading;

		if (loading) {
			textBeforeLoading = getText();
			setText("");
			setClickable(false);
			edgeIconDrawable = null;
			setCompoundDrawablesRelative(null, null, null, null); // no compound drawable: keeps button's shape/height unchanged

			if (spinnerDrawable == null) {
				spinnerDrawable = new SpinnerDrawable(loadingColor, loadingStrokeWidthPx);
			}
			spinnerDrawable.setColor(loadingColor);
			spinnerDrawable.setBounds(0, 0, loadingSizePx, loadingSizePx);

			iconExtraPadding = 0;
			applyPadding();
			invalidate();

			loadingAnimator = ValueAnimator.ofFloat(0f, 360f);
			loadingAnimator.setDuration(700);
			loadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
			loadingAnimator.setInterpolator(new LinearInterpolator());
			loadingAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override public void onAnimationUpdate(ValueAnimator animation) {
					spinnerDrawable.setRotation((float) animation.getAnimatedValue());
					invalidate(); // spinnerDrawable isn't attached as a callback target, so drive redraw manually
				}
			});
			loadingAnimator.start();
		} else {
			if (loadingAnimator != null) {
				loadingAnimator.cancel();
				loadingAnimator = null;
			}
			setClickable(true);
			setText(textBeforeLoading);
			applyIcon(); // restores original icon/text layout
		}
		return this;
	}

	public boolean isLoading() {
		return loading;
	}

	/** Minimal self-contained spinning-arc drawable, no external assets required. */
	private static class SpinnerDrawable extends Drawable {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF bounds = new RectF();
		private float sweepAngle = 270f;
		private float rotation = 0f;

		SpinnerDrawable(int color, int strokeWidthPx) {
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(strokeWidthPx);
			paint.setStrokeCap(Paint.Cap.ROUND);
			paint.setColor(color);
		}

		void setColor(int color) {
			paint.setColor(color);
			invalidateSelf();
		}

		void setRotation(float degrees) {
			this.rotation = degrees;
			invalidateSelf();
		}

		@Override
		public void draw(Canvas canvas) {
			bounds.set(getBounds());
			float inset = paint.getStrokeWidth() / 2f;
			bounds.inset(inset, inset);
			canvas.save();
			canvas.rotate(rotation, bounds.centerX(), bounds.centerY());
			canvas.drawArc(bounds, 0, sweepAngle, false, paint);
			canvas.restore();
		}

		@Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
		@Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	//Made by SaqibCipher #GrafixLab
	// ---------------- Getters ----------------

	public int getButtonColor() { return buttonColor; }
	public int getShadowColor() { return shadowColor; }
	public int getShadowHeight() { return shadowHeightPx; }
	public int getCornerRadius() { return cornerRadiusPx; }
	public boolean isShadowEnabled() { return shadowEnabled; }
	public boolean isRippleEnabled() { return rippleEnabled; }
	public Drawable getIcon() { return iconDrawable; }
	public int getIconSize() { return iconSizePx; }
	public int getIconPadding() { return iconPaddingPx; }
}
