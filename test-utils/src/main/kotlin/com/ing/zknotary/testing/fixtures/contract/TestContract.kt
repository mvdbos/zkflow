package com.ing.zknotary.testing.fixtures.contract

import com.ing.serialization.bfl.annotations.FixedLength
import com.ing.zknotary.common.contracts.ZKCommandData
import com.ing.zknotary.common.contracts.ZKOwnableState
import com.ing.zknotary.common.contracts.ZKTransactionMetadataCommandData
import com.ing.zknotary.common.serialization.bfl.CommandDataSerializerMap
import com.ing.zknotary.common.serialization.bfl.ContractStateSerializerMap
import com.ing.zknotary.common.zkp.metadata.ZKCommandMetadata
import com.ing.zknotary.common.zkp.metadata.ZKTransactionMetadata
import com.ing.zknotary.common.zkp.metadata.commandMetadata
import com.ing.zknotary.common.zkp.metadata.transactionMetadata
import com.ing.zknotary.testing.fixtures.contract.TestContract.Create.Companion.verifyCreate
import com.ing.zknotary.testing.fixtures.contract.TestContract.Move.Companion.verifyMove
import com.ing.zknotary.testing.fixtures.contract.TestContract.MoveBidirectional.Companion.verifyMoveBidirectional
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.LedgerTransaction
import java.io.File
import java.util.Random

public val testSerializers: Unit = run {
    ContractStateSerializerMap.register(TestContract.TestState::class, 1, TestContract.TestState.serializer())
    CommandDataSerializerMap.register(TestContract.Create::class, 2, TestContract.Create.serializer())
    CommandDataSerializerMap.register(TestContract.Move::class, 3, TestContract.Move.serializer())
    CommandDataSerializerMap.register(TestContract.MoveBidirectional::class, 4, TestContract.MoveBidirectional.serializer())
    CommandDataSerializerMap.register(TestContract.SignOnly::class, 5, TestContract.SignOnly.serializer())
}

public class TestContract : Contract {
    public companion object {
        public const val PROGRAM_ID: ContractClassName = "com.ing.zknotary.testing.fixtures.contract.TestContract"
    }

    @Serializable
    @BelongsToContract(TestContract::class)
    public data class TestState(
        override val owner: @Contextual AnonymousParty,
        val value: Int = Random().nextInt(1000)
    ) : ZKOwnableState {
        init {
            // TODO: Hack to trigger the registration of the serializerMap above
            testSerializers
        }

        @FixedLength([2])
        override val participants: List<@Contextual AnonymousParty> = listOf(owner)

        override fun withNewOwner(newOwner: AnonymousParty): CommandAndState =
            CommandAndState(Move(), copy(owner = newOwner))
    }

    // Commands
    @Serializable
    public class Create : TypeOnlyCommandData(), ZKCommandData, ZKTransactionMetadataCommandData {
        @Transient
        override val transactionMetadata: ZKTransactionMetadata = transactionMetadata {
            commands {
                +Create::class
            }
        }

        @Transient
        override val metadata: ZKCommandMetadata = commandMetadata {
            private = true
            circuit {
                buildFolder =
                    File("${System.getProperty("user.dir")}/../zinc-platform-sources/build/circuits/create")
            }
            outputs { 1 of TestState::class }
            numberOfSigners = 1
        }

        public companion object {
            public fun verifyCreate(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                if (tx.outputs.size != 1) throw IllegalArgumentException("Failed requirement: the tx has only one output")
                if (tx.inputs.isNotEmpty()) throw IllegalArgumentException("Failed requirement: the tx has no inputs")

                // Transaction contents
                val output = tx.getOutput(0) as TestState
                if (output.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: the output state is owned by the command signer")
            }
        }
    }

    /**
     *
     * This command is only used on [CollectSignaturesFlowTest]. It expects two signatures, but nothing else.
     */
    @Serializable
    public class SignOnly : TypeOnlyCommandData(), ZKCommandData {
        @Transient
        override val metadata: ZKCommandMetadata = commandMetadata {}
    }

    @Serializable
    public class Move : TypeOnlyCommandData(), ZKCommandData, ZKTransactionMetadataCommandData {
        @Transient
        override val transactionMetadata: ZKTransactionMetadata = transactionMetadata {
            commands {
                +Move::class
            }
        }

        @Transient
        override val metadata: ZKCommandMetadata = commandMetadata {
            private = true
            circuit {
                buildFolder =
                    File("${System.getProperty("user.dir")}/../zinc-platform-sources/build/circuits/move")
            }
            inputs { 1 of TestState::class }
            outputs { 1 of TestState::class }
            numberOfSigners = 1
        }

        public companion object {
            public fun verifyMove(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                if (tx.outputs.size != 1) throw IllegalArgumentException("Failed requirement: the tx has only one output")
                if (tx.inputs.size != 1) throw IllegalArgumentException("Failed requirement: the tx has only one input")

                // Transaction contents
                val output = tx.getOutput(0) as TestState
                val input = tx.getInput(0) as TestState

                if (input.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: the input state is owned by a required command signer")
                if (input.value != output.value) throw IllegalArgumentException("Failed requirement: the value of the input and out put should be equal")
            }
        }
    }

    @Serializable
    public class MoveBidirectional : ZKCommandData {
        @Transient
        override val metadata: ZKCommandMetadata = commandMetadata {
            inputs { 2 of TestState::class }
            outputs { 2 of TestState::class }
            numberOfSigners = 2
        }

        public companion object {
            public fun verifyMoveBidirectional(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                if (tx.outputs.size != 2) throw IllegalArgumentException("Failed requirement: the tx has two outputs")
                if (tx.inputs.size != 2) throw IllegalArgumentException("Failed requirement: the tx has two inputs")

                if (tx.inputStates.sumBy { (it as TestState).value } != tx.outputStates.sumBy { (it as TestState).value }) throw IllegalArgumentException(
                    "Failed requirement: amounts are not conserved"
                )

                tx.inputStates.forEachIndexed { index, input ->
                    // Transaction contents
                    val output = tx.getOutput(index) as TestState
                    input as TestState

                    if (input.owner.owningKey == output.owner.owningKey) throw IllegalArgumentException("Failed requirement: input state $index changes ownership")
                    if ((tx.outputStates.reversed()[index] as TestState).owner.owningKey != input.owner.owningKey) throw IllegalArgumentException(
                        "Failed requirement: ownership of input $index should swap ownership"
                    )

                    if (input.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: input state $index is owned by a required command signer")

                    if (input.value != output.value) throw IllegalArgumentException("Failed requirement: the value of the input and out put should be equal")
                }
            }
        }
    }

    override fun verify(tx: LedgerTransaction) {
        // The transaction may have only one command, of a type defined above
        if (tx.commands.size != 1) throw IllegalArgumentException("Failed requirement: the tx has only one command")
        val command = tx.commands[0]

        when (command.value) {
            is Create -> verifyCreate(tx, command)
            is Move -> verifyMove(tx, command)
            is MoveBidirectional -> verifyMoveBidirectional(tx, command)
            is SignOnly -> {
            }
            else -> {
                throw IllegalStateException("No valid command found")
            }
        }
    }
}
