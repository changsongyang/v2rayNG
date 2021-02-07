package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.android.synthetic.main.item_qrcode.view.*
import kotlinx.android.synthetic.main.item_recycler_main.view.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>()
        , ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }

    var changeable: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = mActivity.mainViewModel.serverList.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.getGuid(position) ?: return
            val config = MmkvManager.decodeServerConfig(guid) ?: return
            val outbound = config.getProxyOutbound() ?: return
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)

            holder.name.text = config.remarks
            holder.radio.isChecked = guid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.test_result.text = aff?.getTestDelayString() ?: ""
            if (aff?.testDelayMillis?:0L < 0L) {
                holder.test_result.setTextColor(ContextCompat.getColor(mActivity, R.color.viewfinder_laser))
            } else {
                holder.test_result.setTextColor(ContextCompat.getColor(mActivity, R.color.colorPing))
            }
            holder.subscription.text = ""
            // TODO: add sub remarks

            var shareOptions = share_method.asList()
            if (config.configType == EConfigType.CUSTOM) {
                holder.type.text = mActivity.getString(R.string.server_customize_config)
                shareOptions = shareOptions.takeLast(1)
            } else if (config.configType == EConfigType.VLESS) {
                holder.type.text = config.configType.name
            } else {
                holder.type.text = config.configType.name.toLowerCase()
            }
            holder.statistics.text = "${outbound.getServerAddress()} : ${outbound.getServerPort()}"

            holder.layout_share.setOnClickListener {
                AlertDialog.Builder(mActivity).setItems(shareOptions.toTypedArray()) { _, i ->
                    try {
                        when (i) {
                            0 -> {
                                if (config.configType == EConfigType.CUSTOM) {
                                    shareFullContent(position)
                                } else {
                                    val iv = LayoutInflater.from(mActivity).inflate(R.layout.item_qrcode, null)
                                    iv.iv_qcode.setImageBitmap(AngConfigManager.share2QRCode(guid))
                                    AlertDialog.Builder(mActivity).setView(iv).show()
                                }
                            }
                            1 -> {
                                if (AngConfigManager.share2Clipboard(guid) == 0) {
                                    mActivity.toast(R.string.toast_success)
                                } else {
                                    mActivity.toast(R.string.toast_failure)
                                }
                            }
                            2 -> shareFullContent(position)
                            else -> mActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.show()
            }

            holder.layout_edit.setOnClickListener {
                val intent = Intent().putExtra("guid", guid)
                if (config.configType == EConfigType.CUSTOM) {
                    mActivity.startActivity(intent.setClass(mActivity, ServerCustomConfigActivity::class.java))
                } else {
                    mActivity.startActivity(intent.setClass(mActivity, ServerActivity::class.java))
                }
            }
            holder.layout_remove.setOnClickListener {
                if (guid != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
                        && AngConfigManager.removeServer(position) == 0) {
                    notifyItemRemoved(position)
                }
            }

            holder.infoContainer.setOnClickListener {
                if (changeable) {
                    AngConfigManager.setActiveServer(position)
                } else {
                    mActivity.showCircle()
                    Utils.stopVService(mActivity)
                    Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                mActivity.showCircle()
                                if (!Utils.startVService(mActivity, position)) {
                                    mActivity.hideCircle()
                                }
                            }
                }
                notifyDataSetChanged()
            }
        }
        if (holder is FooterViewHolder) {
            //if (activity?.defaultDPreference?.getPrefBoolean(AppConfig.PREF_INAPP_BUY_IS_PREMIUM, false)) {
            if (true) {
                holder.layout_edit.visibility = View.INVISIBLE
            } else {
                holder.layout_edit.setOnClickListener {
                    Utils.openUri(mActivity, "${Utils.decode(AppConfig.promotionUrl)}?t=${System.currentTimeMillis()}")
                }
            }
        }
    }

    private fun shareFullContent(position: Int) {
        if (AngConfigManager.shareFullContent2Clipboard(mActivity.mainViewModel.getGuid(position)) == 0) {
            mActivity.toast(R.string.toast_success)
        } else {
            mActivity.toast(R.string.toast_failure)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_recycler_main, parent, false))
            else ->
                FooterViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_recycler_footer, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == mActivity.mainViewModel.serverList.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(itemView: View) : BaseViewHolder(itemView), ItemTouchHelperViewHolder {
        val subscription = itemView.tv_subscription!!
        val radio = itemView.btn_radio!!
        val name = itemView.tv_name!!
        val test_result = itemView.tv_test_result!!
        val type = itemView.tv_type!!
        val statistics = itemView.tv_statistics!!
        val infoContainer = itemView.info_container!!
        val layout_edit = itemView.layout_edit!!
        val layout_share = itemView.layout_share
        val layout_remove = itemView.layout_remove!!

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class FooterViewHolder(itemView: View) : BaseViewHolder(itemView), ItemTouchHelperViewHolder {
        val layout_edit = itemView.layout_edit!!

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemDismiss(position: Int) {
        if (mActivity.mainViewModel.getGuid(position) != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
//            mActivity.alert(R.string.del_config_comfirm) {
//                positiveButton(android.R.string.ok) {
            if (AngConfigManager.removeServer(position) == 0) {
                notifyItemRemoved(position)
            }
//                }
//                show()
//            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        AngConfigManager.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        notifyItemRangeChanged(fromPosition, toPosition - fromPosition + 1)
        return true
    }

    override fun onItemMoveCompleted() {
        AngConfigManager.storeConfigFile()
    }
}
