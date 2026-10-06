package com.example.ink

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ink = InkView(this)

        fun btn(label: String, onClick: (Button) -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick(this) }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            addView(btn("Geri al") { ink.undo() })
            addView(btn("İleri al") { ink.redo() })
            addView(btn("Temizle") { ink.clear() })
            addView(btn("Siyah") { ink.inkColor = Color.BLACK })
            addView(btn("Mavi") { ink.inkColor = Color.rgb(25, 80, 220) })
            addView(btn("Kırmızı") { ink.inkColor = Color.rgb(210, 40, 40) })
            addView(btn("İnce") { ink.config = ink.config.copy(maxWidth = 4f) })
            addView(btn("Kalın") { ink.config = ink.config.copy(maxWidth = 12f) })
            addView(btn("Parmak: kapalı") {
                ink.allowFinger = !ink.allowFinger
                it.text = if (ink.allowFinger) "Parmak: açık" else "Parmak: kapalı"
            })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(HorizontalScrollView(this@MainActivity).apply { addView(bar) },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(ink, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }
}
