package com.example.pmmovil.agent

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.example.pmmovil.agent.update.AgentStateStore
import com.example.pmmovil.agent.update.AgentWorkScheduler
import kotlin.math.roundToInt

class AgentActivity : Activity() {

    private lateinit var statusCard: LinearLayout
    private lateinit var statusBadgeContainer: LinearLayout
    private lateinit var statusBadgeIcon: ImageView
    private lateinit var statusBadge: TextView
    private lateinit var statusTitle: TextView
    private lateinit var chipVersionValue: TextView
    private lateinit var chipCheckValue: TextView
    private lateinit var resultIcon: ImageView
    private lateinit var statusResult: TextView
    private lateinit var errorText: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var checkButton: Button
    private lateinit var openButton: Button

    private var badgeLoopAnimator: Animator? = null
    private var lastStateKind: StateKind? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusRefreshTask = object : Runnable {
        override fun run() {
            if (!::statusTitle.isInitialized) return
            refreshStatus()
            if (isWorkerActive()) {
                mainHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSystemBars()
        setContentView(buildContent())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusTitle.isInitialized) {
            refreshStatus()
            scheduleStatusRefresh()
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(statusRefreshTask)
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(statusRefreshTask)
        badgeLoopAnimator?.cancel()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val palette = currentPalette()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(21))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.header, palette.headerSecondary)
            )
        }

        val appIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_agent)
            contentDescription = getString(R.string.agent_app_description)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = circleDrawable(Color.argb(46, 255, 255, 255))
        }
        header.addView(appIcon, LinearLayout.LayoutParams(dp(64), dp(64)))

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        headerText.addView(textView(getString(R.string.agent_app_name), 25f, Color.WHITE, true))
        headerText.addView(
            textView(getString(R.string.agent_header_subtitle), 15f, Color.argb(225, 255, 255, 255), false)
        )
        header.addView(
            headerText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val headerWrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headerWrapper.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        headerWrapper.addView(
            View(this).apply { setBackgroundColor(PMM_YELLOW) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        )
        content.addView(headerWrapper)

        // Android 15+ (targetSdk 35+) dibuja el contenido detrás de las barras del
        // sistema por defecto (edge-to-edge obligatorio, ya no se puede desactivar).
        // Sin esto la barra de estado tapa el header: empujamos el header hacia abajo
        // el alto exacto de la barra de estado, y dejamos que su fondo azul siga
        // pintando detrás de ella en vez de dejar una franja negra encima.
        val headerBaseTopPadding = dp(24)
        ViewCompat.setOnApplyWindowInsetsListener(content) { rootView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = headerBaseTopPadding + systemBars.top)
            rootView.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(28))
        }

        scrollContent.addView(
            textView(
                getString(R.string.agent_eyebrow_managed_device).uppercase(),
                11.5f,
                palette.primary,
                true
            ).apply { letterSpacing = 0.08f },
            verticalParams(top = 0)
        )
        scrollContent.addView(
            textView(getString(R.string.agent_status_title), 20f, palette.primaryText, true),
            verticalParams(top = 3)
        )

        scrollContent.addView(buildStatusCard(palette), verticalParams(top = 16))
        scrollContent.addView(buildAutomaticUpdateCard(palette), verticalParams(top = 14))

        checkButton = Button(this).apply {
            text = getString(R.string.agent_check_now)
            textSize = 16f
            isAllCaps = false
            minHeight = dp(54)
            minimumHeight = dp(54)
            setTextColor(Color.WHITE)
            background = roundedDrawable(palette.primary, 16)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            compoundDrawablePadding = dp(9)
            setCompoundDrawablesWithIntrinsicBounds(tintedIcon(R.drawable.ic_state_sync, Color.WHITE, 19), null, null, null)
            contentDescription = getString(R.string.agent_check_now)
            attachPressFeedback()
            setOnClickListener {
                AgentWorkScheduler.enqueueNow(this@AgentActivity)
                statusTitle.text = getString(R.string.agent_check_started)
                statusProgress.visibility = View.VISIBLE
                scheduleStatusRefresh()
            }
        }
        scrollContent.addView(checkButton, verticalParams(top = 18))

        openButton = Button(this).apply {
            text = getString(R.string.agent_open_pmmovil)
            textSize = 16f
            isAllCaps = false
            minHeight = dp(54)
            minimumHeight = dp(54)
            setTextColor(palette.primary)
            background = roundedDrawable(palette.surface, 16).apply {
                setStroke(dp(1), palette.outline)
            }
            setPadding(dp(18), dp(8), dp(18), dp(8))
            compoundDrawablePadding = dp(9)
            setCompoundDrawablesWithIntrinsicBounds(tintedIcon(R.drawable.ic_open_external, palette.primary, 18), null, null, null)
            contentDescription = getString(R.string.agent_open_pmmovil)
            attachPressFeedback()
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(AgentConstants.PMMOVIL_PACKAGE)
                    ?.let(::startActivity)
            }
        }
        scrollContent.addView(openButton, verticalParams(top = 10))

        val footer = textView(
            "Administrador PMMovil · Servicio protegido",
            12f,
            palette.secondaryText,
            false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        scrollContent.addView(footer, verticalParams(top = 2))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(scrollContent)
        }
        content.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        return content
    }

    private fun buildStatusCard(palette: UiPalette): View {
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedDrawable(palette.surface, 22)
            elevation = dp(3).toFloat()
        }

        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cardHeader.addView(
            textView(getString(R.string.agent_status_section), 13f, palette.secondaryText, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        statusBadgeIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        statusBadge = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        statusBadgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(7), dp(12), dp(7))
            addView(statusBadgeIcon, LinearLayout.LayoutParams(dp(13), dp(13)).apply { marginEnd = dp(5) })
            addView(statusBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        cardHeader.addView(
            statusBadgeContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
        )
        statusCard.addView(cardHeader)

        statusTitle = textView("", 22f, palette.primaryText, true).apply {
            setPadding(0, dp(18), 0, 0)
        }
        statusCard.addView(statusTitle)

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipVersionValue = TextView(this)
        chipCheckValue = TextView(this)
        chipRow.addView(
            buildChip(palette, R.drawable.ic_tag, getString(R.string.agent_version_chip_label), chipVersionValue),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        chipRow.addView(
            buildChip(palette, R.drawable.ic_clock, getString(R.string.agent_last_check_label), chipCheckValue),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        statusCard.addView(chipRow, verticalParams(top = 12))

        statusProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        statusCard.addView(statusProgress, verticalParams(top = 16, height = 5))

        val resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        resultIcon = ImageView(this).apply { visibility = View.GONE }
        statusResult = textView("", 14f, palette.secondaryText, false)
        resultRow.addView(resultIcon, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(6) })
        resultRow.addView(statusResult, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        statusCard.addView(resultRow, verticalParams(top = 12))

        errorText = textView("", 14f, palette.error, false).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedDrawable(palette.errorSurface, 12)
            visibility = View.GONE
        }
        statusCard.addView(errorText, verticalParams(top = 12))
        return statusCard
    }

    private fun buildChip(palette: UiPalette, @DrawableRes iconRes: Int, label: String, valueView: TextView): View {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = roundedDrawable(palette.infoSurface, 12)
        }
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(
            ImageView(this@AgentActivity).apply {
                setImageDrawable(tintedIcon(iconRes, palette.secondaryText, 12))
            },
            LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(5) }
        )
        labelRow.addView(textView(label.uppercase(), 9.5f, palette.secondaryText, true).apply { letterSpacing = 0.03f })
        chip.addView(labelRow)
        valueView.apply {
            textSize = 13.5f
            setTextColor(palette.primaryText)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(3), 0, 0)
        }
        chip.addView(valueView)
        return chip
    }

    private fun buildAutomaticUpdateCard(palette: UiPalette): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedDrawable(palette.infoSurface, 18)
        }
        card.addView(
            ImageView(this).apply { setImageDrawable(tintedIcon(R.drawable.ic_info, palette.primary, 17)) },
            LinearLayout.LayoutParams(dp(19), dp(19)).apply { marginEnd = dp(11); topMargin = dp(1) }
        )
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(textView(getString(R.string.agent_auto_update_title), 16f, palette.primary, true))
        textColumn.addView(
            textView(getString(R.string.agent_auto_update_detail), 14f, palette.secondaryText, false),
            verticalParams(top = 4)
        )
        card.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return card
    }

    private fun refreshStatus() {
        val palette = currentPalette()
        val devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
        val state = AgentStateStore(this)
        val currentVersion = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(AgentConstants.PMMOVIL_PACKAGE, 0).longVersionCode
        }.getOrNull()
        val result = state.lastResult
        val isBusy = result == RESULT_CHECKING || result == RESULT_INSTALLING

        val presentation = when {
            result == RESULT_CHECKING -> StatusPresentation(
                getString(R.string.agent_status_checking),
                getString(R.string.agent_status_in_progress),
                palette.warningSurface,
                palette.warning
            )
            result == RESULT_INSTALLING -> StatusPresentation(
                getString(R.string.agent_status_installing),
                getString(R.string.agent_status_in_progress),
                palette.infoSurface,
                palette.primary
            )
            state.lastError != null -> StatusPresentation(
                getString(R.string.agent_status_error),
                getString(R.string.agent_status_attention),
                palette.errorSurface,
                palette.error
            )
            result == RESULT_UP_TO_DATE -> StatusPresentation(
                getString(R.string.agent_status_up_to_date),
                getString(R.string.agent_status_updated),
                palette.successSurface,
                palette.success
            )
            else -> StatusPresentation(
                getString(R.string.agent_status_ready),
                if (isDeviceOwner) getString(R.string.agent_status_active)
                else getString(R.string.agent_status_attention),
                if (isDeviceOwner) palette.successSurface else palette.warningSurface,
                if (isDeviceOwner) palette.success else palette.warning
            )
        }

        val kind = when {
            result == RESULT_CHECKING -> StateKind.CHECKING
            result == RESULT_INSTALLING -> StateKind.INSTALLING
            state.lastError != null -> StateKind.ERROR
            result == RESULT_UP_TO_DATE || isDeviceOwner -> StateKind.SUCCESS
            else -> StateKind.ATTENTION
        }

        if (::statusCard.isInitialized) {
            TransitionManager.beginDelayedTransition(statusCard, AutoTransition().setDuration(220))
        }

        statusTitle.text = presentation.title
        statusTitle.setTextColor(palette.primaryText)
        statusBadge.text = presentation.badge
        statusBadge.setTextColor(presentation.badgeTextColor)
        statusBadgeContainer.background = roundedDrawable(presentation.badgeColor, 18)
        statusBadgeIcon.setImageDrawable(tintedIcon(iconFor(kind), presentation.badgeTextColor, 13))

        chipVersionValue.text = currentVersion?.toString() ?: getString(R.string.agent_no_version)
        chipCheckValue.text = state.lastCheckLabel()

        val resultShowsCheck = kind == StateKind.SUCCESS && state.lastError == null
        resultIcon.visibility = if (resultShowsCheck) View.VISIBLE else View.GONE
        if (resultShowsCheck) {
            resultIcon.setImageDrawable(tintedIcon(R.drawable.ic_state_check, palette.success, 15))
        }
        statusResult.text = buildString {
            append(getString(R.string.agent_result_label))
            append(": ")
            append(result)
        }
        statusResult.setTextColor(if (resultShowsCheck) palette.success else palette.secondaryText)

        statusProgress.visibility = if (isBusy) View.VISIBLE else View.GONE
        errorText.text = state.lastError.orEmpty()
        errorText.setTextColor(palette.error)
        errorText.background = roundedDrawable(palette.errorSurface, 12)
        errorText.visibility = if (state.lastError != null) View.VISIBLE else View.GONE
        checkButton.isEnabled = !isBusy
        checkButton.alpha = if (isBusy) 0.65f else 1f

        if (kind != lastStateKind) {
            animateStateTransition(kind, palette)
            if (kind == StateKind.ERROR) playShake(statusCard)
            lastStateKind = kind
        }
    }

    private fun animateStateTransition(kind: StateKind, palette: UiPalette) {
        badgeLoopAnimator?.cancel()
        badgeLoopAnimator = null
        statusBadgeIcon.rotation = 0f
        statusBadgeIcon.scaleX = 1f
        statusBadgeIcon.scaleY = 1f

        if (!ValueAnimator.areAnimatorsEnabled()) return

        when (kind) {
            StateKind.CHECKING -> {
                badgeLoopAnimator = ObjectAnimator.ofFloat(statusBadgeIcon, View.ROTATION, 0f, 360f).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
            StateKind.INSTALLING -> {
                badgeLoopAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    statusBadgeIcon,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f, 1f)
                ).apply {
                    duration = 700
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            StateKind.SUCCESS -> {
                statusBadgeIcon.scaleX = 0.3f
                statusBadgeIcon.scaleY = 0.3f
                statusBadgeIcon.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(OvershootInterpolator(2.6f))
                    .start()
            }
            StateKind.ERROR, StateKind.ATTENTION -> Unit
        }
    }

    private fun playShake(target: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 0f, -10f, 10f, -7f, 7f, -3f, 3f, 0f).apply {
            duration = 420
            start()
        }
    }

    private fun View.attachPressFeedback() {
        setOnTouchListener { view, event ->
            if (view.isEnabled && ValueAnimator.areAnimatorsEnabled()) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
    }

    private fun iconFor(kind: StateKind): Int = when (kind) {
        StateKind.CHECKING -> R.drawable.ic_state_sync
        StateKind.INSTALLING -> R.drawable.ic_state_sync
        StateKind.ERROR -> R.drawable.ic_state_warning
        StateKind.SUCCESS -> R.drawable.ic_state_check
        StateKind.ATTENTION -> R.drawable.ic_state_warning
    }

    private fun tintedIcon(@DrawableRes resId: Int, color: Int, sizeDp: Int): Drawable {
        val drawable = ContextCompat.getDrawable(this, resId)!!.mutate()
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        val size = dp(sizeDp)
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    private fun scheduleStatusRefresh() {
        mainHandler.removeCallbacks(statusRefreshTask)
        mainHandler.post(statusRefreshTask)
    }

    private fun isWorkerActive(): Boolean {
        val result = AgentStateStore(this).lastResult
        return result == RESULT_CHECKING || result == RESULT_INSTALLING
    }

    private fun setSystemBars() {
        val palette = currentPalette()
        // En edge-to-edge (obligatorio en targetSdk 35+) el color de las barras ya no
        // se controla con statusBarColor/navigationBarColor; solo se elige si sus
        // íconos son claros u oscuros. El header vive detrás de la barra de estado
        // (ver insets listener en buildContent), así que sus íconos van en blanco.
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = !palette.isDark
    }

    private fun currentPalette(): UiPalette {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) UiPalette.dark else UiPalette.light
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun verticalParams(
        top: Int,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams {
        val resolvedHeight = if (height >= 0) dp(height) else height
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resolvedHeight
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private enum class StateKind { CHECKING, INSTALLING, ERROR, SUCCESS, ATTENTION }

    private data class StatusPresentation(
        val title: String,
        val badge: String,
        val badgeColor: Int,
        val badgeTextColor: Int
    )

    private data class UiPalette(
        val background: Int,
        val surface: Int,
        val infoSurface: Int,
        val successSurface: Int,
        val warningSurface: Int,
        val errorSurface: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val outline: Int,
        val primary: Int,
        val header: Int,
        val headerSecondary: Int,
        val success: Int,
        val warning: Int,
        val error: Int,
        val isDark: Boolean
    ) {
        companion object {
            val light = UiPalette(
                background = Color.rgb(246, 248, 252),
                surface = Color.WHITE,
                infoSurface = Color.rgb(235, 243, 255),
                successSurface = Color.rgb(231, 248, 239),
                warningSurface = Color.rgb(255, 246, 220),
                errorSurface = Color.rgb(255, 235, 233),
                primaryText = Color.rgb(20, 32, 51),
                secondaryText = Color.rgb(82, 99, 124),
                outline = Color.rgb(201, 214, 235),
                primary = Color.rgb(16, 83, 180),
                header = Color.rgb(10, 72, 155),
                headerSecondary = Color.rgb(7, 44, 101),
                success = Color.rgb(18, 126, 82),
                warning = Color.rgb(164, 103, 0),
                error = Color.rgb(177, 49, 39),
                isDark = false
            )

            val dark = UiPalette(
                background = Color.rgb(15, 23, 42),
                surface = Color.rgb(30, 41, 59),
                infoSurface = Color.rgb(24, 52, 91),
                successSurface = Color.rgb(24, 74, 54),
                warningSurface = Color.rgb(72, 53, 18),
                errorSurface = Color.rgb(77, 32, 35),
                primaryText = Color.rgb(241, 245, 249),
                secondaryText = Color.rgb(190, 202, 220),
                outline = Color.rgb(71, 91, 119),
                primary = Color.rgb(116, 170, 255),
                header = Color.rgb(15, 67, 140),
                headerSecondary = Color.rgb(6, 34, 78),
                success = Color.rgb(90, 210, 150),
                warning = Color.rgb(255, 196, 82),
                error = Color.rgb(255, 142, 133),
                isDark = true
            )
        }
    }

    companion object {
        private const val STATUS_REFRESH_INTERVAL_MS = 1_000L
        private const val RESULT_CHECKING = "revisando"
        private const val RESULT_INSTALLING = "instalación solicitada"
        private const val RESULT_UP_TO_DATE = "sin actualización"
        private val PMM_YELLOW = Color.rgb(0xF8, 0xE7, 0x1C)
    }
}
