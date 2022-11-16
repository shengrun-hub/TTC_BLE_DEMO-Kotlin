package com.ble.demo.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ble.demo.R
import com.ble.demo.util.HexAsciiWatcher
import com.ble.demo.util.HexKeyboardUtil
import kotlinx.android.synthetic.main.activity_hex_input.*

/**
 * Created by JiaJiefei on 2016/8/18.
 */
class HexInputActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_hex_input)
        supportActionBar?.title = "Hex"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val hexStr = intent.getStringExtra(EXTRA_HEX_STRING)
        var len: Int
        if (!TextUtils.isEmpty(hexStr)) {
            editText.setText(hexStr)
            editText.setSelection(hexStr!!.length)
            len = hexStr.length
            len = if (len % 2 == 0) len / 2 else len / 2 + 1
        } else {
            len = 0
        }
        tv_input_bytes.text = getString(R.string.input_bytes, len)
        val maxLength = intent.getIntExtra(EXTRA_MAX_LENGTH, 40)
        val watcher = HexAsciiWatcher(this)
        watcher.setHost(editText)
        watcher.setTextType(HexAsciiWatcher.HEX)
        watcher.maxLength = maxLength
        watcher.setIndicator(tv_input_bytes)
        editText.addTextChangedListener(watcher)
        val keyboardUtil = HexKeyboardUtil(this, editText, maxLength)
        keyboardUtil.showKeyboard()
        keyboardUtil.setOnDoneListener(object : HexKeyboardUtil.OnDoneListener {
            override fun done(input: String?) {
                val data = Intent()
                data.putExtra(EXTRA_HEX_STRING, input)
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_HEX_STRING = "extra_hex_string"
        const val EXTRA_MAX_LENGTH = "extra_max_length"
    }
}