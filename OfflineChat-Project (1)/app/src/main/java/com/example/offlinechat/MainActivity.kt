package com.example.offlinechat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var serviceId: String
    private lateinit var key: SecretKeySpec
    private lateinit var myName: String
    private lateinit var advertName: String
    private lateinit var client: ConnectionsClient
    private val connected = mutableSetOf<String>()
    private val messages = mutableListOf<String>()
    private val seen = mutableSetOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name")
        val pass = intent.getStringExtra("pass")
        if (name.isNullOrBlank() || pass.isNullOrEmpty()) { finish(); return }

        myName = name
        advertName = name + "#" + (1000..9999).random()
        serviceId = Crypto.serviceId(pass)
        key = Crypto.key(pass)

        setContentView(R.layout.activity_main)
        client = Nearby.getConnectionsClient(this)
        status = findViewById(R.id.status)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messages)
        findViewById<ListView>(R.id.list).adapter = adapter

        val input = findViewById<EditText>(R.id.input)
        findViewById<Button>(R.id.send).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (connected.isEmpty()) {
                Toast.makeText(this, "Abhi koi connected nahi hai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val msgId = java.util.UUID.randomUUID().toString()
            seen += msgId
            val plain = "$msgId|$myName|$text".toByteArray()
            client.sendPayload(connected.toList(), Payload.fromBytes(Crypto.encrypt(key, plain)))
            addMessage("Me: $text")
            input.text.clear()
        }

        if (hasPermissions()) startNearby()
        else ActivityCompat.requestPermissions(this, requiredPermissions(), 1)
    }

    private fun requiredPermissions(): Array<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_ADVERTISE
            p += Manifest.permission.BLUETOOTH_CONNECT
            p += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPermissions()) startNearby()
        else status.text = "Permissions chahiye, bina inke app nahi chalega"
    }

    private fun startNearby() {
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            advertName, serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        )
        client.startDiscovery(
            serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        )
        status.text = "Login: $myName | Group ke log dhundh raha hai..."
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (advertName > info.endpointName) {
                client.requestConnection(advertName, id, connectionCallback)
            }
        }
        override fun onEndpointLost(id: String) {}
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            client.acceptConnection(id, payloadCallback)
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected += id
                status.text = "Login: $myName | Connected: ${connected.size}"
            }
        }
        override fun onDisconnected(id: String) {
            connected -= id
            status.text = "Login: $myName | Connected: ${connected.size}"
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            val raw = payload.asBytes() ?: return
            // Galat password / chhedkhani wala data yahin ignore ho jata hai
            val plain = Crypto.decrypt(key, raw) ?: return
            val parts = String(plain).split("|", limit = 3)
            if (parts.size < 3) return
            if (!seen.add(parts[0])) return
            addMessage("${parts[1]}: ${parts[2]}")
            val others = connected.filter { it != id }
            if (others.isNotEmpty()) client.sendPayload(others, Payload.fromBytes(raw))
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    private fun addMessage(msg: String) = runOnUiThread {
        messages += msg
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        if (::client.isInitialized) {
            client.stopAllEndpoints()
            client.stopAdvertising()
            client.stopDiscovery()
        }
        super.onDestroy()
    }
}
