package com.ing.zkflow.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.ing.zkflow.common.transactions.NotarisedTransactionPayload
import com.ing.zkflow.common.transactions.PrivateNotarisedTransactionPayload
import com.ing.zkflow.common.transactions.SignedZKVerifierTransaction
import com.ing.zkflow.common.transactions.prove
import com.ing.zkflow.common.transactions.resolveStateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TraversableTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Verifies the given transaction, then sends it to the named notary. If the notary agrees that the transaction
 * is acceptable then it is from that point onwards committed to the ledger, and will be written through to the
 * vault. Additionally it will be distributed to the parties reflected in the participants list of the states.
 *
 * The transaction is expected to have already been resolved: if its dependencies are not available in local
 * storage, verification will fail. It must have signatures from all necessary parties other than the notary.
 *
 * A list of [FlowSession]s is required for each non-local participant of the transaction. These participants will receive
 * the final notarised transaction by calling [ZKReceiveFinalityFlow] in their counterpart com.ing.zkflow.flows. Sessions with non-participants
 * can also be included, but they must specify [StatesToRecord.ALL_VISIBLE] for statesToRecord if they wish to record the
 * contract states into their vaults.
 *
 * The flow returns the same transaction but with the additional signatures from the notary.
 *
 * NOTE: This is an inlined flow but for backwards compatibility is annotated with [InitiatingFlow].
 */
// To maintain backwards compatibility with the old API, FinalityFlow can act both as an initiating flow and as an inlined flow.
// This is only possible because a flow is only truly initiating when the first call to initiateFlow is made (where the
// presence of @InitiatingFlow is checked). So the new API is inlined simply because that code path doesn't call initiateFlow.
@InitiatingFlow
class ZKFinalityFlow private constructor(
    val stx: SignedTransaction,
    override val progressTracker: ProgressTracker,
    /**
     * The sessions that will receive the full transaction data, including all private data.
     */
    private val privateSessions: Collection<FlowSession>,
    /**
     * The sessions that will receive only the public transaction data
     */
    private val publicSessions: Collection<FlowSession>,
) : FlowLogic<SignedTransaction>() {

    /**
     * Notarise the given transaction and broadcast it to all the participants.
     *
     * @param transaction What to commit.
     * @param publicSessions A collection of [FlowSession]s for each non-local participant of the transaction. Sessions to non-participants can
     * also be provided.
     */
    @JvmOverloads
    constructor(
        transaction: SignedTransaction,
        privateSessions: Collection<FlowSession>,
        publicSessions: Collection<FlowSession> = emptyList(),
        progressTracker: ProgressTracker = tracker()
    ) : this(transaction, progressTracker, privateSessions = privateSessions, publicSessions = publicSessions)

    companion object {
        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service") {
            override fun childProgressTracker() = NotaryFlow.Client.tracker()
        }

        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

        @JvmStatic
        fun tracker() = ProgressTracker(
            NOTARISING,
            BROADCASTING
        )
    }

    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): SignedTransaction {
        // Note: this method is carefully broken up to minimize the amount of data reachable from the stack at
        // the point where subFlow is invoked, as that minimizes the checkpointing work to be done.
        //
        // Lookup the resolved transactions and use them to map each signed transaction to the list of participants.
        // Then send to the notary if needed, record locally and distribute.

        logCommandData()

        val sessionParties = (publicSessions + privateSessions).map { it.counterparty }.toSet()
        require(sessionParties.none { serviceHub.myInfo.isLegalIdentity(it) }) {
            "Do not provide flow sessions for the local node. ZKFinalityFlow will record the notarised transaction locally." +
                "\nLocal node identities: ${serviceHub.myInfo.legalIdentities}, " +
                "\nPrivate SessionParties: ${privateSessions.map { it.counterparty }} " +
                "\nPublic SessionParties: ${publicSessions.map { it.counterparty }} "
        }

        val externalTxParticipants = extractExternalParticipants(stx.tx)
        val missingRecipients = externalTxParticipants - sessionParties
        require(missingRecipients.isEmpty()) {
            "Flow sessions were not provided for the following transaction participants: $missingRecipients"
        }

        val notarised = notariseAndRecord()

        progressTracker.currentStep =
            BROADCASTING

        // We send the full `PrivateNotarisedTransactionPayload` to the private sessions
        for (session in privateSessions) {
            try {
                subFlow(SendNotarisedTransactionPayloadFlow(session, notarised))
                logger.info("Party ${session.counterparty} received the full SignedTransaction and the private ZKVerifierTransaction.")
            } catch (e: UnexpectedFlowEndException) {
                throw UnexpectedFlowEndException(
                    "${session.counterparty} has finished prematurely and we're trying to send them the finalised transaction. " +
                        "Did they forget to call ZKReceiveFinalityFlow? (${e.message})",
                    e.cause,
                    e.originalErrorId
                )
            }
        }

        // We send only the public `PublicNotarisedTransactionPayload` to the public sessions
        for (session in publicSessions) {
            try {
                subFlow(SendNotarisedTransactionPayloadFlow(session, notarised.toPublic()))
                logger.info("Party ${session.counterparty} received the private ZKVerifierTransaction.")
            } catch (e: UnexpectedFlowEndException) {
                throw UnexpectedFlowEndException(
                    "${session.counterparty} has finished prematurely and we're trying to send them the finalised transaction. " +
                        "Did they forget to call ZKReceiveFinalityFlow? (${e.message})",
                    e.cause,
                    e.originalErrorId
                )
            }
        }

        logger.info("All parties received the transaction successfully.")

        return notarised.stx
    }

    private fun logCommandData() {
        if (logger.isDebugEnabled) {
            val commandDataTypes =
                stx.tx.commands.asSequence().mapNotNull { it.value::class.qualifiedName }.distinct()
            logger.debug("Started finalization, commands are ${commandDataTypes.joinToString(", ", "[", "]")}.")
        }
    }

    @Suspendable
    private fun notariseAndRecord(): PrivateNotarisedTransactionPayload {
        // Create proof and vtx
        val vtx = stx.prove(serviceHub)

        val notarisedSvtx = if (needsNotarySignature(vtx)) {
            progressTracker.currentStep =
                NOTARISING
            val notarySignatures = subFlow(ZKNotaryFlow(stx, vtx))
            vtx + notarySignatures
        } else {
            logger.info("No need to notarise this transaction.")
            vtx
        }
        logger.info("Recording transaction locally.")
        val notarised = PrivateNotarisedTransactionPayload(notarisedSvtx, SignedTransaction(stx.tx, notarisedSvtx.sigs))
        serviceHub.recordTransactions(notarised)
        logger.info("Recorded transaction locally successfully.")
        return notarised
    }

    private fun needsNotarySignature(stx: SignedZKVerifierTransaction): Boolean {
        val wtx = stx.tx
        val needsNotarisation = wtx.inputs.isNotEmpty() || wtx.references.isNotEmpty() || wtx.timeWindow != null
        return needsNotarisation && hasNoNotarySignature(stx)
    }

    private fun hasNoNotarySignature(stx: SignedZKVerifierTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.asSequence().map { it.by }.toSet()
        return notaryKey?.isFulfilledBy(signers) != true
    }

    private fun extractExternalParticipants(tx: TraversableTransaction): Set<Party> {
        val inputStates = tx.inputs.map {
            SerializedStateAndRef(resolveStateRef(it, serviceHub, includePrivate = true), it).toStateAndRef().state.data
        }
        val participants = tx.outputStates.flatMap { it.participants } + inputStates.flatMap { it.participants }
        return groupAbstractPartyByWellKnownParty(serviceHub, participants).keys - serviceHub.myInfo.legalIdentities
    }
}

