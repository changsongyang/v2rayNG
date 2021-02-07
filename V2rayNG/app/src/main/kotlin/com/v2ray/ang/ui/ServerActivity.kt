package com.v2ray.ang.ui

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.tencent.mmkv.MMKV
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.Companion.DEFAULT_NETWORK
import com.v2ray.ang.dto.V2rayConfig.Companion.DEFAULT_PORT
import com.v2ray.ang.dto.V2rayConfig.Companion.HTTP
import com.v2ray.ang.dto.V2rayConfig.Companion.TLS
import com.v2ray.ang.dto.V2rayConfig.Companion.XTLS
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.MmkvManager.ID_MAIN
import com.v2ray.ang.util.MmkvManager.KEY_SELECTED_SERVER
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.activity_server_socks.*
import kotlinx.android.synthetic.main.activity_server_vmess.*
import kotlinx.android.synthetic.main.activity_server_vmess.et_address
import kotlinx.android.synthetic.main.activity_server_vmess.et_id
import kotlinx.android.synthetic.main.activity_server_vmess.et_path
import kotlinx.android.synthetic.main.activity_server_vmess.et_port
import kotlinx.android.synthetic.main.activity_server_vmess.et_remarks
import kotlinx.android.synthetic.main.activity_server_vmess.et_request_host
import kotlinx.android.synthetic.main.activity_server_vmess.sp_allow_insecure
import kotlinx.android.synthetic.main.activity_server_vmess.sp_header_type
import kotlinx.android.synthetic.main.activity_server_vmess.sp_network
import kotlinx.android.synthetic.main.activity_server_vmess.sp_stream_security

