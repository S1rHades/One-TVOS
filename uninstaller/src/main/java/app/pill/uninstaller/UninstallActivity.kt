package app.pill.uninstaller

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One screen: a button that asks Android to uninstall Pill Launcher (the TV confirms).
 * Once it's gone, the same button removes this app too. Checked again on every return,
 * so the screen always matches what is installed.
 */
class UninstallActivity : Activity() {

    private lateinit var message: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        message = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        button = Button(this).apply {
            textSize = 20f
            isAllCaps = false
            setPadding((32 * dp).toInt(), (14 * dp).toInt(), (32 * dp).toInt(), (14 * dp).toInt())
            // White when focused, as in the launcher
            onFocusChangeListener = View.OnFocusChangeListener { v, focused -> style(v as Button, focused, dp) }
            style(this, false, dp)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(0x1B, 0x25, 0x40))
                setPadding((64 * dp).toInt(), 0, (64 * dp).toInt(), 0)
                addView(message)
                addView(
                    button,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = (32 * dp).toInt() },
                )
            },
        )
    }

    override fun onResume() {
        super.onResume()
        if (installed(LAUNCHER)) {
            message.text = "Removes Pill Launcher and its settings from this TV.\nThe TV asks you to confirm, then its own home screen takes over."
            button.text = "Uninstall Pill Launcher"
            button.setOnClickListener { uninstall(LAUNCHER) }
        } else {
            message.text = "Pill Launcher isn't installed.\nYou can remove this uninstaller too."
            button.text = "Remove this uninstaller"
            button.setOnClickListener { uninstall(packageName) }
        }
        button.requestFocus()
    }

    private fun installed(pkg: String): Boolean =
        try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private fun uninstall(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            message.text = "This TV can't uninstall apps from here.\nUse the TV's Settings → Apps instead."
        }
    }

    private fun style(b: Button, focused: Boolean, dp: Float) {
        b.background = GradientDrawable().apply {
            cornerRadius = 28 * dp
            setColor(if (focused) Color.WHITE else Color.argb(0x26, 0xFF, 0xFF, 0xFF))
        }
        b.setTextColor(if (focused) Color.rgb(0x10, 0x14, 0x20) else Color.WHITE)
    }

    private companion object {
        const val LAUNCHER = "app.pill.launcher"
    }
}
