package com.ble.demo.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.api.DataUtil
import com.ble.ble.oad.*
import com.ble.demo.R
import com.ble.demo.databinding.ActivityOadBinding
import com.ble.demo.util.BtUtil
import com.ble.gatt.GattAttributes
import com.ble.utils.DimensUtil
import com.ble.utils.ToastUtil
import com.ttcble.leui.LeProxy
import java.io.File
import java.util.*
import androidx.core.content.edit

/**
 * OAD (Over-the-Air Download) firmware upgrade page.
 *
 * Flow:
 *   1. Check connected device's GATT services:
 *      - If ffc0 (TI_OAD_Service) present → device is in OAD mode → proceed directly
 *      - If ffd0 (TI_Reset_Service) present → device is in normal mode → show Reset UI
 *   2. Reset flow: user inputs hex command → sends to ffd1 → device disconnects →
 *      auto-reconnect → re-check services → if ffc0 appears, proceed with OAD
 *   3. OAD flow: select firmware file, choose OAD type, start programming
 */
class OADActivity : BaseActivity(), View.OnClickListener, OADListener {

    private enum class State {
        CHECKING,       // checking device services on entry
        NEED_RESET,     // ffd0 found — waiting for user to send Reset command
        RESETTING,      // Reset command sent — waiting for disconnect
        RECONNECTING,   // disconnected after Reset — auto-reconnecting
        READY,          // ffc0 found — ready for OAD upgrade
        PROGRAMMING     // OAD in progress
    }

    companion object {
        private const val TAG = "OADActivity"

        // Persistence — OADType per MAC, file per OADType, Reset shared
        private const val PREF_NAME = "oad_prefs"
        private const val PREF_OAD_TYPE = "oad_type_"
        private const val PREF_FILE_URI = "file_uri_"
        private const val PREF_FILE_NAME = "file_name_"
        private const val PREF_FILE_PATH = "file_path_"
        private const val PREF_RESET_CMD = "reset_cmd"
    }

    private lateinit var binding: ActivityOadBinding

    private val mAssetsFiles: MutableList<String> = ArrayList()

    private var mSendInterval = 20
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mFilePath: String? = null
    private val mProgressInfo = ProgressInfo()
    private var mOADProxy: OADProxy? = null

    private var mState = State.CHECKING
    private var mSelectedOadType = OADType.cc2640_r2_oad
    private var mIsConnected = true // assume connected on entry