/**
 * The receiving counterpart to [FinalityFlow].
 *
 * All parties who are receiving a finalised transaction from a sender flow must subcall this flow in their own flows.
 *
 * It's typical to have already signed the transaction proposal in the same workflow using [ZKSignTransactionFlow]. If so
 * then the transaction ID can be passed in as an extra check to ensure the finalised transaction is the one that was signed
 * before it's committed to the vault.
 *
 * @param otherSideSession The session which is providing the transaction to record.
 * @param statesToRecord Which states to commit to the vault. Defaults to [StatesToRecord.ONLY_RELEVANT].
 */
class ZKReceiveFinalityFlow @JvmOverloads constructor(
    private val otherSideSession: FlowSession,
    private val expectedTxId: SecureHash? = null,
    private val statesToRecord: StatesToRecord = StatesToRecord.ONLY_RELEVANT
) : FlowLogic<SignedZKVerifierTransaction>() {
    @Suspendable
    override fun call(): SignedZKVerifierTransaction {
        return subFlow(object : ZKReceiveNotarisedTransactionPayloadFlow(
            otherSideSession,
            checkSufficientSignatures = true,
            statesToRecord = statesToRecord
        ) {
                override fun checkBeforeRecording(notarised: NotarisedTransactionPayload) {
                    expectedTxId?.let {
                        require(it == notarised.svtx.id) {
                            "We expected to receive transaction with ID $it but instead got ${notarised.svtx.id}. Transaction was" +
                                "not recorded, nor its states sent to the vault."
                        }
                    }
                }
            })
    }
}
