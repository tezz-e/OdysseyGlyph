package com.example.odysseyglyph

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * NothingSpotlightOverlay
 *
 * An authentic, hardware-aligned interactive spotlight tutorial overlay for Glyph Odyssey.
 * Implements fluid morphing cutouts, specular Nothing Red glowing rims, floating frosted glass
 * tooltips with directional chevron arrows, animated stadium indicator dots, and subtle haptics.
 */
@SuppressLint("ViewConstructor")
class NothingSpotlightOverlay private constructor(
    context: Context,
    private val scrollView: ScrollView?
) : FrameLayout(context) {

    data class Step(
        val targetView: View,
        val title: String,
        val description: String,
        val cornerRadiusDp: Float = 22f,
        val paddingDp: Float = 6f
    )

    private val steps = mutableListOf<Step>()
    private var currentStepIndex = -1
    private var onCompletedAction: (() -> Unit)? = null
    private var onDismissedAction: (() -> Unit)? = null

    // Canvas Paints
    private val density = resources.displayMetrics.density
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6050507") // 90% obsidian glass scrim
    }
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.parseColor("#EA3323") // Nothing Red
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        color = Color.parseColor("#44EA3323") // Glowing red halo
    }
    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#141418")
    }
    private val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.parseColor("#33EA3323")
    }

    // Geometry state for cutout morphing
    private val currentRect = RectF()
    private val fromRect = RectF()
    private val toRect = RectF()
    private var currentRadius = 22f * density
    private var fromRadius = currentRadius
    private var toRadius = currentRadius

    // Arrow geometry
    private val arrowPath = Path()
    private var isArrowPointingUp = true
    private var arrowTipX = 0f
    private var arrowTipY = 0f
    private var arrowBaseY = 0f

    // Animators
    private var morphAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private val fluidInterpolator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f) // Fluid Apple/Nothing bezier curve
    } else {
        DecelerateInterpolator()
    }

    // Tooltip View bindings
    private val tooltipRoot: View
    private val tooltipCard: View
    private val tooltipContent: View
    private val tvStep: TextView
    private val tvTitle: TextView
    private val tvDesc: TextView
    private val btnSkip: View
    private val btnNext: MaterialButton
    private val llDots: LinearLayout

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isFocusable = true
        isFocusableInTouchMode = true

        val inflater = LayoutInflater.from(context)
        tooltipRoot = inflater.inflate(R.layout.layout_spotlight_tooltip, this, false)
        addView(tooltipRoot)

        tooltipCard = tooltipRoot.findViewById(R.id.tooltipCard)
        tooltipContent = tooltipRoot.findViewById(R.id.tooltipContent)
        tvStep = tooltipRoot.findViewById(R.id.tvSpotlightStep)
        tvTitle = tooltipRoot.findViewById(R.id.tvSpotlightTitle)
        tvDesc = tooltipRoot.findViewById(R.id.tvSpotlightDesc)
        btnSkip = tooltipRoot.findViewById(R.id.btnSpotlightSkip)
        btnNext = tooltipRoot.findViewById(R.id.btnSpotlightNext)
        llDots = tooltipRoot.findViewById(R.id.llSpotlightDots)

        btnSkip.setOnClickListener {
            performSafeHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
            dismiss(completed = false)
        }

        btnNext.setOnClickListener {
            performSafeHaptic(HapticFeedbackConstants.CLOCK_TICK)
            if (currentStepIndex < steps.size - 1) {
                goToStep(currentStepIndex + 1)
            } else {
                dismiss(completed = true)
            }
        }

        // Tapping the cutout also advances to next step
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (currentRect.contains(event.x, event.y)) {
                    performSafeHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    if (currentStepIndex < steps.size - 1) {
                        goToStep(currentStepIndex + 1)
                    } else {
                        dismiss(completed = true)
                    }
                    return@setOnTouchListener true
                }
            }
            true // Consume all background touches
        }

        startPulseAnimation()
    }

    private fun performSafeHaptic(feedbackConstant: Int) {
        try {
            performHapticFeedback(feedbackConstant)
        } catch (_: Exception) {}
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.30f, 0.85f).apply {
            duration = 1100
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val alphaVal = va.animatedValue as Float
                glowPaint.alpha = (alphaVal * 255 * 0.40f).toInt()
                invalidate()
            }
            start()
        }
    }

    fun addStep(step: Step): NothingSpotlightOverlay {
        steps.add(step)
        return this
    }

    fun setOnCompleted(action: () -> Unit): NothingSpotlightOverlay {
        onCompletedAction = action
        return this
    }

    fun setOnDismissed(action: () -> Unit): NothingSpotlightOverlay {
        onDismissedAction = action
        return this
    }

    fun start() {
        if (steps.isEmpty()) return
        initDots()
        post {
            goToStep(0)
        }
    }

    private fun initDots() {
        llDots.removeAllViews()
        for (i in steps.indices) {
            val dot = View(context).apply {
                val lp = LinearLayout.LayoutParams(
                    (6 * density).toInt(),
                    (6 * density).toInt()
                ).apply {
                    marginEnd = (5 * density).toInt()
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 3 * density
                    setColor(Color.parseColor("#33FFFFFF"))
                }
            }
            llDots.addView(dot)
        }
    }

    private fun updateDots(activeIndex: Int, oldIndex: Int) {
        for (i in 0 until llDots.childCount) {
            val dot = llDots.getChildAt(i) ?: continue
            val bg = dot.background as? GradientDrawable ?: continue

            if (i == activeIndex) {
                // Animate expansion to active stadium pill
                animateDotWidth(dot, (6 * density).toInt(), (18 * density).toInt())
                bg.setColor(Color.parseColor("#EA3323")) // Nothing Red
            } else if (i == oldIndex) {
                // Animate contraction back to small circle
                animateDotWidth(dot, (18 * density).toInt(), (6 * density).toInt())
                bg.setColor(Color.parseColor("#33FFFFFF"))
            } else {
                val lp = dot.layoutParams
                lp.width = (6 * density).toInt()
                dot.layoutParams = lp
                bg.setColor(Color.parseColor("#33FFFFFF"))
            }
        }
    }

    private fun animateDotWidth(dot: View, fromWidth: Int, toWidth: Int) {
        ValueAnimator.ofInt(fromWidth, toWidth).apply {
            duration = 240
            interpolator = fluidInterpolator
            addUpdateListener { va ->
                val lp = dot.layoutParams
                lp.width = va.animatedValue as Int
                dot.layoutParams = lp
            }
            start()
        }
    }

    private fun goToStep(newIndex: Int) {
        if (newIndex !in steps.indices) {
            dismiss(completed = true)
            return
        }

        val step = steps[newIndex]
        val targetView = step.targetView

        if (!targetView.isLaidOut) {
            targetView.post { goToStep(newIndex) }
            return
        }

        // Smooth scroll if target is partially off-screen
        scrollView?.let { sv ->
            val viewRect = Rect()
            targetView.getDrawingRect(viewRect)
            try {
                sv.offsetDescendantRectToMyCoords(targetView, viewRect)
                val svHeight = sv.height
                val targetScrollY = (viewRect.centerY() - svHeight / 2).coerceAtLeast(0)
                if (Math.abs(sv.scrollY - targetScrollY) > 30) {
                    sv.smoothScrollTo(0, targetScrollY)
                }
            } catch (_: Exception) {}
        }

        val loc = IntArray(2)
        targetView.getLocationInWindow(loc)
        val pad = step.paddingDp * density
        val targetRect = RectF(
            loc[0] - pad,
            loc[1] - pad,
            loc[0] + targetView.width + pad,
            loc[1] + targetView.height + pad
        )
        val targetRadius = step.cornerRadiusDp * density

        val oldIndex = currentStepIndex
        currentStepIndex = newIndex

        if (oldIndex == -1) {
            // First time entrance
            currentRect.set(targetRect)
            currentRadius = targetRadius
            bindContent(step, newIndex)
            updateDots(newIndex, -1)
            positionTooltip(targetRect, immediate = true)

            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            // Morphing transition between steps
            morphAnimator?.cancel()
            fromRect.set(currentRect)
            toRect.set(targetRect)
            fromRadius = currentRadius
            toRadius = targetRadius

            updateDots(newIndex, oldIndex)
            animateTooltipTransition(step, newIndex, targetRect)

            morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 380
                interpolator = fluidInterpolator
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    currentRect.left = lerp(fromRect.left, toRect.left, f)
                    currentRect.top = lerp(fromRect.top, toRect.top, f)
                    currentRect.right = lerp(fromRect.right, toRect.right, f)
                    currentRect.bottom = lerp(fromRect.bottom, toRect.bottom, f)
                    currentRadius = lerp(fromRadius, toRadius, f)
                    updateArrowGeometry()
                    invalidate()
                }
                start()
            }
        }
    }

    private fun bindContent(step: Step, index: Int) {
        tvStep.text = "STEP ${index + 1} OF ${steps.size}"
        tvTitle.text = step.title
        tvDesc.text = step.description

        if (index == steps.size - 1) {
            btnNext.text = "GET STARTED"
            btnNext.setIconResource(0)
        } else {
            btnNext.text = "NEXT"
            btnNext.setIconResource(R.drawable.ic_chevron_right)
        }
    }

    private fun animateTooltipTransition(step: Step, newIndex: Int, targetRect: RectF) {
        tooltipContent.animate()
            .alpha(0f)
            .translationY(-8f * density)
            .setDuration(120)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                bindContent(step, newIndex)
                positionTooltip(targetRect, immediate = false)

                tooltipContent.translationY = 8f * density
                tooltipContent.scaleX = 0.96f
                tooltipContent.scaleY = 0.96f
                tooltipContent.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun positionTooltip(targetRect: RectF, immediate: Boolean) {
        tooltipRoot.post {
            val tooltipH = tooltipCard.height.toFloat().coerceAtLeast(140f * density)
            val screenH = height.toFloat().coerceAtLeast(1000f * density)

            // If the target view is in the lower half of the screen, place tooltip ABOVE it!
            // This prevents crowding against the system navigation bar.
            val placeBelow = targetRect.centerY() <= screenH * 0.52f
            isArrowPointingUp = placeBelow

            val targetY = if (placeBelow) {
                targetRect.bottom + 12f * density - tooltipCard.top
            } else {
                targetRect.top - tooltipH - 12f * density - tooltipCard.top
            }

            if (immediate) {
                tooltipRoot.translationY = targetY
                updateArrowGeometry()
                invalidate()
            } else {
                tooltipRoot.animate()
                    .translationY(targetY)
                    .setDuration(360)
                    .setInterpolator(fluidInterpolator)
                    .setUpdateListener {
                        updateArrowGeometry()
                        invalidate()
                    }
                    .start()
            }
        }
    }

    private fun updateArrowGeometry() {
        val cutoutCenterX = currentRect.centerX()
        val cardLeft = tooltipCard.left + 24f * density
        val cardRight = tooltipCard.right - 24f * density

        arrowTipX = cutoutCenterX.coerceIn(cardLeft, cardRight)
        val arrowHalfW = 9f * density

        arrowPath.reset()
        if (isArrowPointingUp) {
            // Tooltip is below cutout: arrow tip points UP to cutout bottom
            val tipY = currentRect.bottom + 2f * density
            val baseY = tooltipRoot.translationY + tooltipCard.top

            arrowPath.moveTo(arrowTipX, tipY)
            arrowPath.lineTo(arrowTipX - arrowHalfW, baseY)
            arrowPath.lineTo(arrowTipX + arrowHalfW, baseY)
            arrowPath.close()
        } else {
            // Tooltip is above cutout: arrow tip points DOWN to cutout top
            val tipY = currentRect.top - 2f * density
            val baseY = tooltipRoot.translationY + tooltipCard.bottom

            arrowPath.moveTo(arrowTipX, tipY)
            arrowPath.lineTo(arrowTipX - arrowHalfW, baseY)
            arrowPath.lineTo(arrowTipX + arrowHalfW, baseY)
            arrowPath.close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw darkened obsidian scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (currentRect.width() > 0 && currentRect.height() > 0) {
            // 2. Punch clear spotlight cutout
            canvas.drawRoundRect(currentRect, currentRadius, currentRadius, cutoutPaint)

            // 3. Draw glowing diffuse Nothing Red halo
            canvas.drawRoundRect(currentRect, currentRadius, currentRadius, glowPaint)

            // 4. Draw crisp specular Nothing Red hairline border
            canvas.drawRoundRect(currentRect, currentRadius, currentRadius, rimPaint)

            // 5. Draw connecting arrow triangle
            if (!arrowPath.isEmpty) {
                canvas.drawPath(arrowPath, arrowFillPaint)
                canvas.drawPath(arrowPath, arrowStrokePaint)
            }
        }
    }

    fun dismiss(completed: Boolean = true) {
        pulseAnimator?.cancel()
        morphAnimator?.cancel()

        animate()
            .alpha(0f)
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(240)
            .setInterpolator(fluidInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val parentGroup = parent as? ViewGroup
                    parentGroup?.removeView(this@NothingSpotlightOverlay)
                    if (completed) {
                        onCompletedAction?.invoke()
                    } else {
                        onDismissedAction?.invoke()
                    }
                }
            })
            .start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            dismiss(completed = false)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        morphAnimator?.cancel()
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    companion object {
        fun attach(activity: Activity, scrollView: ScrollView? = null): NothingSpotlightOverlay {
            val overlay = NothingSpotlightOverlay(activity, scrollView)
            val decorView = activity.window.decorView as ViewGroup
            val lp = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            decorView.addView(overlay, lp)
            return overlay
        }
    }
}
