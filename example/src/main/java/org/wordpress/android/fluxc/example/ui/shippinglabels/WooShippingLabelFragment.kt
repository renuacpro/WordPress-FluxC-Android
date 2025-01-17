package org.wordpress.android.fluxc.example.ui.shippinglabels

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_woo_shippinglabels.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.example.R
import org.wordpress.android.fluxc.example.prependToLog
import org.wordpress.android.fluxc.example.replaceFragment
import org.wordpress.android.fluxc.example.ui.StoreSelectorDialog
import org.wordpress.android.fluxc.example.ui.common.showStoreSelectorDialog
import org.wordpress.android.fluxc.example.utils.showSingleLineDialog
import org.wordpress.android.fluxc.example.utils.toggleSiteDependentButtons
import org.wordpress.android.fluxc.generated.WCCoreActionBuilder
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.model.LocalOrRemoteId.RemoteId
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.order.OrderIdentifier
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingAccountSettings
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingLabelModel.ShippingLabelAddress
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingLabelModel.ShippingLabelPackage
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingLabelPackageData
import org.wordpress.android.fluxc.model.shippinglabels.WCShippingLabelPaperSize
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrdersByIdsPayload
import org.wordpress.android.fluxc.store.WCShippingLabelStore
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class WooShippingLabelFragment : Fragment() {
    @Inject internal lateinit var dispatcher: Dispatcher
    @Inject internal lateinit var wooCommerceStore: WooCommerceStore
    @Inject internal lateinit var wcShippingLabelStore: WCShippingLabelStore
    @Inject internal lateinit var wcOrderStore: WCOrderStore

    private var selectedPos: Int = -1
    private var selectedSite: SiteModel? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_woo_shippinglabels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shipping_labels_select_site.setOnClickListener {
            showStoreSelectorDialog(selectedPos, object : StoreSelectorDialog.Listener {
                override fun onSiteSelected(site: SiteModel, pos: Int) {
                    selectedSite = site
                    selectedPos = pos
                    buttonContainer.toggleSiteDependentButtons(true)
                    shipping_labels_selected_site.text = site.name ?: site.displayName
                }
            })
        }

        fetch_shipping_labels.setOnClickListener {
            selectedSite?.let { site ->
                showSingleLineDialog(activity, "Enter the order ID:", isNumeric = true) { orderEditText ->
                    if (orderEditText.text.isEmpty()) {
                        prependToLog("OrderId is null so doing nothing")
                        return@showSingleLineDialog
                    }

                    val orderId = orderEditText.text.toString().toLong()
                    prependToLog("Submitting request to fetch shipping labels for order $orderId")
                    coroutineScope.launch {
                        try {
                            val response = withContext(Dispatchers.Default) {
                                wcShippingLabelStore.fetchShippingLabelsForOrder(site, orderId)
                            }
                            response.error?.let {
                                prependToLog("${it.type}: ${it.message}")
                            }
                            response.model?.let {
                                val labelIds = it.map { it.remoteShippingLabelId }.joinToString(",")
                                prependToLog("Order $orderId has ${it.size} shipping labels with ids $labelIds")
                            }
                        } catch (e: Exception) {
                            prependToLog("Error: ${e.message}")
                        }
                    }
                }
            }
        }

        refund_shipping_label.setOnClickListener {
            selectedSite?.let { site ->
                showSingleLineDialog(activity, "Enter the order ID:", isNumeric = true) { orderEditText ->
                    if (orderEditText.text.isEmpty()) {
                        prependToLog("OrderId is null so doing nothing")
                        return@showSingleLineDialog
                    }

                    val orderId = orderEditText.text.toString().toLong()
                    showSingleLineDialog(
                            activity, "Enter the remote shipping Label Id:", isNumeric = true
                    ) { remoteIdEditText ->
                        if (remoteIdEditText.text.isEmpty()) {
                            prependToLog("Remote Id is null so doing nothing")
                            return@showSingleLineDialog
                        }

                        val remoteId = remoteIdEditText.text.toString().toLong()
                        prependToLog("Submitting request to refund shipping label for order $orderId with id $remoteId")

                        coroutineScope.launch {
                            try {
                                val response = withContext(Dispatchers.Default) {
                                    wcShippingLabelStore.refundShippingLabelForOrder(site, orderId, remoteId)
                                }
                                response.error?.let {
                                    prependToLog("${it.type}: ${it.message}")
                                }
                                response.model?.let {
                                    prependToLog(
                                            "Refund for $orderId with shipping label $remoteId is ${response.model}"
                                    )
                                }
                            } catch (e: Exception) {
                                prependToLog("Error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        print_shipping_label.setOnClickListener {
            selectedSite?.let { site ->
                showSingleLineDialog(
                        activity, "Enter the remote shipping Label Id:", isNumeric = true
                ) { remoteIdEditText ->
                    if (remoteIdEditText.text.isEmpty()) {
                        prependToLog("Remote Id is null so doing nothing")
                        return@showSingleLineDialog
                    }

                    val remoteId = remoteIdEditText.text.toString().toLong()
                    prependToLog("Submitting request to print shipping label for id $remoteId")

                    coroutineScope.launch {
                        try {
                            val response = withContext(Dispatchers.Default) {
                                // the paper size can be label, legal, letter
                                // For the example app, the default is set as label
                                wcShippingLabelStore.printShippingLabel(site, "label", remoteId)
                            }
                            response.error?.let {
                                prependToLog("${it.type}: ${it.message}")
                            }
                            response.model?.let { base64Content ->
                                // Since this function is used only by Woo testers and the Woo app
                                // only supports API > 21, it's fine to add a check here to support devices
                                // above API 19
                                if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                                    writePDFToFile(base64Content)?.let { openWebView(it) }
                                }
                            }
                        } catch (e: Exception) {
                            prependToLog("Error: ${e.message}")
                        }
                    }
                }
            }
        }

        verify_address.setOnClickListener {
            selectedSite?.let { site ->
                replaceFragment(WooVerifyAddressFragment.newInstance(site))
            }
        }

        get_packages.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val result = withContext(Dispatchers.Default) {
                        wcShippingLabelStore.getPackageTypes(site)
                    }
                    result.error?.let {
                        prependToLog("${it.type}: ${it.message}")
                    }
                    result.model?.let {
                        prependToLog("$it")
                    }
                }
            }
        }

        get_shipping_rates.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val orderId = showSingleLineDialog(requireActivity(), "Enter order id:", isNumeric = true)
                            ?.toLong() ?: return@launch

                    val (order, origin, destination) = loadData(site, orderId)

                    if (order == null) {
                        prependToLog("Couldn't retrieve order data")
                        return@launch
                    }
                    if (origin == null || destination == null) {
                        prependToLog(
                                "Invalid origin or destination address:\n" +
                                        "Origin:\n$origin\nDestination:\n$destination"
                        )
                        return@launch
                    }
                    var name: String
                    showSingleLineDialog(activity, "Enter package name:") { text ->
                        name = text.text.toString()

                        var height: Float?
                        showSingleLineDialog(activity, "Enter height:", isNumeric = true) { h ->
                            height = h.text.toString().toFloatOrNull()

                            var width: Float?
                            showSingleLineDialog(activity, "Enter width:", isNumeric = true) { w ->
                                width = w.text.toString().toFloatOrNull()

                                var length: Float?
                                showSingleLineDialog(activity, "Enter length:", isNumeric = true) { l ->
                                    length = l.text.toString().toFloatOrNull()

                                    var weight: Float?
                                    showSingleLineDialog(activity, "Enter weight:", isNumeric = true) { t ->
                                        weight = t.text.toString().toFloatOrNull()

                                        val box: ShippingLabelPackage?
                                        if (height == null || width == null || length == null || weight == null) {
                                            prependToLog(
                                                    "Invalid package parameters:\n" +
                                                            "Height: $height\n" +
                                                            "Width: $width\n" +
                                                            "Length: $length\n" +
                                                            "Weight: $weight"
                                            )
                                        } else {
                                            box = ShippingLabelPackage(
                                                    name,
                                                    "medium_flat_box_top",
                                                    height!!,
                                                    length!!,
                                                    width!!,
                                                    weight!!
                                            )

                                            coroutineScope.launch {
                                                val result = wcShippingLabelStore.getShippingRates(
                                                        site,
                                                        order.remoteOrderId,
                                                        origin,
                                                        destination,
                                                        listOf(box)
                                                )

                                                result.error?.let {
                                                    prependToLog("${it.type}: ${it.message}")
                                                }
                                                result.model?.let {
                                                    prependToLog("$it")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        purchase_label.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val orderId = showSingleLineDialog(requireActivity(), "Enter order id:", isNumeric = true)
                            ?.toLong() ?: return@launch

                    val (order, origin, destination) = loadData(site, orderId)

                    if (order == null) {
                        prependToLog("Couldn't retrieve order data")
                        return@launch
                    }
                    if (origin == null || destination == null) {
                        prependToLog(
                                "Invalid origin or destination address:\n" +
                                        "Origin:\n$origin\nDestination:\n$destination"
                        )
                        return@launch
                    }

                    val boxId = showSingleLineDialog(
                            requireActivity(), "Enter box Id", defaultValue = "medium_flat_box_top"
                    )
                    val height = showSingleLineDialog(requireActivity(), "Enter height:", isNumeric = true)
                            ?.toFloat()
                    val width = showSingleLineDialog(requireActivity(), "Enter width:", isNumeric = true)
                            ?.toFloat()
                    val length = showSingleLineDialog(requireActivity(), "Enter length:", isNumeric = true)
                            ?.toFloat()
                    val weight = showSingleLineDialog(requireActivity(), "Enter weight:", isNumeric = true)
                            ?.toFloat()
                    if (boxId == null || height == null || width == null || length == null || weight == null) {
                        prependToLog(
                                "Invalid package parameters:\n" +
                                        "BoxId: $boxId\n" +
                                        "Height: $height\n" +
                                        "Width: $width\n" +
                                        "Length: $length\n" +
                                        "Weight: $weight"
                        )
                    }
                    prependToLog("Retrieving rates")

                    val box = ShippingLabelPackage(
                            "default",
                            boxId!!,
                            height!!,
                            length!!,
                            width!!,
                            weight!!
                    )
                    val ratesResult = wcShippingLabelStore.getShippingRates(
                            site,
                            order.remoteOrderId,
                            origin,
                            destination,
                            listOf(box)
                    )
                    if (ratesResult.isError) {
                        prependToLog(
                                "Getting rates failed: " +
                                        "${ratesResult.error.type}: ${ratesResult.error.message}"
                        )
                        return@launch
                    }
                    if (ratesResult.model!!.packageRates.isEmpty() ||
                            ratesResult.model!!.packageRates.first().shippingOptions.isEmpty() ||
                            ratesResult.model!!.packageRates.first().shippingOptions.first().rates.isEmpty()) {
                        prependToLog("Couldn't find rates for the given input, please try with different parameters")
                        return@launch
                    }
                    val rate = ratesResult.model!!.packageRates.first().shippingOptions.first().rates.first()
                    val packageData = WCShippingLabelPackageData(
                            id = "default",
                            boxId = "medium_flat_box_top",
                            length = length,
                            height = height,
                            width = width,
                            weight = weight,
                            shipmentId = rate.shipmentId,
                            rateId = rate.rateId,
                            serviceId = rate.serviceId,
                            carrierId = rate.carrierId,
                            products = order.getLineItemList().map { it.id!! }
                    )
                    val result = wcShippingLabelStore.purchaseShippingLabels(
                            site,
                            order.remoteOrderId,
                            origin,
                            destination,
                            listOf(packageData)
                    )

                    result.error?.let {
                        prependToLog("${it.type}: ${it.message}")
                    }
                    result.model?.let {
                        val label = it.first()
                        prependToLog(
                                "Purchased a shipping label with the following details:\n" +
                                        "Order Id: ${label.localOrderId}\n" +
                                        "Products: ${label.productNames}\n" +
                                        "Label Id: ${label.remoteShippingLabelId}\n" +
                                        "Price: ${label.rate}"
                        )
                    }
                }
            }
        }

        get_shipping_plugin_info.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val result = withContext(Dispatchers.Default) {
                        wooCommerceStore.fetchWooCommerceServicesPluginInfo(site)
                    }
                    result.error?.let {
                        prependToLog("${it.type}: ${it.message}")
                    }
                    result.model?.let {
                        prependToLog("$it")
                    }
                }
            }
        }

        get_account_settings.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val result = wcShippingLabelStore.getAccountSettings(site)
                    result.error?.let {
                        prependToLog("${it.type}: ${it.message}")
                    }
                    if (result.model != null) {
                        prependToLog("${result.model}")
                    } else {
                        prependToLog("The WooCommerce services plugin is not installed")
                    }
                }
            }
        }
        update_account_settings.setOnClickListener {
            selectedSite?.let { site ->
                coroutineScope.launch {
                    val result = wcShippingLabelStore.getAccountSettings(site)
                    result.error?.let {
                        prependToLog("Can't fetch account settings\n${it.type}: ${it.message}")
                    }
                    if (result.model != null) {
                        showAccountSettingsDialog(site, result.model!!)
                    } else {
                        prependToLog("The WooCommerce services plugin is not installed")
                    }
                }
            }
        }
    }

    private fun showAccountSettingsDialog(selectedSite: SiteModel, accountSettings: WCShippingAccountSettings) {
        val dialog = AlertDialog.Builder(requireContext()).let {
            it.setView(R.layout.dialog_wc_shipping_label_settings)
            it.show()
        }
        dialog.findViewById<CheckBox>(R.id.enabled_checkbox)?.isChecked = accountSettings.isCreatingLabelsEnabled
        dialog.findViewById<EditText>(R.id.payment_method_id)?.setText(
                accountSettings.selectedPaymentMethodId?.toString() ?: ""
        )
        dialog.findViewById<CheckBox>(R.id.email_receipts_checkbox)?.isChecked = accountSettings.isEmailReceiptEnabled
        dialog.findViewById<Spinner>(R.id.paper_size_spinner)?.let {
            val items = listOf("label", "legal", "letter")
            it.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
            it.setSelection(items.indexOf(accountSettings.paperSize.stringValue))
        }
        dialog.findViewById<Button>(R.id.save_button)?.setOnClickListener {
            dialog.hide()
            coroutineScope.launch {
                val result = wcShippingLabelStore.updateAccountSettings(
                        selectedSite,
                        isCreatingLabelsEnabled = dialog.findViewById<CheckBox>(R.id.enabled_checkbox)?.isChecked,
                        selectedPaymentMethodId = dialog.findViewById<EditText>(R.id.payment_method_id)?.text
                                ?.toString()?.ifEmpty { null }?.toInt(),
                        isEmailReceiptEnabled = dialog.findViewById<CheckBox>(R.id.email_receipts_checkbox)?.isChecked,
                        paperSize = dialog.findViewById<Spinner>(R.id.paper_size_spinner)?.selectedItem?.let {
                            WCShippingLabelPaperSize.fromString(it as String)
                        }
                )
                dialog.dismiss()
                result.error?.let {
                    prependToLog("${it.type}: ${it.message}")
                }
                if (result.model == true) {
                    prependToLog("Settings updated")
                } else {
                    prependToLog("The WooCommerce services plugin is not installed")
                }
            }
        }
    }

    private suspend fun loadData(site: SiteModel, orderId: Long):
            Triple<WCOrderModel?, ShippingLabelAddress?, ShippingLabelAddress?> {
        prependToLog("Loading shipping data...")

        dispatcher.dispatch(WCCoreActionBuilder.newFetchSiteSettingsAction(site))

        val payload = FetchOrdersByIdsPayload(site, listOf(RemoteId(orderId)))
        dispatcher.dispatch(WCOrderActionBuilder.newFetchOrdersByIdsAction(payload))

        delay(5000)

        val origin = wooCommerceStore.getSiteSettings(site)?.let {
            ShippingLabelAddress(
                    address = it.address,
                    city = it.city,
                    postcode = it.postalCode,
                    state = it.stateCode,
                    country = it.countryCode
            )
        }

        val order = wcOrderStore.getOrderByIdentifier(OrderIdentifier(site.id, orderId))
        val destination = order?.getShippingAddress()?.let {
            ShippingLabelAddress(
                    address = it.address1,
                    city = it.city,
                    postcode = it.postcode,
                    state = it.state,
                    country = it.country
            )
        }

        return Triple(order, origin, destination)
    }

    /**
     * Creates a temporary file for storing captured photos
     */
    @RequiresApi(VERSION_CODES.KITKAT)
    private fun createTempPdfFile(context: Context): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        return try {
            File.createTempFile(
                    "PDF_${timeStamp}_",
                    ".pdf",
                    storageDir
            )
        } catch (ex: IOException) {
            ex.printStackTrace()
            prependToLog("Error when creating temp file: ${ex.message}")
            null
        }
    }

    /**
     * Since this function is used only by Woo testers and the Woo app
     * only supports API > 21, it's fine to leave this method to support only
     * API 19 and above.
     */
    @RequiresApi(VERSION_CODES.KITKAT)
    private fun writePDFToFile(base64Content: String): File? {
        return try {
            createTempPdfFile(requireContext())?.let { file ->
                val out = FileOutputStream(file)
                val pdfAsBytes = Base64.decode(base64Content, 0)
                out.write(pdfAsBytes)
                out.flush()
                out.close()
                file
            }
        } catch (e: Exception) {
            e.printStackTrace()
            prependToLog("Error when writing pdf to file: ${e.message}")
            null
        }
    }

    private fun openWebView(file: File) {
        val authority = requireContext().applicationContext.packageName + ".provider"
        val fileUri = FileProvider.getUriForFile(
                requireContext(),
                authority,
                file
        )

        val sendIntent = Intent(Intent.ACTION_VIEW)
        sendIntent.setDataAndType(fileUri, "application/pdf")
        sendIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(sendIntent)
    }
}
