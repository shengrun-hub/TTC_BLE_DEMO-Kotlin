package com.ble.demo.ui

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ble.api.DataUtil
import com.ble.ble.oad.*
import com.ble.demo.MainActivity
import com.ble.demo.R
import com.ble.demo.util.BtUtil
import com.ble.gatt.GattAttributes
import com.ble.utils.DimensUtil
import com.ble.utils.ToastUtil
import com.ttcble.leui.LeProxy
import kotlinx.android.synthetic.main.activity_oad.*
import java.util.*

/**
 * CC2541的OAD需判断镜像类别是A还是B，只有类别不同才可以升级
 *
 *
 * 本Demo给出的几个发送间隔仅供参考
 */
class OADActivity : AppCompatActivity(), View.OnClickListener, OADListener {
    companion object {
        private const val TAG = "OADActivity"

        private const val REQ_FILE_PATH = 1
    }

    private val mAssetsFiles: MutableList<String> = ArrayList()


    private var mSendInterval = 20 //发送间隔
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mFilePath: String? = null
    private val mProgressInfo = ProgressInfo()
    private var mOADProxy: OADProxy? = null//升级的关键类


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
        val bytes =
            (mProgressInfo.iBytes / 1024).toString() + "KB/" + mProgressInfo.nBytes / 1024 + "KB"
        oad_tv_progress?.text = "$progress%"
        oad_tv_time?.text = time
        oad_tv_bytes?.text = bytes
        oad_progressBar?.progress = progress
    }

    private val mLocalReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(BtUtil.EXTRA_ADDRESS)

            if (address != mDeviceAddress) return

            var s: String? = null
            when (intent.action) {
                BtUtil.ACTION_GATT_DISCONNECTED -> {
                    oad_tv_state?.setText(R.string.disconnected)
                    s = "Disconnected"
                }
                BtUtil.ACTION_GATT_CONNECTED -> {
                    oad_tv_state?.setText(R.string.connected)
                    s = "Connected"
                }
                BtUtil.ACTION_DATA_AVAILABLE -> {
                    val uuid =
                        intent.getStringExtra(BtUtil.EXTRA_UUID)
                    val data =
                        intent.getByteArrayExtra(BtUtil.EXTRA_DATA)
                    if (GattAttributes.TI_OAD_Image_Identify.toString() == uuid) {
                        val ver = DataUtil.buildUint16(data[1], data[0]).toInt().and(0xFFFF)
                        val imgType = if (ver and 1 == 1) 'B' else 'A'
                        // 显示模块当前程序的镜像类型（A/B）
                        oad_tv_image_type?.text = "Target Image Type: $imgType"
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

    private fun appendLog(s: String?) {
        runOnUiThread {
            s?.also {
                logView?.append(Log.DEBUG, it)
            }
        }
    }

    private fun makeFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BtUtil.ACTION_GATT_CONNECTED)
        filter.addAction(BtUtil.ACTION_GATT_DISCONNECTED)
        filter.addAction(BtUtil.ACTION_MTU_CHANGED)
        filter.addAction(BtUtil.ACTION_DATA_AVAILABLE)
        return filter
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oad)
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
        mDeviceName = intent.getStringExtra(MainActivity.EXTRA_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(MainActivity.EXTRA_DEVICE_ADDRESS)
        initView()

        /*if (Build.VERSION.SDK_INT >= 21) {
            val gatt = mLeProxy.getBluetoothGatt(mDeviceAddress)
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        }*/


        mOADProxy = OADManager.getOADProxy(
            LeProxy.instance.getService(),
            this,
            OADType.cc2640_r2_oad
        ) //TODO 升级类型，依模块型号而定
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter())

        //这一步只有CC2541 OAD才需要
        //Timer().schedule(GetTargetImgInfoTask(mDeviceAddress), 100, 100)
    }

    private fun initView() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = mDeviceName
        supportActionBar?.subtitle = mDeviceAddress

        oad_tv_filepath?.text = mFilePath
        oad_btn_start?.setOnClickListener(this)
        updateProgressUi()
        oad_btn_load_file.setOnClickListener(this)

        // 发送间隔
        val intervals = resources.getStringArray(R.array.oad_send_interval_values)
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        oad_sp_send_interval.adapter = intervalAdapter
        oad_sp_send_interval.setSelection(1) // 默认20ms
        oad_sp_send_interval.onItemSelectedListener = object : OnItemSelectedListener {
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

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }


    private inner class GetTargetImgInfoTask(address: String?) : TimerTask() {
        var i = 0
        var gatt: BluetoothGatt? = null
        var charIdentify: BluetoothGattCharacteristic? = null
        var charBlock: BluetoothGattCharacteristic? = null

        init {
            LeProxy.instance.getService()?.let {
                gatt = it.getBluetoothGatt(address)
                if (gatt != null) {
                    val serv = gatt!!.getService(GattAttributes.TI_OAD_Service)
                    if (serv != null) {
                        charIdentify = serv.getCharacteristic(GattAttributes.TI_OAD_Image_Identify)
                        charIdentify?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        it.setCharacteristicNotification(gatt, charIdentify, true)
                        charBlock = serv.getCharacteristic(GattAttributes.TI_OAD_Image_Block)
                    }
                }
            }
        }

        override fun run() {
            if (charIdentify != null) {
                when (i) {
                    0 -> {
                        charIdentify!!.value = byteArrayOf(0)
                        Log.e(TAG, "write 0: " + gatt!!.writeCharacteristic(charIdentify))
                    }
                    1 -> {
                        charIdentify!!.value = byteArrayOf(1)
                        Log.e(TAG, "write 1: " + gatt!!.writeCharacteristic(charIdentify))
                    }
                    else -> Log.w(TAG, "\$GetTargetImgInfoTask.cancel(): " + cancel())
                }
            } else {
                cancel()
            }
            i++
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // 重写返回键事件
        if (mOADProxy!!.isProgramming) {
            ToastUtil.show(this, R.string.oad_programming)
        } else {
            super.onBackPressed()
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
            R.id.oad_btn_start -> if (mOADProxy!!.isProgramming) {
                // 取消升级
                mOADProxy!!.stopProgramming()
            } else {
                // 开始升级
                if (mFilePath != null) {
                    val isAssets = mAssetsFiles.contains(mFilePath!!)
                    mOADProxy!!.prepare(mDeviceAddress, mFilePath, isAssets)
                } else {
                    ToastUtil.show(this, R.string.oad_please_select_a_image)
                }
            }
        }
    }

    /**
     * 加载升级文件
     */
    private fun showLoadFileMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val menuList = ListView(this)
        menuList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mAssetsFiles)
        menuList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            if (position < mAssetsFiles.size - 1) {
                // 加载assets文件
                mFilePath = mAssetsFiles[position]
                oad_tv_filepath?.text = mFilePath
            } else {
                // 加载本地文件（Download目录）
                val intent = Intent(this, FileActivity::class.java)
                startActivityForResult(intent, REQ_FILE_PATH)
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

    public override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (resultCode == Activity.RESULT_OK && requestCode == REQ_FILE_PATH) {
            val filepath = data!!.getStringExtra(FileActivity.EXTRA_FILE_PATH)
            if (filepath != null) {
                mFilePath = filepath
                oad_tv_filepath?.text = mFilePath
            }
            Log.e(TAG, "########### $mFilePath")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPrepared(address: String) {
        // 准备就绪，开始升级
        runOnUiThread {
            oad_btn_start.setText(R.string.oad_cancel)
            // 准备就绪，开始升级
            mOADProxy?.startProgramming(mSendInterval)
        }
        appendLog("OAD Prepared")
    }

    override fun onFinished(
        address: String,
        nBytes: Int,
        milliseconds: Long
    ) {
        // 升级完毕，这里只是APP端发送完所有有数据
        runOnUiThread {
            displayData(nBytes, nBytes, milliseconds)
            oad_btn_start.setText(R.string.oad_start)
        }
    }

    override fun onInterrupted(
        address: String,
        iBytes: Int,
        nBytes: Int,
        milliseconds: Long
    ) {
        // 升级异常中断
        runOnUiThread {
            displayData(nBytes, nBytes, milliseconds)
            oad_btn_start.setText(R.string.oad_start)
        }
    }

    override fun onProgressChanged(
        address: String,
        iBytes: Int,
        nBytes: Int,
        milliseconds: Long
    ) {
        // 升级进度
        runOnUiThread {
            displayData(iBytes, nBytes, milliseconds)
        }
    }

    override fun onBlockWrite(arg0: ByteArray) {
        //升级过程中发的数据，可忽略
    }

    // R2 OAD 才有状态回调
    override fun onStatusChange(address: String, status: Int) {
        if (status == OADStatus.SUCCESS) {
            Log.i(TAG, "升级成功")
        } else {
            Log.e(TAG, "升级异常：" + OADStatus.getMessage(status))
        }

        val s = "$status [" + OADStatus.getMessage(status) + "]"
        appendLog(s)
    }
}