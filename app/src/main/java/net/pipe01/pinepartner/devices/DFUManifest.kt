package net.pipe01.pinepartner.devices

import kotlinx.serialization.Serializable

@Serializable
data class DFUManifest(
    val manifest: Inner
) {
    @Serializable
    data class Inner(
        val application: Application,
        val dfu_version: Float,
    )

    @Serializable
    data class Application(
        val bin_file: String,
        val dat_file: String,
        val init_packet_data: InitPacketData,
    )

    @Serializable
    data class InitPacketData(
        val application_version: Long,
        val device_revision: Long,
        val device_type: Int,
        val firmware_crc16: Int,
        val softdevice_req: List<Int>,
    )
}

