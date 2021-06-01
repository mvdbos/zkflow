package com.ing.zknotary.zinc.types.corda.partyandreference

import com.ing.zknotary.common.serialization.bfl.serializers.PartyAndReferenceSurrogate
import com.ing.zknotary.common.serialization.bfl.serializers.publickey.BCRSASurrogate
import com.ing.zknotary.testing.DeserializationTestBase
import com.ing.zknotary.testing.getZincZKService
import com.ing.zknotary.zinc.types.toZincJson
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.Crypto
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.core.TestIdentity
import kotlin.random.Random

class DeserializeAnonymousPartyBCRSAAndReferenceTest :
    DeserializationTestBase<DeserializeAnonymousPartyBCRSAAndReferenceTest, DeserializeAnonymousPartyBCRSAAndReferenceTest.Data>({
        it.data.toZincJson(BCRSASurrogate.ENCODED_SIZE)
    }) {
    override fun getZincZKService() = getZincZKService<DeserializeAnonymousPartyBCRSAAndReferenceTest>()

    @Serializable
    data class Data(val data: @Contextual PartyAndReference)

    companion object {
        @JvmStatic
        fun testData(): List<Data> {
            val reference = OpaqueBytes(Random(42).nextBytes(PartyAndReferenceSurrogate.REFERENCE_SIZE))
            return listOf(
                Data(
                    PartyAndReference(
                        TestIdentity.fresh("Alice", Crypto.RSA_SHA256).party.anonymise(),
                        reference
                    )
                ),
            )
        }
    }
}