class ServerActivity : BaseActivity() {

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy { editGuid.isNotEmpty() && editGuid == mainStorage?.decodeString(KEY_SELECTED_SERVER) }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value)) ?: EConfigType.VMESS
    }
    private val securitys: Array<out String> by lazy {
        resources.getStringArray(R.array.securitys)
    }
    private val shadowsocksSecuritys: Array<out String> by lazy {
        resources.getStringArray(R.array.ss_securitys)
    }
    private val flows: Array<out String> by lazy {
        resources.getStringArray(R.array.flows)
    }
    private val networks: Array<out String> by lazy {
        resources.getStringArray(R.array.networks)
    }
    private val headertypes: Array<out String> by lazy {
        resources.getStringArray(R.array.headertypes)
    }
    private val streamSecuritys: Array<out String> by lazy {
        resources.getStringArray(R.array.streamsecurityxs)
    }
    private val allowinsecures: Array<out String> by lazy {
        resources.getStringArray(R.array.allowinsecures)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.title_server)

        val config = MmkvManager.decodeServerConfig(editGuid)
        when(config?.configType ?: createConfigType) {
            EConfigType.VMESS -> setContentView(R.layout.activity_server_vmess)
            EConfigType.CUSTOM -> return
            EConfigType.SHADOWSOCKS -> setContentView(R.layout.activity_server_shadowsocks)
            EConfigType.SOCKS -> setContentView(R.layout.activity_server_socks)
//            EConfigType.VLESS -> setContentView(R.layout.activity_server_vless)
//            EConfigType.TROJAN -> setContentView(R.layout.activity_server_trojan)
        }
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * bingding seleced server config
     */
    private fun bindingServer(config: ServerConfig): Boolean {
        val outbound = config.getProxyOutbound() ?: return false
        val streamSetting = config.outboundBean?.streamSettings ?: return false

        et_remarks.text = Utils.getEditable(config.remarks)
        et_address.text = Utils.getEditable(outbound.getServerAddress().orEmpty())
        et_port.text = Utils.getEditable(outbound.getServerPort()?.or(DEFAULT_PORT).toString())
        et_id.text = Utils.getEditable(outbound.getPassword().orEmpty())
        et_alterId?.text = Utils.getEditable(outbound.settings?.vnext?.get(0)?.users?.get(0)?.alterId.toString())
        if (config.configType == EConfigType.SOCKS) {
            et_security.text = Utils.getEditable(outbound.settings?.servers?.get(0)?.users?.get(0)?.user.orEmpty())
        } else if (config.configType == EConfigType.VLESS) {
            et_security.text = Utils.getEditable(outbound.getSecurityEncryption().orEmpty())
            val flow = Utils.arrayFind(flows, outbound.settings?.vnext?.get(0)?.users?.get(0)?.flow.orEmpty())
            if (flow >= 0) {
                //sp_flow.setSelection(flow)
            }
        }
        val securityEncryptions = if (config.configType == EConfigType.SHADOWSOCKS) shadowsocksSecuritys else securitys
        val security = Utils.arrayFind(securityEncryptions, outbound.getSecurityEncryption().orEmpty())
        if (security >= 0) {
            sp_security?.setSelection(security)
        }

        val streamSecurity = Utils.arrayFind(streamSecuritys, streamSetting.security)
        if (streamSecurity >= 0) {
            sp_stream_security?.setSelection(streamSecurity)
            streamSetting.tlsSettings?: streamSetting.xtlsSettings?.let { tlsSetting ->
                val allowinsecure = Utils.arrayFind(allowinsecures, tlsSetting.allowInsecure.toString())
                if (allowinsecure >= 0) {
                    sp_allow_insecure?.setSelection(allowinsecure)
                }
                et_request_host.text = Utils.getEditable(tlsSetting.serverName)
            }
        }
        val network = Utils.arrayFind(networks, streamSetting.network)
        if (network >= 0) {
            sp_network?.setSelection(network)
        }
        outbound.getTransportSettingDetails()?.let { transportDetails ->
            sp_header_type.setSelection(Utils.arrayFind(headertypes, transportDetails[0]))
            et_request_host.text = Utils.getEditable(transportDetails[1])
            et_path.text = Utils.getEditable(transportDetails[2])
        }
        return true
    }

    /**
     * clear or init server config
     */
    private fun clearServer(): Boolean {
        et_remarks.text = null
        et_address.text = null
        et_port.text = Utils.getEditable(DEFAULT_PORT.toString())
        et_id.text = null
        et_alterId?.text = Utils.getEditable("0")
        sp_security?.setSelection(0)
        sp_network?.setSelection(0)

        sp_header_type?.setSelection(0)
        et_request_host?.text = null
        et_path?.text = null
        sp_stream_security?.setSelection(0)
        sp_allow_insecure?.setSelection(0)

        //et_security.text = null
        //sp_flow?.setSelection(0)
        return true
    }

    /**
     * save server config
     */
    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(et_remarks.text.toString())) {
            toast(R.string.server_lab_remarks)
            return false
        }
        if (TextUtils.isEmpty(et_address.text.toString())) {
            toast(R.string.server_lab_address)
            return false
        }
        val port = Utils.parseInt(et_port.text.toString())
        if (port <= 0) {
            toast(R.string.server_lab_port)
            return false
        }
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ServerConfig.create(createConfigType)
        if (config.configType != EConfigType.SOCKS && TextUtils.isEmpty(et_id.text.toString())) {
            toast(R.string.server_lab_id)
            return false
        }
        et_alterId?.let {
            val alterId = Utils.parseInt(et_alterId.text.toString())
            if (alterId < 0) {
                toast(R.string.server_lab_alterid)
                return false
            }
        }

        config.remarks = et_remarks.text.toString().trim()
        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
            saveVnext(vnext, port, config)
        }
        config.outboundBean?.settings?.servers?.get(0)?.let { server ->
            saveServers(server, port, config)
        }
        config.outboundBean?.streamSettings?.let {
            saveStreamSettings(it)
        }

        MmkvManager.encodeServerConfig(editGuid, config)
        toast(R.string.toast_success)
        finish()
        return true
    }

    private fun saveVnext(vnext: V2rayConfig.OutboundBean.OutSettingsBean.VnextBean, port: Int, config: ServerConfig) {
        vnext.address = et_address.text.toString().trim()
        vnext.port = port
        vnext.users[0].id = et_id.text.toString().trim()
        if (config.configType == EConfigType.VMESS) {
            vnext.users[0].alterId = Utils.parseInt(et_alterId.text.toString())
            vnext.users[0].security = securitys[sp_security.selectedItemPosition]
        } else if (config.configType == EConfigType.VLESS) {
            vnext.users[0].encryption = et_security.text.toString().trim()
            if (streamSecuritys[sp_stream_security.selectedItemPosition] == XTLS) {
//                vnext.users[0].flow = if (flows[sp_flow.selectedItemPosition].isBlank()) DEFAULT_FLOW
//                else flows[sp_flow.selectedItemPosition]
            }
        }
    }

    private fun saveServers(server: V2rayConfig.OutboundBean.OutSettingsBean.ServersBean, port: Int, config: ServerConfig) {
        server.address = et_address.text.toString().trim()
        server.port = port
        if (config.configType == EConfigType.SHADOWSOCKS) {
            server.password = et_id.text.toString().trim()
            server.method = shadowsocksSecuritys[sp_security.selectedItemPosition]
        } else if (config.configType == EConfigType.SOCKS && !TextUtils.isEmpty(et_security.text) && !TextUtils.isEmpty(et_id.text)) {
            val socksUsersBean = V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
            socksUsersBean.user = et_security.text.toString().trim()
            socksUsersBean.pass = et_id.text.toString().trim()
            server.users = listOf(socksUsersBean)
        } else if (config.configType == EConfigType.TROJAN) {
            server.password = et_id.text.toString().trim()
        }
    }

    private fun saveStreamSettings(streamSetting: V2rayConfig.OutboundBean.StreamSettingsBean) {
        streamSetting.network = if (sp_network != null) networks[sp_network.selectedItemPosition] else DEFAULT_NETWORK
        val headerType = if (sp_header_type != null) headertypes[sp_header_type.selectedItemPosition] else ""
        val requestHost = et_request_host.text.toString().trim()
        val path = et_path?.text.toString().trim()
        var sni = requestHost
        when (streamSetting.network) {
            "tcp" -> if (headerType == HTTP) {
                val tcpSetting = V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean()
                tcpSetting.header.type = headerType
                if (!TextUtils.isEmpty(requestHost) || !TextUtils.isEmpty(path)) {
                    val requestObj = V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean()
                    requestObj.headers.Host = requestHost.split(",").map { it.trim() }
                    requestObj.path = path.split(",").map { it.trim() }
                    tcpSetting.header.request = requestObj
                    sni = requestObj.headers.Host.getOrNull(0) ?: sni
                }
                streamSetting.tcpSettings = tcpSetting
            }
            "kcp" -> {
                val kcpsetting = V2rayConfig.OutboundBean.StreamSettingsBean.KcpSettingsBean()
                kcpsetting.header.type = headerType
                if (TextUtils.isEmpty(path)) {
                    kcpsetting.seed = null
                } else {
                    kcpsetting.seed = path
                }
                streamSetting.kcpSettings = kcpsetting
            }
            "ws" -> {
                val wssetting = V2rayConfig.OutboundBean.StreamSettingsBean.WsSettingsBean()
                if (!TextUtils.isEmpty(requestHost)) {
                    wssetting.headers.Host = requestHost
                    sni = requestHost
                }
                if (!TextUtils.isEmpty(path)) {
                    wssetting.path = path
                }
                streamSetting.wsSettings = wssetting
            }
            "h2" -> {
                val h2Setting = V2rayConfig.OutboundBean.StreamSettingsBean.HttpSettingsBean()
                if (!TextUtils.isEmpty(requestHost)) {
                    h2Setting.host = requestHost.split(",").map { it.trim() }
                    sni = h2Setting.host.getOrNull(0) ?: sni
                }
                h2Setting.path = path
                streamSetting.httpSettings = h2Setting
            }
            "quic" -> {
                val quicsetting = V2rayConfig.OutboundBean.StreamSettingsBean.QuicSettingBean()
                quicsetting.security = requestHost
                quicsetting.key = path
                quicsetting.header.type = headerType
                streamSetting.quicSettings = quicsetting
            }
        }

        streamSetting.security = if (sp_stream_security != null) streamSecuritys[sp_stream_security.selectedItemPosition] else TLS
        val tlsSetting = V2rayConfig.OutboundBean.StreamSettingsBean.TlsSettingsBean(
                allowInsecure = if (sp_allow_insecure != null) allowinsecures[sp_allow_insecure.selectedItemPosition].toBoolean() else false,
                serverName = sni
        )
        if (streamSetting.security == TLS) {
            streamSetting.tlsSettings = tlsSetting
        } else if (streamSetting.security == XTLS) {
            streamSetting.xtlsSettings = tlsSetting
        }
    }

    /**
     * save server config
     */
    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                    .show()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu?.findItem(R.id.del_config)
        val saveButton = menu?.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
