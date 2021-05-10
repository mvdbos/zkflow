package io.ivno.collateraltoken.workflow.deposit

import co.paralleluniverse.fibers.Suspendable
import io.ivno.collateraltoken.contract.Deposit
import io.ivno.collateraltoken.workflow.*
import io.onixlabs.corda.core.workflow.currentStep
import io.onixlabs.corda.core.workflow.initiateFlows
import io.onixlabs.corda.identityframework.workflow.checkHasSufficientFlowSessions
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

class RejectDepositPaymentFlow(
    private val oldDeposit: StateAndRef<Deposit>,
    private val newDeposit: Deposit,
    private val sessions: Set<FlowSession>,
    override val progressTracker: ProgressTracker = tracker()
) : FlowLogic<SignedTransaction>() {

    companion object {
        @JvmStatic
        fun tracker() = ProgressTracker(INITIALIZING, GENERATING, VERIFYING, SIGNING, FINALIZING)

        private const val FLOW_VERSION_1 = 1
    }

    @Suspendable
    override fun call(): SignedTransaction {
        currentStep(INITIALIZING)
        checkHasSufficientFlowSessions(sessions, newDeposit)

        val tokenType = newDeposit.amount.amountType.resolve(serviceHub)
        val membershipReferences = synchronizeMembership(tokenType.state.data.network, newDeposit, sessions)

        val unsignedTransaction = transaction(oldDeposit.state.notary) {
            addMembershipReferences(membershipReferences)
            addDepositAdvance(oldDeposit, newDeposit)
        }

        val fullySignedTransaction = verifyAndSign(unsignedTransaction, newDeposit.custodian.owningKey)
        return finalize(fullySignedTransaction, sessions)
    }

    @StartableByRPC
    @InitiatingFlow(version = FLOW_VERSION_1)
    class Initiator(
        private val oldDeposit: StateAndRef<Deposit>,
        private val newDeposit: Deposit,
        private val observers: Set<Party> = emptySet()
    ) : FlowLogic<SignedTransaction>() {

        private companion object {
            object REJECTING : ProgressTracker.Step("Rejecting deposit payment.") {
                override fun childProgressTracker(): ProgressTracker = tracker()
            }
        }

        override val progressTracker: ProgressTracker = ProgressTracker(REJECTING)

        @Suspendable
        override fun call(): SignedTransaction {
            currentStep(REJECTING)
            return subFlow(
                RejectDepositPaymentFlow(
                    oldDeposit,
                    newDeposit,
                    initiateFlows(observers, newDeposit),
                    REJECTING.childProgressTracker()
                )
            )
        }
    }
}
