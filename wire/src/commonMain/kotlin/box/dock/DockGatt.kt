package box.dock

/**
 * BLE transport. Platform BLE APIs differ enough that the transport itself is
 * `expect`/`actual`, but everything that can be got wrong by reasoning -- the
 * framing and the signature -- stays in common code and is unit tested.
 *
 * Service   5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e01
 *   pairing   ...5e02  read   (only while the dock's pairing window is open)
 *   provision ...5e03  write  (signed)
 *   command   ...5e04  write  (signed)
 *   status    ...5e05  read + notify
 */
object DockGatt {
    const val SERVICE   = "5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e01"
    const val PAIRING   = "5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e02"
    const val PROVISION = "5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e03"
    const val COMMAND   = "5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e04"
    const val STATUS    = "5f6d8c20-9a1e-4b7c-8d3f-1a2b3c4d5e05"

    /**
     * Bytes for a signed command write.
     *
     * The dock pairs "just works", so an encrypted link only proves the device
     * bonded -- any phone in range can do that. The signature is what proves
     * this is the paired app, so a BLE write is authorised exactly like an
     * HTTP request.
     */
    fun commandFrame(key: ByteArray, command: Char, nonce: Long): ByteArray =
        DockCrypto.bleFrame(key, "/ble/cmd", byteArrayOf(command.code.toByte()), nonce)

    /** Credentials are "ssid\u0000password", signed as bytes so the password is covered. */
    fun provisionFrame(key: ByteArray, ssid: String, password: String, nonce: Long): ByteArray =
        DockCrypto.bleFrame(
            key, "/ble/provision",
            ssid.encodeToByteArray() + byteArrayOf(0) + password.encodeToByteArray(),
            nonce,
        )
}
