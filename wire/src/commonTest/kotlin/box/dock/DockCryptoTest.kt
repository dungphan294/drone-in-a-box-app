package box.dock

import box.dock.DockCrypto.hexToBytes
import box.dock.DockCrypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The canonical-form vectors here are the SAME ones the firmware asserts in
 * firmware/lift_motor/components/../test/host/test_auth.c, and the same ones
 * tools/dock_cli.py checks. Three implementations of one wire format, pinned
 * by one set of vectors: if any drifts, its own tests fail rather than the
 * mismatch showing up as a mysterious 401 on the bench.
 */
class DockCryptoTest {

    @Test fun sha256KnownAnswers() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            DockCrypto.sha256(ByteArray(0)).toHex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DockCrypto.sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test fun hmacRfc4231Case1() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            DockCrypto.hmacSha256(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray()).toHex(),
        )
    }

    @Test fun canonicalMatchesFirmware() {
        // test_canonical_is_length_prefixed in test_auth.c
        assertEquals(
            "4:POST8:/api/cmd3:c=e1:7",
            DockCrypto.canonical("POST", "/api/cmd", "c=e", 7).decodeToString(),
        )
    }

    @Test fun canonicalResistsSplicing() {
        // test_canonical_resists_field_splicing in test_auth.c
        val a = DockCrypto.canonical("POST", "/api/cmd", "c=e", 1).decodeToString()
        val b = DockCrypto.canonical("POST", "/api", "/cmdc=e", 1).decodeToString()
        assertTrue(a != b)
    }

    @Test fun blobCoversBytesAfterNul() {
        // test_blob_covers_bytes_after_a_nul in test_auth.c: the password must
        // be signed, not just the SSID.
        val good = DockCrypto.canonicalBlob(
            "BLE", "/ble/provision",
            "net".encodeToByteArray() + byteArrayOf(0) + "secret".encodeToByteArray(), 1,
        )
        assertEquals("3:BLE14:/ble/provision10:net\u0000secret1:1", good.decodeToString())
    }

    @Test fun signatureMatchesGeneratedVector() {
        val key = "01080f161d242b323940474e555c636a71787f868d949ba2a9b0b7bec5ccd3da".hexToBytes()
        assertEquals(
            "91083fa3f576614df26bee9058b0fd379924e0d21b5cd0e435faa43f7755ce1f",
            DockCrypto.hmacSha256(key, DockCrypto.canonical("POST", "/api/cmd", "c=e", 100)).toHex(),
        )
        assertEquals(
            "8ce7472e7026ebffae8fb4271f18f4dcb4a8a9628c171dd55a9878e87fabc606",
            DockCrypto.hmacSha256(
                key,
                DockCrypto.canonicalBlob(
                    "BLE", "/ble/provision",
                    "net".encodeToByteArray() + byteArrayOf(0) + "secret".encodeToByteArray(), 1,
                ),
            ).toHex(),
        )
    }

    @Test fun bleFrameLayout() {
        val frame = DockGatt.commandFrame(ByteArray(32), 'e', 7)
        assertEquals(41, frame.size)               // 8 nonce + 32 signature + 1 payload
        assertEquals(7, frame[0].toInt())          // little-endian nonce
        assertEquals('e'.code.toByte(), frame[40])
    }
}
