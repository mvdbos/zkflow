package com.ing.zknotary.common.zkp

import com.ing.zknotary.common.contracts.ZKCommandData
import com.ing.zknotary.common.transactions.SignedZKVerifierTransaction
import com.ing.zknotary.common.transactions.ZKVerifierTransaction
import com.ing.zknotary.common.transactions.zkCommandData
import com.ing.zknotary.node.services.ServiceNames
import com.ing.zknotary.node.services.ZKWritableVerifierTransactionStorage
import com.ing.zknotary.node.services.getCordaServiceFromConfig
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.WireTransaction
import java.util.function.Predicate

abstract class ZKTransactionCordaService(val serviceHub: ServiceHub) : ZKTransactionService, SingletonSerializeAsToken() {

    private val vtxStorage: ZKWritableVerifierTransactionStorage by lazy { serviceHub.getCordaServiceFromConfig(ServiceNames.ZK_VERIFIER_TX_STORAGE) as ZKWritableVerifierTransactionStorage }

    override fun prove(
        wtx: WireTransaction,
        inputs: List<StateAndRef<ContractState>>,
        references: List<StateAndRef<ContractState>>
    ): ZKVerifierTransaction {

        val inputNonces = collectNonces(wtx.inputs)
        val referenceNonces = collectNonces(wtx.references)

        val witness = Witness(wtx, inputs, references, inputNonces, referenceNonces)

        val zkService = zkServiceForTx(wtx.zkCommandData())
        val proof = zkService.prove(witness)

        return ZKVerifierTransaction.fromWireTransaction(wtx, proof)
    }

    abstract fun zkServiceForTx(command: ZKCommandData): ZKService

    override fun verify(stx: SignedZKVerifierTransaction, checkSufficientSignatures: Boolean) {

        val tx = stx.tx

        // Check proof
        zkServiceForTx(tx.zkCommandData).verify(tx.proof, calculatePublicInput(tx))

        // Check signatures
        if (checkSufficientSignatures) {
            stx.verifyRequiredSignatures()
        } else {
            stx.checkSignaturesAreValid()
        }

        // Check output hashes
        stx.tx.verifyOutputsGroupHash()

        // Check backchain
        tx.inputs.forEach { validateBackchain(it) }
        tx.references.forEach { validateBackchain(it) }
    }

    private fun calculatePublicInput(tx: ZKVerifierTransaction): PublicInput {

        // Prepare public input
        val inputHashes = getUtxoHashes(tx.inputs)
        val referenceHashes = getUtxoHashes(tx.references)

        return PublicInput(
            tx.id,
            inputHashes = inputHashes,
            referenceHashes = referenceHashes
        )
    }

    private fun validateBackchain(stateRef: StateRef) {
        val prevVtx = vtxStorage.getTransaction(stateRef.txhash) ?: error("Should not happen")
        // TODO: Perhaps save this recursion until the end? Depends which order we want...
        verify(prevVtx, true)
    }

    private fun getUtxoHashes(stateRefs: List<StateRef>): List<SecureHash> {
        return stateRefs.map { stateRef ->
            val prevVtx = vtxStorage.getTransaction(stateRef.txhash)
                ?: error("Verifier tx not found for hash ${stateRef.txhash}")

            /*
             * To be able to verify that the stateRefs that are used in the transaction are correct, and unchanged from
             * when they were outputs in the previous tx, the verifier needs both the Merkle hash for each output and
             * the nonce that was used to create those Merkle hashes.
             *
             * These values will be used as part of the instance when verifying the proof.
             */
            prevVtx.tx.outputHashes[stateRef.index]
            /*
             * Now the verifier calls currentVtx.proof.verify(currentVtx.id, prevVtx.outputHashes).
             *
             * Inside the circuit, the prover proves:
             * - witnessTx.stateRefs[i] contents hashed with nonce from witness should equal instance.moveTxstateRefHashesFromPrevTx[i].
             *   This proves that prover did not change the contents of the state
             * - Recalculates witnessTx merkleRoot based on all components from the witness, including witnessTx.stateRefs.
             * - witnessTx.merkleRoot == instance.moveTx.id. This proves the witnessTx is the same as the ZKVerifierTransaction
             *   that the verifier is trying to verify. It also proves that the stateRefs consumed are indeed part of the
             *   transaction identified by the instance.
             */
        }
    }

    private fun collectNonces(stateRefs: List<StateRef>): List<SecureHash> {

        return stateRefs.map { stateRef ->
            val prevStx = serviceHub.validatedTransactions.getTransaction(stateRef.txhash)
                ?: error("Plaintext tx not found for hash ${stateRef.txhash}")

            val ftx = prevStx.buildFilteredTransaction(Predicate { true })
            val nonces = ftx.filteredComponentGroups.find { it.groupIndex == ComponentGroupEnum.OUTPUTS_GROUP.ordinal }!!.nonces

            nonces[stateRef.index]
        }
    }
}
