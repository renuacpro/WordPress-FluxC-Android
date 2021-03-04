package org.wordpress.android.fluxc.model.shippinglabels

import com.google.gson.annotations.SerializedName

data class WCShippingLabelPackageData(
    val id: String,
    @SerializedName("box_id") val boxId: String,
    val length: Float,
    val width: Float,
    val height: Float,
    val weight: Float,
    @SerializedName("shipment_id") val shipmentId: String,
    @SerializedName("rate_id") val rateId: String,
    @SerializedName("service_id") val serviceId: String,
    @SerializedName("carrier_id") val carrierId: String,
    val products: List<Long>
)
