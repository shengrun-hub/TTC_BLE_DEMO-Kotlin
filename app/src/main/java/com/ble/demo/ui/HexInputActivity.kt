package com.ble.demo.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.WindowManager
import com.ble.demo.R
import com.ble.demo.databinding.ActivityHexInputBinding
import com.ble.demo.util.HexAsciiWatcher
import com.ble.demo.util.HexKeyboardUtil

/**
 * Created by JiaJiefei on 2016/8/18.
 */
class HexInputActivity : BaseActivity() {

    private lateinit var binding: ActivityHexInputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding = ActivityHexInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Hex"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val hexStr = intent.getStringExtra(EXTRA_HEX_STRING)
        var len: Int
        if (!TextUtils.isEmpty(hexStr)) {
            binding.editText.setText(hexStr)
            binding.editText.setSelection(hexStr!!.length)
            len = hexStr.length
            len = if (len % 2 == 0) len / 2 else len / 2 + 1
        } else {
            len = 0
        }
        binding.tvInputBytes.text = getString(R.string.input_bytes, len)
        val maxLength = intent.getIntExtra(EXTRA_MAX_LENGTH, 40)
        val watcher = HexAsciiWatcher(this)
        watcher.setHost(binding.editText)
        watcher.setTextType(HexAsciiWatcher.HEX)
        watcher.maxLength = maxLength
        watcher.setIndicator(binding.tvInputBytes)
        binding.editText.addTextChangedListener(watcher)
        val keyboardUtil = HexKeyboardUtil(this, binding.editText, maxLength)
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