package in.silken.cretadiag

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/** READ-ONLY OBD-II diagnostic prototype. Never sends manufacturer-specific commands. */
class MainActivity : Activity() {
    private val bg = Color.rgb(12, 18, 30)
    private val panel = Color.rgb(25, 35, 53)
    private val accent = Color.rgb(53, 206, 174)
    private val muted = Color.rgb(170, 185, 204)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var adapter: BluetoothAdapter? = null
    private var isBusy = false
    private var isConnected = false
    private lateinit var deviceList: LinearLayout
    private lateinit var status: TextView
    private lateinit var info: TextView
    private lateinit var log: TextView
    private lateinit var rpm: TextView
    private lateinit var speed: TextView
    private lateinit var volts: TextView
    private lateinit var testButton: Button
    private lateinit var disconnectButton: Button
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(28),dp(20),dp(28)) }
        scroll.addView(root)
        root.addView(text("CRETA  /  DIAGNOSTIC", 13, accent, true))
        root.addView(text("Steering health check", 27, Color.WHITE, true))
        root.addView(text("2019 Diesel  •  Android  •  Bluetooth OBD", 14, muted))
        root.addView(section("Connection"))
        status = text("Not connected", 16, Color.WHITE, true)
        root.addView(status)
        root.addView(text("Pair the CaRPM scanner in Android Bluetooth Settings first. This version supports Classic Bluetooth SPP only, if the scanner exposes it.", 13, muted))
        root.addView(button("Show paired Bluetooth devices") { showDevices() })
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceList)
        root.addView(section("Live vehicle data"))
        rpm = dataText("RPM", "—")
        speed = dataText("Vehicle speed", "—")
        volts = dataText("ECU voltage (PID 0142)", "—")
        root.addView(rpm); root.addView(speed); root.addView(volts)
        testButton = button("Run read-only OBD test") { diagnosticTest() }
        testButton.isEnabled = false
        root.addView(testButton)
        disconnectButton = button("Disconnect") { disconnect() }
        disconnectButton.isEnabled = false
        root.addView(disconnectButton)
        root.addView(section("EPS / Steering angle"))
        info = text("Unavailable: ECU-specific EPS/SAS protocols not verified. No steering calibration or coding commands are sent.", 14, muted)
        root.addView(info)
        root.addView(section("Why the Creta pulls left"))
        root.addView(text("Check tyre pressure and tyre conicity, four-wheel alignment (especially cross-camber/caster), brake drag, suspension and steering centering. Steering zeroing alone may not solve pulling.", 14, Color.WHITE))
        root.addView(section("Communication log"))
        log = text("Ready. No commands sent.", 12, muted).apply { typeface = Typeface.MONOSPACE; setPadding(0,dp(8),0,dp(8)) }
        root.addView(log)
        root.addView(text("Safety: test only while safely parked, ignition on, parking brake applied. No calibration features in this build.", 12, accent))
        setContentView(scroll)
        permissionCheck()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(v: String, size: Int, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = v; textSize = size.toFloat(); setTextColor(c); setPadding(0,dp(6),0,dp(6)); if (bold) setTypeface(null,Typeface.BOLD)
    }
    private fun section(s: String) = text(s.uppercase(), 13, accent, true).apply { setPadding(0,dp(27),0,dp(8)) }
    private fun dataText(label: String, value: String): TextView = text("$label:  $value", 17, Color.WHITE, true).apply {
        setBackgroundColor(panel); setPadding(dp(15),dp(13),dp(15),dp(13));
        val lp = LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); layoutParams=lp
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text=label; isAllCaps=false; setOnClickListener { action() }
    }
    private fun permissionCheck(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 51); return false
        }
        return true
    }
    @Suppress("MissingPermission")
    private fun showDevices() {
        if (!permissionCheck()) return
        deviceList.removeAllViews()
        val bonded = try { adapter?.bondedDevices?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        if (bonded.isEmpty()) deviceList.addView(text("No paired devices. Pair CaRPM in Bluetooth settings, then retry.", 14, muted))
        bonded.sortedBy { it.name ?: "" }.forEach { device ->
            val label = "Connect: ${device.name ?: "Unnamed device"} (${device.address})"
            deviceList.addView(button(label) { connect(device) })
        }
    }
    @Suppress("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!permissionCheck() || isBusy) return
        isBusy=true
        status.text="Connecting to ${device.name ?: "OBD adapter"}…"
        worker.execute {
            try {
                closeSocket()
                val s = device.createRfcommSocketToServiceRecord(spp)
                s.connect() // blocking worker thread
                socket=s; input=s.inputStream; output=s.outputStream
                val reset=command("ATZ", 3000)
                val echo=command("ATE0", 1500)
                val line=command("ATL0", 1500)
                val space=command("ATS0", 1500)
                val protocol=command("ATSP0", 2000)
                val id=command("ATI", 1500)
                val engine=command("0100", 3500)
                val compatible = !engine.contains("NO DATA", true) && Regex("(?i)41\\s*00").containsMatchIn(engine)
                ui {
                    isConnected=true; status.text="Bluetooth connected • ${device.name ?: "OBD"}"
                    testButton.isEnabled=true; disconnectButton.isEnabled=true
                    appendLog("ATZ: $reset\nATE0: $echo\nATL0: $line\nATS0: $space\nATSP0: $protocol\nATI: $id\n0100: $engine")
                    if (!compatible) appendLog("No standard PID 0100 reply. CaRPM firmware may require a different protocol; do not assume compatibility.")
                }
            } catch (e: Exception) {
                closeSocket()
                ui { isConnected=false; status.text="Connection failed"; appendLog("Connection error: ${e.message ?: e.javaClass.simpleName}") }
            } finally { isBusy=false }
        }
    }
    private fun diagnosticTest() {
        if (!isConnected || isBusy) return
        isBusy=true; testButton.isEnabled=false
        worker.execute {
            try {
                val r=command("010C", 3000)
                val s=command("010D", 3000)
                val v=command("0142", 3000)
                val rr=parsePid(r,"0C",2)?.let { ((it[0]*256+it[1])/4.0).toString()+" rpm" } ?: "Not supported"
                val ss=parsePid(s,"0D",1)?.let { "${it[0]} km/h" } ?: "Not supported"
                val vv=parsePid(v,"42",2)?.let { "%.2f V".format((it[0]*256+it[1])/1000.0) } ?: "Not supported"
                ui { rpm.text="RPM:  $rr"; speed.text="Vehicle speed:  $ss"; volts.text="ECU voltage (PID 0142):  $vv"; appendLog("010C: $r\n010D: $s\n0142: $v") }
            } catch (e: Exception) { ui { appendLog("Read error: ${e.message}") } }
            finally { ui { testButton.isEnabled=isConnected }; isBusy=false }
        }
    }
    /** Only standard OBD modes 01 and safe ELM setup commands are used. */
    private fun command(cmd: String, timeoutMs: Long): String {
        require(cmd in setOf("ATZ","ATE0","ATL0","ATS0","ATSP0","ATI","0100","010C","010D","0142"))
        val out=output ?: error("Not connected")
        val ins=input ?: error("Not connected")
        out.write((cmd+"\r").toByteArray(Charsets.US_ASCII)); out.flush()
        val bytes=StringBuilder(); val end=System.currentTimeMillis()+timeoutMs
        while (System.currentTimeMillis()<end) {
            if (ins.available()>0) {
                val n=ins.read()
                if(n<0) break
                if(n=='>'.code) break
                if(bytes.length<4096) bytes.append(n.toChar())
            } else Thread.sleep(25)
        }
        return bytes.toString().trim().replace("\r", " ").replace("\n", " ").take(450)
    }
    private fun parsePid(raw:String, pid:String, count:Int):List<Int>? {
        val stripped=raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val prefix="41$pid"
        val start=stripped.indexOf(prefix)
        if(start<0 || start+prefix.length+count*2>stripped.length) return null
        return try { (0 until count).map { stripped.substring(start+prefix.length+it*2,start+prefix.length+it*2+2).toInt(16) } } catch (_:Exception) { null }
    }
    private fun disconnect() {
        closeSocket(); isConnected=false; status.text="Disconnected"; testButton.isEnabled=false; disconnectButton.isEnabled=false; appendLog("Disconnected")
    }
    private fun closeSocket() { try { socket?.close() } catch (_:Exception) {} ; socket=null; input=null; output=null }
    private fun appendLog(t:String) { log.text=(log.text.toString()+"\n\n"+t).takeLast(4500) }
    private fun ui(block:()->Unit) { main.post(block) }
    override fun onDestroy() { closeSocket(); worker.shutdownNow(); super.onDestroy() }
}
