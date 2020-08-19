package com.ble.demo

import com.ble.ble.LeScanRecord
import com.ble.ble.scan.LeScanResult

class LeDevice {
    val address: String
    var name: String?
    var rxData = "No data"
    var leScanRecord: LeScanRecord? = null
    var isOadSupported = false

    constructor(name: String?, address: String) {
        this.name = name
        this.address = address
    }

    constructor(result: LeScanResult) {
        this.name = result.device.name
        this.address = result.device.address
        this.leScanRecord = result.leScanRecord
    }

    override fun equals(o: Any?): Boolean {
        return if (o is LeDevice) {
            o.address == address
        } else false
    }
}