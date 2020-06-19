package com.ing.zknotary.common.transactions

import com.ing.zknotary.common.serializer.ZincSerializationFactory
import com.ing.zknotary.common.serializer.ZincSerializationFactoryService
import com.ing.zknotary.common.zkp.ZincWitness
import com.ing.zknotary.notary.transactions.createTestsState
import com.ing.zknotary.notary.transactions.moveTestsState
import net.corda.core.crypto.BLAKE2s256DigestService
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SerializationTest {
    private val alice = TestIdentity.fresh("alice", Crypto.EDDSA_ED25519_SHA512)
    private val bob = TestIdentity.fresh("bob", Crypto.EDDSA_ED25519_SHA512)

    private val ledgerServices = MockServices(
        listOf("com.ing.zknotary.common.contracts"),
        alice
    )

    private lateinit var ptx: ZKProverTransaction
    private lateinit var vtx: ZKVerifierTransaction
    private lateinit var sigAlice: ByteArray

    @Before
    fun setup() {
        ledgerServices.ledger {
            val wtx = moveTestsState(createTestsState(owner = alice), newOwner = bob)
            verifies()

            // Build a ZKProverTransaction
            ptx = ZKProverTransactionFactory.create(
                wtx.toLedgerTransaction(ledgerServices),
                ZincSerializationFactoryService(),
                componentGroupLeafDigestService = BLAKE2s256DigestService,
                nodeDigestService = BLAKE2s256DigestService // Should become Pedersen hash when available
            )

            // Collect signatures
            sigAlice = alice.keyPair.private.sign(ptx.id.bytes).bytes

            // build filtered ZKVerifierTransaction
            vtx = ptx.toZKVerifierTransaction()
        }
    }

    @Test
    fun `Serialize to Zinc`() {
        ledgerServices.ledger {
            // Serialize for transport to Zinc
            val witness = ZincWitness(ptx, listOf(sigAlice))
            val json = witness.serialize(ZincSerializationFactory)
            println(String(json.bytes))
            // TODO: do checks on JSON to confirm it is acceptable for Zinc
        }
    }

    @Test
    fun `VerifierTransaction from ProverTransaction has same Merkle root`() {
        ledgerServices.ledger {
            assertEquals(ptx.id, vtx.id)
        }
    }

    @Test
    fun `ProverTransaction survives Corda AMQP serialization`() {
        ledgerServices.ledger {
            val ptxAmqp = ptx.serialize()
            val deserializedptx = ptxAmqp.deserialize()
            assertEquals(ptx, deserializedptx)
            // ptx.verify()
        }
    }

    @Test
    fun `VerifierTransaction survives Corda AMQP serialization`() {
        ledgerServices.ledger {
            val vtxAmqp = vtx.serialize()
            val deserializedVtx = vtxAmqp.deserialize()
            assertEquals(vtx, deserializedVtx)
            // TODO: vtx.verify()
        }
    }
}
