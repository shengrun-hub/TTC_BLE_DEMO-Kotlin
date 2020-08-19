package com.ble.demo.util

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import com.ble.demo.R

/**
 * 16进制或ASCII码字符过滤
 */
class HexAsciiWatcher(private val context: Context?) : TextWatcher {
    private var host: EditText? = null
    private var indicator: TextView? = null
    private var textType = HEX

    /**
     * must > 0
     */
    var maxLength = 40

    /**
     * @param textType [HexAsciiWatcher.HEX] or [HexAsciiWatcher.ASCII]
     */
    fun setTextType(textType: Int) {
        this.textType = textType
    }

    fun setHost(host: EditText?) {
        this.host = host
    }

    fun setIndicator(indicator: TextView?) {
        this.indicator = indicator
    }

    fun setIndicatorText(text: String?) {
        indicator!!.text = text
    }

    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) {
    }

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ) {
    }

    override fun afterTextChanged(s: Editable) {
        // 过滤字符
        filterCharSequence(s)
        val data = s.toString().trim { it <= ' ' }
        if (data.length < maxLength + 1) {
            var len = data.length
            if (textType == HEX) {
                len = if (len % 2 == 1) len / 2 + 1 else len / 2
            }
            if (indicator != null) {
                indicator!!.text = context!!.getString(R.string.input_bytes, len)
            }
        } else {
            // 多余的字符全部清掉
            s.delete(maxLength, s.length)
            if (host != null && context != null) {
                // 提示输入字节数已达上限
                if (textType == HEX) {
                    host!!.error = context.resources
                        .getString(R.string.max_bytes, maxLength / 2)
                } else {
                    host!!.error = context.resources.getString(R.string.max_bytes, maxLength)
                }
            }
        }
    }

    private fun filterCharSequence(s: Editable) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when (textType) {
                HEX ->                     // 0-9 48-57
                    // A-F 65-70
                    // a-z 97-102
                    if (c.toInt() > 47 && c.toInt() < 58 || c.toInt() > 64 && c.toInt() < 71 || c.toInt() > 96 && c.toInt() < 103) {
                        //
                    } else {
                        s.delete(i, i + 1)
                        i--
                    }
                ASCII ->                     // 32-126范围之外的都是乱码字符或是无法输入的字符
                    if (c.toInt() >= 0 && c.toInt() <= 127) {
                        //
                    } else {
                        s.delete(i, i + 1)
                        i--
                    }
            }
            i++
        }
    }

    companion object {
        const val HEX = 0
        const val ASCII = 1
    }
}