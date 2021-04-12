package com.ing.zknotary.common.serialization

import com.ing.zknotary.common.serialization.bfl.corda.CordaSignatureSchemeToSerializers
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.corda.core.crypto.Crypto
import net.corda.core.identity.AbstractParty
import net.corda.testing.core.TestIdentity
import org.junit.jupiter.api.Test

class AbstractPartySerializerTest {
    @Serializable
    data class Data(val value: @Contextual AbstractParty)

    @Test
    fun `serialize and deserialize Party`() {
        val publicKeySerializer = CordaSignatureSchemeToSerializers serializerFor Crypto.DEFAULT_SIGNATURE_SCHEME
        val data1 = Data(TestIdentity.fresh("Alice").party)
        val data2 = Data(TestIdentity.fresh("Bob").party.anonymise())

        roundTrip(data1, publicKeySerializer)
        sameSize(data1, data2, publicKeySerializer)
    }
}
