package com.ing.zknotary.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.ing.zknotary.common.transactions.ZKVerifierTransaction
import com.ing.zknotary.common.transactions.toZKVerifierTransaction
import com.ing.zknotary.common.zkp.ZKConfig
import com.ing.zknotary.node.services.toZKVerifierTransaction
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotarisationResponse
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.internal.notary.generateSignature
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.UntrustworthyData

open class ZKNotaryFlow(
    private val stx: SignedTransaction,
    private val zkConfig: ZKConfig
) : NotaryFlow.Client(stx) {

    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): List<TransactionSignature> {
        val notaryParty = checkTransaction()
        val response = zkNotarise(notaryParty)
        return validateResponse(response, notaryParty)
    }

    /** Notarises the transaction with the [notaryParty], obtains the notary's signature(s). */
    @Throws(NotaryException::class)
    @Suspendable
    protected fun zkNotarise(notaryParty: Party): UntrustworthyData<NotarisationResponse> {
        val session = initiateFlow(notaryParty)
        val requestSignature = generateRequestSignature()
        return if (isValidating(notaryParty)) {
            throw NotaryException(NotaryError.TransactionInvalid(Throwable("Validating notaries can never handle ZKTransactions")))
        } else {
            // TODO: find a way to check that this notary is actually running ZKNotaryServiceFlow (className property?)
            sendAndReceiveNonValidatingWithZKProof(notaryParty, session, requestSignature)
        }
    }

    @Suspendable
    private fun sendAndReceiveNonValidatingWithZKProof(
        notaryParty: Party,
        session: FlowSession,
        signature: NotarisationRequestSignature
    ): UntrustworthyData<NotarisationResponse> {
        val ctx = stx.coreTransaction
        val tx = when (ctx) {
            is ContractUpgradeWireTransaction -> ctx.buildFilteredTransaction()
            is WireTransaction -> buildVerifierTransaction(stx, notaryParty)
            else -> ctx
        }
        // TODO: re-enable this when this flow is refactored to use the ZKProverTransaction
        // session.send(ZKNotarisationPayload(tx, signature))
        return receiveResultOrTiming(session)
    }

    private fun buildVerifierTransaction(stx: SignedTransaction, notaryParty: Party): ZKVerifierTransaction {
        return stx.toZKVerifierTransaction(
            services = serviceHub,
            zkStorage = zkConfig.zkStorage,
            zktransactionService = zkConfig.zkTransactionService
        )
    }

    /****************************************************
     * Copies of private methods from NotaryFlow.Client *
     ****************************************************/
    private fun isValidating(notaryParty: Party): Boolean {
        val onTheCurrentWhitelist = serviceHub.networkMapCache.isNotary(notaryParty)
        return if (!onTheCurrentWhitelist) {
            /*
                Note that the only scenario where it's acceptable to use a notary not in the current network parameter whitelist is
                when performing a notary change transaction after a network merge – the old notary won't be on the whitelist of the new network,
                and can't be used for regular transactions.
            */
            check(stx.coreTransaction is NotaryChangeWireTransaction) {
                "Notary $notaryParty is not on the network parameter whitelist. A non-whitelisted notary can only be used for notary change transactions"
            }
            val historicNotary =
                (serviceHub.networkParametersService as NetworkParametersStorage).getHistoricNotary(notaryParty)
                    ?: throw IllegalStateException("The notary party $notaryParty specified by transaction ${stx.id}, is not recognised as a current or historic notary.")
            historicNotary.validating
        } else serviceHub.networkMapCache.isValidatingNotary(notaryParty)
    }

    /**
     * Ensure that transaction ID instances are not referenced in the serialized form in case several input states are outputs of the
     * same transaction.
     */
    private fun generateRequestSignature(): NotarisationRequestSignature {
        // TODO: This is not required any more once our AMQP serialization supports turning off object referencing.
        val notarisationRequest =
            NotarisationRequest(stx.inputs.map { it.copy(txhash = SecureHash.parse(it.txhash.toString())) }, stx.id)
        return notarisationRequest.generateSignature(serviceHub)
    }
}
