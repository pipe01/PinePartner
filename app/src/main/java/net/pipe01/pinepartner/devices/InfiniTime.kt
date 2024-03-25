package net.pipe01.pinepartner.devices

import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import java.util.UUID

class Characteristic(private val serviceUuid: String, private val uuid: String) {
    fun bind(services: ClientBleGattServices): ClientBleGattCharacteristic {
        val service = services.findService(UUID.fromString(serviceUuid)) ?: throw IllegalArgumentException("Service not found")

        return service.findCharacteristic(UUID.fromString(uuid)) ?: throw IllegalArgumentException("Characteristic not found")
    }
}

object InfiniTime {
    object AlertNotificationService {
        private val ID = "00001811-0000-1000-8000-00805F9B34FB"

        val NEW_ALERT = Characteristic(ID, "00002a46-0000-1000-8000-00805f9b34fb")
    }

    object CurrentTimeService {
        private val ID = "00001805-0000-1000-8000-00805F9B34FB"

        val CURRENT_TIME = Characteristic(ID, "00002a2b-0000-1000-8000-00805f9b34fb")
    }

    object DeviceInformationService {
        private val ID = "0000180A-0000-1000-8000-00805F9B34FB"

        val FIRMWARE_REVISION = Characteristic(ID, "00002a26-0000-1000-8000-00805f9b34fb")
    }

    object DFUService {
        private val ID = "00001530-1212-efde-1523-785feabcd123"

        val CONTROL_POINT = Characteristic(ID, "00001531-1212-efde-1523-785feabcd123")
        val PACKET = Characteristic(ID, "00001532-1212-efde-1523-785feabcd123")
    }

    object BatteryService {
        private val ID = "0000180F-0000-1000-8000-00805F9B34FB"

        val BATTERY_LEVEL = Characteristic(ID, "00002a19-0000-1000-8000-00805f9b34fb")
    }

    object WeatherService {
        private val ID = "00050000-78fc-48fe-8e23-433b3a1942d0"

        val WEATHER_DATA = Characteristic(ID, "00050001-78fc-48fe-8e23-433b3a1942d0")
    }

    object FileSystemService {
        private val ID = "0000FEBB-0000-1000-8000-00805F9B34FB"

        val VERSION = Characteristic(ID, "ADAF0100-4669-6C65-5472-616E73666572")
        val RAW_TRANSFER = Characteristic(ID, "ADAF0200-4669-6C65-5472-616E73666572")
    }
}