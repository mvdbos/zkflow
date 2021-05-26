package io.ivno.collateraltoken.workflow.deposit

import co.paralleluniverse.fibers.Suspendable
import io.ivno.collateraltoken.workflow.*
import io.onixlabs.corda.core.workflow.currentStep
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

class AcceptDepositPaymentFlowHandler(
    private val session: FlowSession,
    override val progressTracker: ProgressTracker = tracker()
) : FlowLogic<SignedTransaction>() {

    companion object {
        @JvmStatic
        fun tracker() = ProgressTracker(SYNCHRONIZING, SIGNING, RECORDING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        synchronizeMembershipHandler(session)
        resolveAccountHandler(session)
        countersignHandler(session)
        return finalizeHandler(session)
    }

    @InitiatedBy(AcceptDepositPaymentFlow.Initiator::class)
    private class Handler(private val session: FlowSession) : FlowLogic<SignedTransaction>() {

        private companion object {
            object HANDLING : ProgressTracker.Step("Handling deposit payment acceptance.") {
                override fun childProgressTracker(): ProgressTracker = tracker()
            }
        }

        override val progressTracker: ProgressTracker = ProgressTracker(HANDLING)

        @Suspendable
        override fun call(): SignedTransaction {
            currentStep(HANDLING)
            return subFlow(AcceptDepositPaymentFlowHandler(session, HANDLING.childProgressTracker()))
        }
    }
}
