package com.ble.demo.ui

import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ble.api.DataUtil
import com.ble.ble.scan.*
import com.ble.demo.LeDevice
import com.ble.demo.R
import com.ble.utils.DimensUtil
import com.ttcble.leui.LeProxy
import kotlinx.android.synthetic.main.fragment_scan.*

class ScanFragment : Fragment(), OnLeScanListener {

    private val mDeviceAdapter = DeviceAdapter()


    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                if (state == BluetoothAdapter.STATE_ON) {
                    scanDevices()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        activity?.registerReceiver(mReceiver, filter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshLayout?.setOnRefreshListener {
            mDeviceAdapter.clear()
            scanDevices()
        }

        scanDeviceListView.layoutManager = LinearLayoutManager(activity)
        scanDeviceListView.adapter = mDeviceAdapter
    }

    override fun onStart() {
        super.onStart()
        scanDevices()
    }

    private fun scanDevices() {
        LeScanner.requestScan(this, REQ_SCAN_DEVICE, object : ScanRequestCallback() {
            override fun onBluetoothDisabled() {
                LeScanner.requestEnableBluetooth(requireActivity())
            }

            override fun shouldShowPermissionRationale(request: ScanPermissionRequest) {
                showPermissionDialog { _, _ ->
                    request.proceed()
                }
            }

            override fun onPermissionDenied() {
                showPermissionDialog { _, _ ->
                    LeScanner.startAppDetailsActivity(requireActivity())
                }
            }

            override fun onLocationServiceDisabled() {
                //Android12以下系统才会触发该回调方法
                showLocationServiceDialog()
            }

            override fun onReady() {
                //准备就绪，开始扫描
                LeScanner.startScan(this@ScanFragment)
            }
        })
    }

    private fun showPermissionDialog(listener: DialogInterface.OnClickListener) {
        val msg = if (Build.VERSION.SDK_INT < 31) {
            R.string.scan_tips_no_location_permission
        } else {
            R.string.scan_tips_no_location_permission
        }
        AlertDialog.Builder(requireActivity())
            .setCancelable(false)
            .setMessage(msg)
            .setPositiveButton(R.string.proceed, listener)
            .setNegativeButton(R.string.cancel) { _, _ -> /**/ }
            .show()
    }

    //Android12以下系统需要开启位置服务
    private fun showLocationServiceDialog() {
        AlertDialog.Builder(requireActivity())
            .setCancelable(false)
            .setMessage(R.string.scan_tips_location_service_disabled)
            .setPositiveButton(R.string.proceed) { _, _ ->
                LeScanner.requestEnableLocation(
                    requireActivity()
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> /**/ }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //todo LeScanner.requestScan() 请求扫描需要处理权限结果
        LeScanner.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onPause() {
        super.onPause()
        LeScanner.stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.unregisterReceiver(mReceiver)
    }


    /**
     * 显示广播数据
     */
    private fun showAdvDetailsDialog(device: LeDevice) {
        val record = device.leScanRecord
        val sb = StringBuilder()
            .append(device.name)
            .append("\n")
            .append(device.address)
            .append("\n\n")
            .append("[${DataUtil.byteArrayToHex(record?.bytes)}]")
            .append("\n\n")
            .append(record.toString())

        AlertDialog.Builder(requireActivity())
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }


    override fun onScanStart() {
    }

    /**
     * 扫描到蓝牙设备
     */
    override fun onLeScan(leScanResult: LeScanResult) {
        val name = leScanResult.leScanRecord.localName
        Log.i(TAG, "onLeScan() - " + leScanResult.device + " [" + name + "]")
        activity?.runOnUiThread {
            mDeviceAdapter.addDevice(LeDevice(leScanResult))
        }
    }

    override fun onScanFailed(errorCode: Int) {
    }

    override fun onScanStop() {
        activity?.runOnUiThread {
            refreshLayout?.isRefreshing = false
        }
    }


    private inner class DeviceAdapter : RecyclerView.Adapter<MyHolder>() {
        val deviceList = ArrayList<LeDevice>()

        fun addDevice(device: LeDevice) {
            if (!deviceList.contains(device)) {
                deviceList.add(device)
                notifyDataSetChanged()
            }
        }

        fun clear() {
            deviceList.clear()
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return deviceList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
            val view = layoutInflater.inflate(R.layout.item_device_list, null)
            val width = DimensUtil.getScreenWidth(parent.context)
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams = ViewGroup.LayoutParams(width, height)
            return MyHolder(view)
        }

        override fun onBindViewHolder(holder: MyHolder, position: Int) {
            val device = deviceList[position]
            val deviceName = device.name
            if (!TextUtils.isEmpty(deviceName)) {
                holder.deviceName.text = deviceName
            } else {
                holder.deviceName.setText(R.string.unknown_device)
            }
            holder.deviceAddress.text = device.address
            holder.itemView.setOnClickListener {
                /**
                 * 点击连接设备
                 */
                LeScanner.stopScan()
                LeProxy.instance.connect(device.address)
            }
            holder.itemView.setOnLongClickListener {
                /**
                 * 长按显示广播数据
                 */
                showAdvDetailsDialog(device)
                true
            }
        }
    }

    private class MyHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView
        val deviceAddress: TextView
        val connect: TextView

        init {
            deviceAddress = view.findViewById(R.id.device_address)
            deviceName = view.findViewById(R.id.device_name)
            connect = view.findViewById(R.id.btn_connect)
            connect.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "ScanFragment"

        private const val REQ_SCAN_DEVICE = 11
    }
}