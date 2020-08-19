package com.ble.demo.util

import android.app.Activity
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import com.ble.demo.R
import java.util.*

/**
 * Created by JiaJiefei on 2016/8/18.
 */
class HexKeyboardUtil(
    act: Activity,
    private val inputEditText: EditText,
    private val maxLength: Int
) {
    private lateinit var keyboardView: KeyboardView
    private var isShow = false
    private var mOnDoneListener: OnDoneListener? = null


    private val mOnKeyboardActionListener: OnKeyboardActionListener =
        object : OnKeyboardActionListener {
            override fun swipeUp() {}
            override fun swipeRight() {}
            override fun swipeLeft() {}
            override fun swipeDown() {}
            override fun onText(text: CharSequence) {}
            override fun onRelease(primaryCode: Int) {}
            override fun onPress(primaryCode: Int) {
                // checkIShowPreview(primaryCode);
            }

            // 显示预览
            private fun checkIShowPreview(primaryCode: Int) {
                val list = Arrays.asList(15, 17) // Del,Done不显示预览
                keyboardView.isPreviewEnabled = !list.contains(primaryCode)
            }

            override fun onKey(primaryCode: Int, keyCodes: IntArray) {
                // checkIShowPreview(primaryCode);
                // Log.i("KeyBoard", "primaryCode=" + primaryCode);
                /**
                 * 实体键盘：
                 * D    E   F
                 * A    B   C
                 * 7    8   9
                 * 4    5   6
                 * 1    2   3
                 * del  0   Done
                 *
                 * Key code:
                 * 0    1   2
                 * 3    4   5
                 * 6    7   8
                 * 9    10  11
                 * 12   13  14
                 * 15   16  17
                 */
                val editable = inputEditText.text
                val start = inputEditText.selectionStart
                if (primaryCode == 17) { // 完成
                    // hideKeyboard();
                    if (mOnDoneListener != null) {
                        mOnDoneListener!!.done(inputEditText.text.toString())
                    }
                } else if (primaryCode == 15) { // 回退
                    if (editable != null && editable.length > 0) {
                        if (start > 0) {
                            editable.delete(start - 1, start)
                        }
                    }
                } else {
                    if (editable!!.length >= maxLength) {
                        inputEditText.error = inputEditText.context.resources
                            .getString(R.string.max_bytes, maxLength / 2)
                    } else {
                        editable.insert(
                            start,
                            Character.toString(
                                HEX_CHARS[primaryCode]
                            )
                        )
                    }
                }
            }
        }


    init {
        keyboardView = act.findViewById<View>(R.id.keyboard_view) as KeyboardView
        keyboardView.keyboard = Keyboard(act, R.xml.keyboard)
        keyboardView.isEnabled = true
        keyboardView.isPreviewEnabled = false
        keyboardView.setOnKeyboardActionListener(mOnKeyboardActionListener)
        hideSoftKeyboard(act, inputEditText)
    }


    fun setOnDoneListener(listener: OnDoneListener?) {
        mOnDoneListener = listener
    }

    // 隐藏系统的输入键盘
    private fun hideSoftKeyboard(ctx: Activity, edit: EditText) {
        ctx.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        try {
            val setShowSoftInputOnFocus = EditText::class.java.getMethod(
                "setShowSoftInputOnFocus",
                Boolean::class.javaPrimitiveType
            )
            setShowSoftInputOnFocus.isAccessible = false
            setShowSoftInputOnFocus.invoke(edit, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun showKeyboard() {
        val visibility = keyboardView.visibility
        if (visibility == View.GONE || visibility == View.INVISIBLE) {
            keyboardView.visibility = View.VISIBLE
            isShow = true
        }
    }

    fun hideKeyboard() {
        val visibility = keyboardView.visibility
        if (visibility == View.VISIBLE) {
            keyboardView.visibility = View.GONE
            isShow = false
        }
    }

    interface OnDoneListener {
        fun done(input: String?)
    }

    companion object {
        private val HEX_CHARS = arrayOf<Char>(
            'D', 'E', 'F', 'A',
            'B', 'C', '7', '8', '9', '4', '5', '6', '1', '2', '3', 0x1F.toChar(), '0', 0x1F.toChar()
        )
    }
}