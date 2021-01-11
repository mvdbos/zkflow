package com.ing.zknotary.common.transactions

import com.ing.zknotary.common.contracts.toZKCommand
import com.ing.zknotary.common.util.ComponentPaddingConfiguration
import com.ing.zknotary.common.util.PaddingWrapper
import com.ing.zknotary.common.zkp.Witness
import com.ing.zknotary.node.services.ServiceNames
import com.ing.zknotary.node.services.ZKVerifierTransactionStorage
import com.ing.zknotary.node.services.ZKWritableVerifierTransactionStorage
import com.ing.zknotary.node.services.getCordaServiceFromConfig
import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.BLAKE2s256DigestService
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.loggerFor
import java.nio.ByteBuffer
import kotlin.math.max

@DeleteForDJVM
fun ZKProverTransaction.prettyPrint(): String {
    val buf = StringBuilder()
    buf.appendln("Prover Transaction:")

    fun addComponentList(buf: StringBuilder, name: String, componentList: List<*>) {
        if (componentList.isNotEmpty()) buf.appendln(" - $name:")
        for ((index, component) in componentList.withIndex()) {
            buf.appendln("\t[$index]:\t$component")
        }
    }

    addComponentList(buf, "REFS", references)
    addComponentList(buf, "INPUTS", inputs)
    addComponentList(buf, "OUTPUTS", outputs)
    buf.appendln(" - COMMAND:  $command")
    addComponentList(buf, "ATTACHMENT HASHES", attachments)

    if (networkParametersHash != null) {
        buf.appendln(" - PARAMETERS HASH:  $networkParametersHash")
    }
    return buf.toString()
}

fun ZKProverTransaction.toZKVerifierTransaction(proof: ByteArray): ZKVerifierTransaction {
    loggerFor<ZKProverTransaction>().debug("Converting ProverTx tot VerifierTx")

    // IMPORTANT: this should only include the nonces for the components that are visible in the ZKVerifierTransaction
    val componentNonces = this.merkleTree.componentNonces.filterKeys {
        it in listOf(
            ComponentGroupEnum.INPUTS_GROUP.ordinal,
            ComponentGroupEnum.REFERENCES_GROUP.ordinal,
            ComponentGroupEnum.NOTARY_GROUP.ordinal,
            ComponentGroupEnum.TIMEWINDOW_GROUP.ordinal,
            ComponentGroupEnum.PARAMETERS_GROUP.ordinal,
            ComponentGroupEnum.SIGNERS_GROUP.ordinal
        )
    }

    val stateRefInputsFiller = ComponentPaddingConfiguration.Filler.StateRef(
        (this.padded.paddingConfiguration.filler(ComponentGroupEnum.INPUTS_GROUP) as ComponentPaddingConfiguration.Filler.StateAndRef).content.ref
    )
    val stateRefReferencesFiller = ComponentPaddingConfiguration.Filler.StateRef(
        (this.padded.paddingConfiguration.filler(ComponentGroupEnum.REFERENCES_GROUP) as ComponentPaddingConfiguration.Filler.StateAndRef).content.ref
    )
    val componentPadding = ComponentPaddingConfiguration.Builder()
        .inputs(
            this.padded.sizeOf(ComponentGroupEnum.INPUTS_GROUP),
            stateRefInputsFiller
        )
        .outputs(
            this.padded.sizeOf(ComponentGroupEnum.OUTPUTS_GROUP),
            this.padded.paddingConfiguration.filler(ComponentGroupEnum.OUTPUTS_GROUP)!!
        )
        .references(
            this.padded.sizeOf(ComponentGroupEnum.REFERENCES_GROUP),
            stateRefReferencesFiller
        )
        .attachments(
            this.padded.sizeOf(ComponentGroupEnum.ATTACHMENTS_GROUP),
            this.padded.paddingConfiguration.filler(ComponentGroupEnum.ATTACHMENTS_GROUP)!!
        )
        .signers(
            this.padded.sizeOf(ComponentGroupEnum.SIGNERS_GROUP),
            this.padded.paddingConfiguration.filler(ComponentGroupEnum.SIGNERS_GROUP)!!
        )
        .build()

    // TODO
    // This construction of the circuit id is temporary and will be replaced in the subsequent work.
    // The proper id must identify circuit and its version.
    val circuitId = SecureHash.sha256(ByteBuffer.allocate(4).putInt(this.command.value.id).array())

    return ZKVerifierTransaction(
        proof,
        this.inputs.map { it.ref },
        this.references.map { it.ref },
        circuitId,

        this.command.signers,

        this.notary,
        this.timeWindow,
        this.networkParametersHash,

        this.componentGroupLeafDigestService,
        this.nodeDigestService,

        this.merkleTree.componentHashes[ComponentGroupEnum.OUTPUTS_GROUP.ordinal] ?: emptyList(),
        this.merkleTree.groupHashes,
        componentNonces,

        componentPadding
    )
}

/**
 * This function deterministically creates a [ZKProverTransaction] from a [WireTransaction].
 *
 * This is deterministic, because the [ZKProverTransaction] reuses the [PrivacySalt] from the WireTransaction.
 */
