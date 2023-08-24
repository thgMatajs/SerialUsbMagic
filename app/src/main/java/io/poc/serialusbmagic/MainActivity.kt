package io.poc.serialusbmagic

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException

private enum class UsbPermission {
    Unknown, Requested, Granted, Denied
}

data class ListItem(var device: UsbDevice, var port: Int, var driver: UsbSerialDriver?)

class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener {

    private var deviceId = 0
    private var portNum = 0
    private var baudRate = 9600


    private var withIoManager = false
    private val broadcastReceiver: BroadcastReceiver

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    private var connected = false

    private val deviceAdapter = DeviceAdapter() { item ->
        if (item.driver == null) {
            Toast.makeText(this, "no driver", Toast.LENGTH_SHORT).show()
        } else {
            deviceId = item.device.deviceId
            portNum = item.port
        }
        connect()
        send()
    }
    private val listItems = ArrayList<ListItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getDevice()
        connect()
        findViewById<RecyclerView>(R.id.rv).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }
    }

    init {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (INTENT_ACTION_GRANT_USB == intent.action) {
                    usbPermission = if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false
                        )
                    ) UsbPermission.Granted else UsbPermission.Denied
                    connect()
                }
            }
        }
    }

    private fun getDevice() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDefaultProber = UsbSerialProber.getDefaultProber()
        val usbCustomProber = CustomProber.customProber
        listItems.clear()
        for (device in usbManager.deviceList.values) {
            var driver = usbDefaultProber.probeDevice(device)
            if (driver == null) {
                driver = usbCustomProber.probeDevice(device)
            }
            if (driver != null) {
                for (port in driver.ports.indices) listItems.add(ListItem(device, port, driver))
            } else {
                listItems.add(ListItem(device, 0, null))
            }
            deviceAdapter.submitList(listItems)
        }
    }

    private fun connect() {
        var device: UsbDevice? = null
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        if (device == null) {
            status("connection failed: device not found")
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.customProber.probeDevice(device)
        }
        if (driver == null) {
            status("connection failed: no driver for device")
            return
        }
        if (driver.ports.size < portNum) {
            status("connection failed: not enough ports at device")
            return
        }
        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(
                driver.device
            )
        ) {
            usbPermission = UsbPermission.Requested
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
            val usbPermissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(
                    INTENT_ACTION_GRANT_USB
                ), flags
            )
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.device)) status("connection failed: permission denied") else status(
                "connection failed: open failed"
            )
            return
        }
        try {
            usbSerialPort!!.open(usbConnection)
            try {
                usbSerialPort!!.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE)
            } catch (e: UnsupportedOperationException) {
                status("unsupport setparameters")
            }
            if (withIoManager) {
                usbIoManager = SerialInputOutputManager(usbSerialPort, this)
                usbIoManager!!.start()
            }
            status("connected")
            connected = true
        } catch (e: Exception) {
            status("connection failed: " + e.message)
            disconnect()
        }
    }

    private fun disconnect() {
        connected = false
        if (usbIoManager != null) {
            usbIoManager!!.listener = null
            usbIoManager!!.stop()
        }
        usbIoManager = null
        try {
            usbSerialPort!!.close()
        } catch (ignored: IOException) {
        }
        usbSerialPort = null
    }

    private fun status(str: String) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show()
        val spn = SpannableStringBuilder(
            """
    $str
    
    """
        )

        println("CLI: STATUS -> :$str , spn: $spn")

    }

    private fun send() {
        if (!connected) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val commands = listOf<String>(
                "admin",
                "admin",
                "configure",
                "set system time-zone Brazil/East",
                "set system host-name diadema",
                "commit",
                "exit",
                "exit"
            )
            commands.forEach { command ->
                val data = """$command
""".toByteArray()
                usbSerialPort!!.write(data, WRITE_WAIT_MILLIS)

            }
        } catch (e: Exception) {
            onRunError(e)
        }
    }

    private fun receive(data: ByteArray) {
        val spn = SpannableStringBuilder()
        spn.append(
            """receive ${data.size} bytes
"""
        )
        if (data.isNotEmpty()) spn.append(HexDump.dumpHexString(data)).append("\n")
        println("CLI -> receive $spn")
    }

    override fun onNewData(data: ByteArray) {
        receive(data)
    }

    override fun onRunError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(broadcastReceiver, IntentFilter(INTENT_ACTION_GRANT_USB))
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted) connect()
    }

    companion object {
        private const val refreshInterval = 200 // msec
        private const val INTENT_ACTION_GRANT_USB = "io.poc.serialusbmagic.GRANT_USB"
        private const val WRITE_WAIT_MILLIS = 2000
        private const val READ_WAIT_MILLIS = 2000
    }
}