    private val mOpenDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleSelectedFile(it) }
        }

    private class ProgressInfo {
        var iBytes = 0
        var nBytes = 0
        var milliseconds: Long = 0
    }

    private fun displayData(iBytes: Int, nBytes: Int, milliseconds: Long) {
        mProgressInfo.iBytes = iBytes
        mProgressInfo.nBytes = nBytes
        mProgressInfo.milliseconds = milliseconds
        updateProgressUi()
    }

    private fun updateProgressUi() {
        val seconds = mProgressInfo.milliseconds / 1000
        var progress = 0
        if (mProgressInfo.nBytes != 0) {
            progress = 100 * mProgressInfo.iBytes / mProgressInfo.nBytes
        }
        val time = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
        val bytes = "${mProgressInfo.iBytes / 1024}KB/${mProgressInfo.nBytes / 1024}KB"
        binding.oadTvProgress.text = "$progress%"
        binding.oadTvTime.text = time
        binding.oadTvBytes.text = bytes
        binding.oadProgressBar.progress = progress
    }

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
            if (address != mDeviceAddress) return

            var s: String? = null
            when (intent.action) {
                BtUtil.ACTION_GATT_DISCONNECTED -> {
                    binding.oadTvState.setText(R.string.disconnected)
                    mIsConnected = false
                    invalidateOptionsMenu()
                    s = "Disconnected"
                    handleDisconnect()
                }
                BtUtil.ACTION_GATT_CONNECTED -> {
                    binding.oadTvState.setText(R.string.connected)
                    mIsConnected = true
                    invalidateOptionsMenu()
                    s = "Connected"
                    handleReconnect()
                }
                BtUtil.ACTION_GATT_SERVICES_DISCOVERED -> {
                    s = "Services discovered"
                    handleServicesDiscovered()
                }
                BtUtil.ACTION_DATA_AVAILABLE -> {
                    val uuid = intent.getStringExtra(BtUtil.EXTRA_UUID)
                    val data = intent.getByteArrayExtra(BtUtil.EXTRA_DATA) ?: return
                    if (GattAttributes.TI_OAD_Image_Identify.toString() == uuid && data.size >= 2) {
                        val ver = DataUtil.buildUint16(data[1], data[0]).toInt().and(0xFFFF)
                        val imgType = if (ver and 1 == 1) 'B' else 'A'
                        binding.oadTvImageType.text = "Target Image Type: $imgType"
                    } else if (GattAttributes.TI_OAD_Image_Block.toString() == uuid) {
                        s = "OAD Block Rx: " + DataUtil.byteArrayToHex(data)
                        Log.e(TAG, s)
                    }
                }
                BtUtil.ACTION_MTU_CHANGED -> {
                    val mtu = intent.getIntExtra(BtUtil.EXTRA_MTU, 23)
                    s = "MTU Changed: $mtu"
                }
            }
            appendLog(s)
        }
    }

    // -- disconnect / reconnect handling for Reset flow -- //

    private fun handleDisconnect() {
        if (mState == State.RESETTING) {
            mState = State.RECONNECTING
            binding.oadTvResetStatus.setText(R.string.oad_reset_sent)
            appendLog("Device disconnected after Reset — reconnecting…")
            LeProxy.instance.connect(mDeviceAddress!!)
        }
    }

    private fun handleReconnect() {
        if (mState == State.RECONNECTING) {
            appendLog("Reconnected — waiting for services…")
        }
    }

    private fun handleServicesDiscovered() {
        if (mState == State.RECONNECTING || mState == State.CHECKING) {
            checkDeviceServices()
        }
    }

    // -- service checking -- //

    /**
     * Check connected device services to determine the OAD flow.
     *   - ffc0 present → READY, show OAD controls
     *   - ffd0 present → NEED_RESET, show Reset UI
     *   - neither   → fall back to READY (backward compatible)
     */
    private fun checkDeviceServices() {
        val gatt = LeProxy.instance.getService()?.getBluetoothGatt(mDeviceAddress)
        if (gatt == null) {
            appendLog("GATT not available — falling back to OAD mode")
            enterReadyState()
            return
        }

        val oadService = gatt.getService(GattAttributes.TI_OAD_Service)       // ffc0
        val resetService = gatt.getService(GattAttributes.TI_Reset_Service)   // ffd0

        if (oadService != null) {
            appendLog("OAD service (ffc0) detected — ready for upgrade")
            enterReadyState()
        } else if (resetService != null) {
            appendLog("Reset service (ffd0) detected — device needs Reset")
            enterNeedResetState()
        } else {
            // Unknown services — fall back to treating as OAD-ready
            appendLog("No known OAD/Reset service — falling back to OAD mode")
            enterReadyState()
        }
    }

    private fun enterNeedResetState() {
        mState = State.NEED_RESET
        binding.oadResetInputRow.visibility = View.VISIBLE
        binding.oadTvResetStatus.visibility = View.GONE
        binding.oadControlsSection.visibility = View.VISIBLE
        binding.oadTvState.setText(R.string.oad_waiting_reset)
    }

    private fun enterReadyState() {
        val wasReconnecting = mState == State.RECONNECTING
        mState = State.READY
        binding.oadResetInputRow.visibility = View.GONE
        binding.oadTvResetStatus.visibility = View.GONE
        binding.oadControlsSection.visibility = View.VISIBLE

        binding.oadBtnStart.setText(R.string.oad_start)
        binding.oadBtnStart.isEnabled = true

        if (mOADProxy == null) {
            createOADProxy()
        }

        // One-click upgrade: after Reset + reconnect, auto-start OAD if a file is selected
        if (wasReconnecting && mFilePath != null) {
            appendLog("Auto-starting OAD after Reset…")
            startOad()
        }
    }

    private fun createOADProxy() {
        mOADProxy?.release()
        mOADProxy = OADManager.getOADProxy(
            LeProxy.instance.getService(),
            this,
            mSelectedOadType
        )
        appendLog("OADProxy created: ${mSelectedOadType.name}")
    }

    // -- Reset command -- //

    @SuppressLint("MissingPermission")
    private fun sendResetCommand() {
        val hexStr = binding.oadEdtReset.text.toString().trim()
        if (hexStr.isEmpty()) {
            ToastUtil.show(this, "Please enter a Reset command (HEX)")
            return
        }

        val data: ByteArray
        try {
            data = DataUtil.hexToByteArray(hexStr)
        } catch (e: Exception) {
            ToastUtil.show(this, "Invalid HEX: ${e.message}")
            return
        }

        val gatt = LeProxy.instance.getService()?.getBluetoothGatt(mDeviceAddress)
        if (gatt == null) {
            ToastUtil.show(this, "Device not connected")
            return
        }

        val resetService = gatt.getService(GattAttributes.TI_Reset_Service)
        val resetChar = resetService?.getCharacteristic(GattAttributes.TI_Reset)
        if (resetChar == null) {
            appendLog("Reset characteristic (ffd1) not found")
            return
        }

        resetChar.value = data
        resetChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val success = gatt.writeCharacteristic(resetChar)

        if (success) {
            mState = State.RESETTING
            binding.oadTvResetStatus.visibility = View.VISIBLE
            binding.oadTvResetStatus.setText(R.string.oad_reset_sent)
            binding.oadResetInputRow.visibility = View.GONE
            binding.oadBtnStart.isEnabled = false
            saveLastReset(hexStr)
            appendLog("Reset command sent: $hexStr — waiting for disconnect…")
        } else {
            appendLog("Failed to write Reset characteristic")
            ToastUtil.show(this, "Failed to send Reset command")
        }
    }

    // -- OADType display names -- //

    private fun oadTypeDisplayName(type: OADType): String = when (type) {
        OADType.cc2541_oad -> "CC2541 OAD"
        OADType.cc2541_large_img_oad -> "CC2541 Large Image OAD"
        OADType.cc2640_on_chip_oad -> "CC2640 On-Chip OAD"
        OADType.cc2640_off_chip_oad -> "CC2640 Off-Chip OAD"
        OADType.cc2640_r2_oad -> "CC2640 R2 OAD"
        OADType.cc2640_oad_2_0_0 -> "CC2640 OAD 2.0.0"
        OADType.phy6202_oad -> "PHY6202 OAD"
        OADType.large_mtu_oad -> "Large MTU OAD"
        OADType.cc23xx_oad -> "CC23xx OAD"
    }

    // -- activity lifecycle -- //

    private fun appendLog(s: String?) {
        runOnUiThread {
            s?.also { binding.logView.append(Log.DEBUG, it) }
        }
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_GATT_CONNECTED)
        filter.addAction(BtUtil.ACTION_GATT_DISCONNECTED)
        filter.addAction(BtUtil.ACTION_MTU_CHANGED)
        filter.addAction(BtUtil.ACTION_DATA_AVAILABLE)
        filter.addAction(BtUtil.ACTION_GATT_SERVICES_DISCOVERED)
        return filter
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            assets.list("")?.also {
                for (name in it) {
                    if (name.endsWith(".bin") || name.endsWith(".hexe")) {
                        mAssetsFiles.add(name)
                    }
                }
            }
            mAssetsFiles.add("本地文件")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mDeviceName = intent.getStringExtra(BtUtil.EXTRA_NAME)
        mDeviceAddress = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)
        initView()

        mState = State.CHECKING
        checkDeviceServices() // check immediately if services are already known

        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter())

        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun initView() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = mDeviceName
        supportActionBar?.subtitle = mDeviceAddress

        // OADType spinner
        val typeNames = OADType.values().map { oadTypeDisplayName(it) }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeNames)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.oadSpType.adapter = typeAdapter
        // Restore OADType for this MAC, or default to cc2640_r2_oad
        val savedOrdinal = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getInt(oadTypeKey(), OADType.cc2640_r2_oad.ordinal)
        mSelectedOadType = OADType.values().getOrElse(savedOrdinal) { OADType.cc2640_r2_oad }
        binding.oadSpType.setSelection(mSelectedOadType.ordinal)
        binding.oadSpType.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                mSelectedOadType = OADType.values()[pos]
                saveLastOadType(mSelectedOadType)
                // Restore firmware for this OADType, or clear if none saved
                mFilePath = null
                binding.oadTvFilepath.text = ""
                restoreLastFile()
                if (mOADProxy != null && mOADProxy?.isProgramming != true) {
                    createOADProxy()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // File & OAD start
        restoreLastFile()
        restoreLastReset()
        binding.oadBtnLoadFile.setOnClickListener(this)
        binding.oadBtnStart.setOnClickListener(this)
        updateProgressUi()

        // Reset send button hidden — bottom Start button handles it
        binding.oadBtnSendReset.visibility = View.GONE

        // Send interval
        val intervals = resources.getStringArray(R.array.oad_send_interval_values)
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        binding.oadSpSendInterval.adapter = intervalAdapter
        binding.oadSpSendInterval.setSelection(1) // default 20ms
        binding.oadSpSendInterval.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                val s = intervals[position]
                mSendInterval = Integer.valueOf(s.substring(0, s.indexOf("ms")))
                Log.i(TAG, "发送间隔：" + mSendInterval + "ms")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.oad, menu)
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
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_connect -> {
                val addr = mDeviceAddress ?: return true
                if (mIsConnected) {
                    LeProxy.instance.disconnect(addr)
                } else {
                    LeProxy.instance.connect(addr)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (mOADProxy?.isProgramming == true) {
                ToastUtil.show(this@OADActivity, R.string.oad_programming)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy()")
        mOADProxy?.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.oad_btn_load_file -> showLoadFileMenu()
            R.id.oad_btn_start -> {
                if (mOADProxy?.isProgramming == true) {
                    mOADProxy?.stopProgramming()
                } else when (mState) {
                    State.NEED_RESET -> sendResetCommand()
                    State.READY -> startOad()
                    else -> { /* CHECKING / RESETTING / RECONNECTING — do nothing */ }
                }
            }
        }
    }

    private fun startOad() {
        if (mFilePath == null) {
            ToastUtil.show(this, R.string.oad_please_select_a_image)
            return
        }
        try {
            val isAssets = mAssetsFiles.contains(mFilePath!!)
            mOADProxy?.prepare(mDeviceAddress, mFilePath, isAssets)
        } catch (e: Exception) {
            appendLog("OAD prepare failed: ${e.message}")
            ToastUtil.show(this, "OAD prepare failed: ${e.message}")
        }
    }

    /**
     * Show firmware file selection dialog.
     */
    private fun showLoadFileMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val menuList = ListView(this)
        menuList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mAssetsFiles)
        menuList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            if (position < mAssetsFiles.size - 1) {
                // Load assets file — clear any persisted local file
                clearLastFile()
                mFilePath = mAssetsFiles[position]
                binding.oadTvFilepath.text = mFilePath
            } else {
                // System file picker
                mOpenDocumentLauncher.launch(arrayOf("*/*"))
            }
            dialog.dismiss()
        }

        val width = (DimensUtil.getScreenWidth(this) * 0.8).toInt()
        dialog.setContentView(
            menuList,
            ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
    }

    /**
     * Copy the selected file from a content URI to a local temp file,
     * resolve its display name, and persist the selection.
     */
    private fun handleSelectedFile(uri: Uri) {
        try {
            // Resolve the display name from the content URI
            val fileName = queryFileName(uri) ?: uri.lastPathSegment ?: "firmware.bin"
            val tempFile = File(cacheDir, "oad_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            mFilePath = tempFile.absolutePath
            binding.oadTvFilepath.text = fileName

            // Persist so the user doesn't have to re-select next time
            saveLastFile(uri.toString(), fileName, tempFile.absolutePath)
            Log.i(TAG, "Selected file: $fileName → $mFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy selected file", e)
            ToastUtil.show(this, R.string.oad_please_select_a_image)
        }
    }

    /** Query the display name from a content URI via the ContentResolver. */
    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    /**
     * Restore the previously-selected local file for the current OADType.
     * If the cached file still exists, use it directly. Otherwise
     * try to re-copy from the persisted URI.
     */
    private fun restoreLastFile() {
        if (mFilePath != null) return // already set (e.g. from assets selection)

        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val savedPath = prefs.getString(filePathKey(), null) ?: return
        val savedName = prefs.getString(fileNameKey(), null) ?: "firmware.bin"
        val savedUri = prefs.getString(fileUriKey(), null)

        val cachedFile = File(savedPath)
        if (cachedFile.exists()) {
            mFilePath = savedPath
            binding.oadTvFilepath.text = savedName
            Log.i(TAG, "Restored cached file: $savedName")
            return
        }

        // Cached file gone — try to re-copy from URI
        if (savedUri != null) {
            try {
                val uri = Uri.parse(savedUri)
                contentResolver.openInputStream(uri)?.use { input ->
                    cachedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                mFilePath = cachedFile.absolutePath
                binding.oadTvFilepath.text = savedName
                Log.i(TAG, "Re-copied file from saved URI: $savedName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-copy saved URI, clearing prefs", e)
                clearLastFile()
            }
        }
    }

    /** Save the Reset command so the user doesn't have to re-type it. */
    private fun saveLastReset(cmd: String) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit {
                putString(PREF_RESET_CMD, cmd)
            }
    }

    // -- pref key helpers: OADType per MAC, firmware per OADType -- //

    private fun oadTypeKey() = PREF_OAD_TYPE + mDeviceAddress
    private fun fileUriKey() = PREF_FILE_URI + mSelectedOadType.ordinal
    private fun fileNameKey() = PREF_FILE_NAME + mSelectedOadType.ordinal
    private fun filePathKey() = PREF_FILE_PATH + mSelectedOadType.ordinal

    /** Save the selected OADType for the current device MAC. */
    private fun saveLastOadType(type: OADType) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit {
                putInt(oadTypeKey(), type.ordinal)
            }
    }

    /** Save the selected local file info per OADType (NOT per MAC). */
    private fun saveLastFile(uri: String, name: String, path: String) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit {
                putString(fileUriKey(), uri)
                    .putString(fileNameKey(), name)
                    .putString(filePathKey(), path)
            }
    }

    /** Restore the last-used Reset command. */
    private fun restoreLastReset() {
        val cmd = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(PREF_RESET_CMD, null)
        if (!cmd.isNullOrEmpty()) {
            binding.oadEdtReset.setText(cmd)
        }
    }

    /** Clear persisted file info for the current OADType. */
    private fun clearLastFile() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit {
                remove(fileUriKey())
                    .remove(fileNameKey())
                    .remove(filePathKey())
            }
    }

    // -- OADListener callbacks -- //

    override fun onPrepared(address: String) {
        mBlockCount = 0
        runOnUiThread {
            mState = State.PROGRAMMING
            binding.oadBtnStart.setText(R.string.oad_cancel)
            mOADProxy?.startProgramming(mSendInterval)
        }
        appendLog("OAD Prepared")
    }

    override fun onFinished(address: String, nBytes: Int, milliseconds: Long) {
        runOnUiThread {
            displayData(nBytes, nBytes, milliseconds)
            binding.oadBtnStart.setText(R.string.oad_start)
            mState = State.READY
        }
    }

    override fun onInterrupted(
        address: String,
        iBytes: Int,
        nBytes: Int,
        milliseconds: Long
    ) {
        runOnUiThread {
            displayData(nBytes, nBytes, milliseconds)
            binding.oadBtnStart.setText(R.string.oad_start)
            mState = State.READY
        }
    }

    override fun onProgressChanged(
        address: String,
        iBytes: Int,
        nBytes: Int,
        milliseconds: Long
    ) {
        runOnUiThread { displayData(iBytes, nBytes, milliseconds) }
    }

    private var mBlockCount = 0

    override fun onBlockWrite(data: ByteArray) {
        mBlockCount++
        appendLog("->[#$mBlockCount] ${DataUtil.byteArrayToHex(data)}")
    }

    override fun onStatusChange(address: String, status: Int) {
        if (status == OADStatus.SUCCESS) {
            Log.i(TAG, "升级成功")
        } else {
            Log.e(TAG, "升级异常：" + OADStatus.getMessage(status))
        }
        val s = "$status [${OADStatus.getMessage(status)}]"
        appendLog(s)
    }
}