fun WireTransaction.toZKProverTransaction(
    services: ServiceHub,
    zkVerifierTransactionStorage: ZKVerifierTransactionStorage = services.getCordaServiceFromConfig(ServiceNames.ZK_VERIFIER_TX_STORAGE),
    componentGroupLeafDigestService: DigestService = BLAKE2s256DigestService,
    nodeDigestService: DigestService = componentGroupLeafDigestService
): ZKProverTransaction {
    loggerFor<WireTransaction>().debug("Converting WireTx to ProverTx")

    require(commands.size == 1) { "There must be exactly one command on a ZKProverTransaction" }

    // Look up the ZKid for each WireTransaction.id
    fun List<StateAndRef<*>>.mapToZkid(): List<StateAndRef<*>> {
        return map {
            val zkid = checkNotNull(zkVerifierTransactionStorage.map.get(it.ref.txhash)) {
                "Unexpectedly could not find the tx id map for ${it.ref.txhash}. Did you run ResolveTransactionsFlow before?"
            }
            StateAndRef(it.state, StateRef(zkid, it.ref.index))
        }
    }

    val ltx = toLedgerTransaction(services)

    return ZKProverTransaction(
        inputs = ltx.inputs.mapToZkid(),
        outputs = ltx.outputs.map { TransactionState(data = it.data, notary = it.notary) },
        references = ltx.references.mapToZkid(),
        command = ltx.commands.single().toZKCommand(),
        notary = ltx.notary!!,
        timeWindow = ltx.timeWindow,
        privacySalt = ltx.privacySalt,
        networkParametersHash = ltx.networkParameters?.serialize()?.hash,
        attachments = ltx.attachments.map { it.id },
        componentGroupLeafDigestService = componentGroupLeafDigestService,
        nodeDigestService = nodeDigestService
    )
}

fun toWitness(
    ptx: ZKProverTransaction,
    serviceHub: ServiceHub,
    vtxStorage: ZKWritableVerifierTransactionStorage,
    padding: ComponentPaddingConfiguration = ptx.padded.paddingConfiguration,
    componentGroupLeafDigestService: DigestService = BLAKE2s256DigestService
): Witness {
    loggerFor<ZKProverTransaction>().debug("Creating Witness from ProverTx")
    // Because the PrivacySalt of the WireTransaction is reused to create the ProverTransactions,
    // the nonces are also identical from WireTransaction to ZKProverTransaction.
    // This means we can collect the UTXO nonces for the inputs and references of the wiretransaction and it should
    // just work.
    // When we move to full backchain privacy and no longer have the WireTransactions at all, we will
    // promote the ZKProverTransactions to first-class citizens and then they will be saved in the vault as WireTransactions
    // are now.
    fun List<PaddingWrapper<StateAndRef<ContractState>>>.collectUtxoNonces() = mapIndexed { _, it ->
        when (it) {
            is PaddingWrapper.Filler -> {
                // When it is a padded state, the nonce is ALWAYS a zerohash of the algo used for merkle tree leaves
                componentGroupLeafDigestService.zeroHash
            }
            is PaddingWrapper.Original -> {
                // When it is an original state, we look up the tx it points to and collect the nonce for the UTXO it points to.
                val wtxId = vtxStorage.map.getWtxId(it.content.ref.txhash)
                    ?: error("Mapping to Wtx not found for vtxId: ${it.content.ref.txhash}")

                val outputTx = serviceHub.validatedTransactions.getTransaction(wtxId)
                    ?: error("Could not fetch output transaction for StateRef ${it.content.ref}")

                val filler = padding.filler(ComponentGroupEnum.OUTPUTS_GROUP)
                require(filler is ComponentPaddingConfiguration.Filler.TransactionState) { "Expected filler of type TransactionState" }

                outputTx.tx.outputs.wrappedPad(
                    padding.sizeOf(ComponentGroupEnum.OUTPUTS_GROUP) ?: error("Padding configuration not found"),
                    filler.content
                )

                componentGroupLeafDigestService.hash(
                    ptx.privacySalt.bytes + ByteBuffer.allocate(8)
                        .putInt(ComponentGroupEnum.OUTPUTS_GROUP.ordinal).putInt(it.content.ref.index).array()
                )
            }
        }
    }

    // Collect the nonces for the outputs pointed to by the inputs and references.
    val inputNonces = ptx.padded.inputs().collectUtxoNonces()
    val referenceNonces = ptx.padded.references().collectUtxoNonces()

    return Witness(ptx, inputNonces = inputNonces, referenceNonces = referenceNonces)
}

/**
 * Extends a list with a default value.
 */
fun <T> List<T>.pad(n: Int, default: T) = List(max(size, n)) {
    if (it < size)
        this[it]
    else {
        default
    }
}

fun <T> List<T>.wrappedPad(n: Int, default: T) =
    map { PaddingWrapper.Original(it) }.pad(n, PaddingWrapper.Filler(default))

fun <T> T?.wrappedPad(default: T) =
    if (this == null) PaddingWrapper.Filler(default) else PaddingWrapper.Original(this)
