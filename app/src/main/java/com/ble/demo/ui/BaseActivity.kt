package com.ble.demo.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ble.demo.R

abstract class BaseActivity : AppCompatActivity() {

    private val scrimColor by lazy { ContextCompat.getColor(this, R.color.colorPrimaryDark) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrimColor)
        )
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view?.let { wrap(it) })
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view?.let { wrap(it) }, params)
    }

    private fun wrap(content: View): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val scrim = View(this).apply { setBackgroundColor(scrimColor) }

        container.addView(
            scrim,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        )
        container.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val initialLeft = container.paddingLeft
        val initialRight = container.paddingRight
        val initialBottom = container.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(container) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scrim.updateHeight(bars.top)
            target.setPadding(
                initialLeft + bars.left, target.paddingTop,
                initialRight + bars.right, initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(container)
        return container
    }

    private fun View.updateHeight(h: Int) {
        val lp = layoutParams ?: return
        if (lp.height == h) return
        lp.height = h
        layoutParams = lp
    }
}
