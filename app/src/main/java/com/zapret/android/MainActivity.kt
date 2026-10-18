package com.zapret.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zapret.android.core.CiadpiArgs
import com.zapret.android.core.Strategies
import com.zapret.android.data.Prefs
import com.zapret.android.databinding.ActivityMainBinding
import com.zapret.android.util.Signals

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ZapretVpnService.start(this)
        } else {
            Toast.makeText(this, R.string.toast_vpn_permission_denied, Toast.LENGTH_LONG).show()
        }
        binding.root.postDelayed({ refreshState() }, 400)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        setupStrategySpinner()
        setupSettings()
        setupMode()
        binding.buttonToggle.setOnClickListener { onToggleClicked() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(Signals.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshState()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(stateReceiver)
    }

    override fun onPause() {
        super.onPause()
        persistInputFields()
    }

    private fun setupStrategySpinner() {
        val titles = Strategies.ALL.map { getString(it.titleRes) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStrategy.adapter = adapter
        binding.spinnerStrategy.setSelection(
            Strategies.ALL.indexOfFirst { it.id == prefs.strategyId }.coerceAtLeast(0)
        )
        binding.spinnerStrategy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val strategy = Strategies.ALL[position]
                binding.tvStrategyDesc.text = getString(strategy.descriptionRes)
                val changed = prefs.strategyId != strategy.id
                prefs.strategyId = strategy.id
                if (changed) restartIfRunning()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupSettings() {
        binding.etExtraArgs.setText(prefs.extraArgs)
        binding.etExtraArgs.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etExtraArgs.text?.toString()?.trim() ?: ""
                if (prefs.extraArgs != value) {
                    prefs.extraArgs = value
                    restartIfRunning()
                }
            }
        }

        binding.swUdp.isChecked = prefs.udp
        binding.swUdp.setOnCheckedChangeListener { _, checked ->
            if (prefs.udp != checked) {
                prefs.udp = checked
                restartIfRunning()
            }
        }

        binding.swIpv6.isChecked = prefs.ipv6
        binding.swIpv6.setOnCheckedChangeListener { _, checked ->
            if (prefs.ipv6 != checked) {
                prefs.ipv6 = checked
                restartIfRunning()
            }
        }

        binding.swBoot.isChecked = prefs.startOnBoot
        binding.swBoot.setOnCheckedChangeListener { _, checked ->
            prefs.startOnBoot = checked
        }

        binding.etDns.setText(prefs.dns)
        binding.etDns.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveDns()
            }
        }
    }

    private fun setupMode() {
        binding.rbMode.check(if (prefs.mode == Prefs.MODE_PROXY) R.id.rbProxy else R.id.rbVpn)
        binding.rbMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = if (checkedId == R.id.rbProxy) Prefs.MODE_PROXY else Prefs.MODE_VPN
            if (prefs.mode != newMode) {
                val wasRunning = isRunning()
                if (wasRunning) stopProxy()
                prefs.mode = newMode
                if (wasRunning) {
                    binding.root.postDelayed({ if (!isRunning()) startProxy() }, 800)
                }
                refreshState()
            }
        }
    }

    private fun onToggleClicked() {
        if (isRunning()) stopProxy() else startProxy()
        binding.root.postDelayed({ refreshState() }, 400)
    }

    private fun isRunning(): Boolean = when (prefs.mode) {
        Prefs.MODE_PROXY -> ProxyModeService.running
        else -> ZapretVpnService.running
    }

    private fun startProxy() {
        when (prefs.mode) {
            Prefs.MODE_PROXY -> ProxyModeService.start(this)
            else -> {
                val consent = VpnService.prepare(this)
                if (consent != null) {
                    vpnPermissionLauncher.launch(consent)
                } else {
                    ZapretVpnService.start(this)
                }
            }
        }
    }

    private fun stopProxy() {
        when (prefs.mode) {
            Prefs.MODE_PROXY -> ProxyModeService.stop(this)
            else -> ZapretVpnService.stop(this)
        }
    }

    private fun restartIfRunning() {
        if (!isRunning()) return
        stopProxy()
        binding.root.postDelayed({ if (!isRunning()) startProxy() }, 800)
    }

    private fun saveDns() {
        val value = binding.etDns.text?.toString()?.trim() ?: ""
        if (value.isNotEmpty() && !Patterns.IP_ADDRESS.matcher(value).matches()) {
            Toast.makeText(this, R.string.toast_invalid_dns, Toast.LENGTH_SHORT).show()
            binding.etDns.setText(prefs.dns)
            return
        }
        val effective = value.ifEmpty { Prefs.DEFAULT_DNS }
        if (prefs.dns != effective) {
            prefs.dns = effective
            restartIfRunning()
        }
    }

    private fun persistInputFields() {
        prefs.extraArgs = binding.etExtraArgs.text?.toString()?.trim() ?: ""
        val dns = binding.etDns.text?.toString()?.trim() ?: ""
        if (dns.isEmpty() || Patterns.IP_ADDRESS.matcher(dns).matches()) {
            prefs.dns = dns.ifEmpty { Prefs.DEFAULT_DNS }
        }
    }

    private fun refreshState() {
        val running = isRunning()
        val proxyMode = prefs.mode == Prefs.MODE_PROXY

        binding.tvStatus.setText(
            if (running) R.string.status_connected else R.string.status_disconnected
        )
        binding.tvDetail.text = when {
            running && proxyMode -> getString(R.string.status_detail_proxy, CiadpiArgs.PROXY_PORT)
            running -> getString(R.string.status_detail_vpn)
            else -> getString(R.string.status_detail_idle)
        }
        binding.buttonToggle.setText(if (running) R.string.action_stop else R.string.action_start)
        binding.tvProxyInfo.visibility = if (proxyMode) View.VISIBLE else View.GONE
        binding.tvProxyInfo.text = getString(R.string.proxy_info, CiadpiArgs.PROXY_PORT)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
