package com.ing.zknotary.common.client.flows.testflows

import co.paralleluniverse.fibers.Suspendable
import com.ing.zknotary.client.flows.ZKCollectSignaturesFlow
import com.ing.zknotary.client.flows.ZKFinalityFlow
import com.ing.zknotary.client.flows.ZKReceiveFinalityFlow
import com.ing.zknotary.client.flows.ZKSignTransactionFlow
import com.ing.zknotary.client.flows.signInitialZKTransaction
import com.ing.zknotary.common.client.flows.TestSerializationScheme
import com.ing.zknotary.common.zkp.ZKTransactionService
import com.ing.zknotary.node.services.ServiceNames
import com.ing.zknotary.node.services.getCordaServiceFromConfig
import com.ing.zknotary.testing.fixtures.contract.TestContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Disclaimer: this is not how it is supposed to be used in "real" flows, it works just for this test
 */
@InitiatingFlow
class MoveFlow(
    private val createStx: SignedTransaction,
    private val newOwner: Party
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val zkService: ZKTransactionService = serviceHub.getCordaServiceFromConfig(ServiceNames.ZK_TX_SERVICE)

        val session = initiateFlow(newOwner)

        val me = serviceHub.myInfo.legalIdentities.single()
        val state = createStx.coreTransaction.outRef<TestContract.TestState>(0)
        val command = Command(TestContract.Move(), listOf(newOwner, me).map { it.owningKey })
        val stateAndContract = StateAndContract(state.state.data.copy(owner = newOwner), TestContract.PROGRAM_ID)

        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.single())
        builder.withItems(state, stateAndContract, command)
        val ltx =
            builder.toWireTransaction(serviceHub, TestSerializationScheme.SCHEME_ID).toLedgerTransaction(serviceHub)

        // Transaction creator signs transaction.
        val stx = serviceHub.signInitialTransaction(builder)
        stx.verify(serviceHub, false)

        val vtx = zkService.prove(stx.tx, ltx.inputs, ltx.references)
        val partiallySignedVtx = signInitialZKTransaction(vtx)
        val svtx = subFlow(ZKCollectSignaturesFlow(stx, partiallySignedVtx, listOf(session)))

        subFlow(ZKFinalityFlow(stx, svtx, listOf(session)))

        return stx
    }

    companion object {

        @InitiatedBy(MoveFlow::class)
        class Verifier(val session: FlowSession) : FlowLogic<Unit>() {

            @Suspendable
            override fun call() {
                val flow = object : ZKSignTransactionFlow(session) {
                    @Suspendable
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        // In non-test scenario here counterparty can verify incoming Tx from business perspective
                    }
                }

                // Invoke the subFlow, in response to the counterparty calling [ZKCollectSignaturesFlow].
                val stx = subFlow(flow)

                // Invoke flow in response to ZKFinalityFlow
                subFlow(ZKReceiveFinalityFlow(session, stx))
            }
        }
    }
}
