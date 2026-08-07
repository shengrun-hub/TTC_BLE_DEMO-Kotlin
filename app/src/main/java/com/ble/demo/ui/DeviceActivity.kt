package com.ble.demo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.api.DataUtil
import com.ble.demo.R
import com.ble.demo.databinding.ActivityDeviceBinding
import com.ble.demo.util.BtUtil
import com.ble.demo.util.HexAsciiWatcher
import com.ttcble.leui.LeProxy

/**
 * Data-passthrough demo page — combined send / receive / MTU / OAD entry.
 * Launched from [ScanFragment] when the user taps a scanned device.
 */
class DeviceActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityDeviceBinding
    private lateinit var mInputWatcher: HexAsciiWatcher

    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mIsConnected = true
    private var mIsHex = true
    private var mMtu = 23

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
            if (address != mDeviceAddress) return

            when (intent.action) {
                BtUtil.ACTION_GATT_DISCONNECTED -> {
                    mIsConnected = false
                    invalidateOptionsMenu()
                    appendLog("Disconnected")
                }
                BtUtil.ACTION_GATT_CONNECTED -> {
                    mIsConnected = true
                    invalidateOptionsMenu()
                    appendLog("Connected")
                }
                BtUtil.ACTION_DATA_AVAILABLE -> {
                    val uuid = intent.getStringExtra(BtUtil.EXTRA_UUID)
                    val data = intent.getByteArrayExtra(BtUtil.EXTRA_DATA)
                    val sb = StringBuilder()
                        .append("uuid: $uuid\nlength: ${data?.size ?: 0}\n")
                    sb.append(
                        if (mIsHex) "data: ${DataUtil.byteArrayToHex(data)}"
                        else "data: ${data?.let { String(it) } ?: ""}"
                    )
                    binding.deviceLogView.append(Log.DEBUG, sb.toString())
                }
                BtUtil.ACTION_GATT_SERVICES_DISCOVERED -> {
                    appendLog("Services discovered")
                }
                BtUtil.ACTION_MTU_CHANGED -> {
                    mMtu = intent.getIntExtra(BtUtil.EXTRA_MTU, 23)
                    updateInputMode()
                    appendLog("MTU changed: $mMtu")
                }

                BtUtil.ACTION_DESCRIPTOR_WRITE -> appendLog("Descriptor written")
            }
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread { binding.deviceLogView.append(Log.DEBUG, msg) }
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_GATT_CONNECTED)
        filter.addAction(BtUtil.ACTION_GATT_DISCONNECTED)
        filter.addAction(BtUtil.ACTION_GATT_SERVICES_DISCOVERED)
        filter.addAction(BtUtil.ACTION_DATA_AVAILABLE)
        filter.addAction(BtUtil.ACTION_MTU_CHANGED)
        filter.addAction(BtUtil.ACTION_DESCRIPTOR_WRITE)
        return filter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mDeviceName = intent.getStringExtra(BtUtil.EXTRA_NAME)
        mDeviceAddress = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = mDeviceName
        supportActionBar?.subtitle = mDeviceAddress

        initView()
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter())
    }

    private fun initView() {
        // Input watcher with byte counter
        mInputWatcher = HexAsciiWatcher(this)
        mInputWatcher.setHost(binding.deviceEdtTx)
        mInputWatcher.setIndicator(binding.deviceTvBytes)
        mInputWatcher.setTextType(if (mIsHex) HexAsciiWatcher.HEX else HexAsciiWatcher.ASCII)
        mInputWatcher.maxLength = 40
        binding.deviceEdtTx.addTextChangedListener(mInputWatcher)
        binding.deviceEdtTx.setOnClickListener { goHexInput() }

        // HEX / Encrypt checkboxes
        binding.deviceCboxHex.setOnCheckedChangeListener { _, isChecked ->
            mIsHex = isChecked
            updateInputMode()
        }
        binding.deviceCboxEncrypt.setOnCheckedChangeListener { _, isChecked ->
            BtUtil.instance.setEncrypt(isChecked)
            updateInputMode()
        }

        // Buttons
        binding.deviceBtnSend.setOnClickListener(this)
    }

    private fun updateInputMode() {
        val encrypt = binding.deviceCboxEncrypt.isChecked
        mInputWatcher.setTextType(if (mIsHex) HexAsciiWatcher.HEX else HexAsciiWatcher.ASCII)
        val maxBytes = mMtu - 3 - if (encrypt) 3 else 0
        val maxLen = if (mIsHex) maxBytes * 2 else maxBytes
        mInputWatcher.maxLength = maxLen
        val hint = if (mIsHex) getString(R.string.connected_send_hex_hint, maxBytes)
                   else getString(R.string.connected_send_ascii_hint, maxBytes)
        binding.deviceEdtTx.hint = hint
        mInputWatcher.setIndicatorText(getString(R.string.input_bytes, 0))
    }

    private fun goHexInput() {
        if (mIsHex) {
            val intent = Intent(this, HexInputActivity::class.java)
            intent.putExtra(HexInputActivity.EXTRA_MAX_LENGTH, mInputWatcher.maxLength)
            intent.putExtra(HexInputActivity.EXTRA_HEX_STRING, binding.deviceEdtTx.text.toString())
            startActivityForResult(intent, REQ_HEX_INPUT)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_HEX_INPUT && resultCode == RESULT_OK) {
            val hexStr = data?.getStringExtra(HexInputActivity.EXTRA_HEX_STRING) ?: return
            binding.deviceEdtTx.setText(hexStr)
            binding.deviceEdtTx.setSelection(hexStr.length)
        }
    }

    override fun onClick(v: View) {
        if (v.id == R.id.device_btn_send) send()
    }

    private fun send() {
        val addr = mDeviceAddress ?: return
        val input = binding.deviceEdtTx.text.toString()
        if (input.isEmpty()) return

        val data = if (mIsHex) DataUtil.hexToByteArray(input) else input.toByteArray()
        BtUtil.instance.send(addr, data)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val connectItem = menu.findItem(R.id.action_connect)
        if (mIsConnected) {
            connectItem.setTitle(R.string.menu_disconnect)
        } else {
            connectItem.setTitle(R.string.menu_connect)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            R.id.action_connect -> {
                val addr = mDeviceAddress ?: return true
                if (mIsConnected) LeProxy.instance.disconnect(addr)
                else LeProxy.instance.connect(addr)
                return true
            }
            R.id.action_oad -> {
                val addr = mDeviceAddress ?: return true
                val intent = Intent(this, OADActivity::class.java)
                intent.putExtra(BtUtil.EXTRA_NAME, mDeviceName)
                intent.putExtra(BtUtil.EXTRA_ADDRESS, addr)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDeviceAddress?.let { LeProxy.instance.disconnect(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver)
    }

    companion object {
        private const val TAG = "DeviceActivity"
        private const val REQ_HEX_INPUT = 3
    }
}
