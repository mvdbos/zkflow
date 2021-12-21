package com.ing.zkflow.testing.fixtures.contract

import com.ing.serialization.bfl.annotations.FixedLength
import com.ing.zkflow.common.contracts.ZKOwnableState
import com.ing.zkflow.common.contracts.ZKTransactionMetadataCommandData
import com.ing.zkflow.common.transactions.zkFLowMetadata
import com.ing.zkflow.common.zkp.metadata.ResolvedZKCommandMetadata
import com.ing.zkflow.common.zkp.metadata.ResolvedZKTransactionMetadata
import com.ing.zkflow.common.zkp.metadata.commandMetadata
import com.ing.zkflow.common.zkp.metadata.transactionMetadata
import com.ing.zkflow.serialization.CommandDataSerializerMap
import com.ing.zkflow.serialization.ContractStateSerializerMap
import com.ing.zkflow.testing.fixtures.contract.TestContract.Create.Companion.verifyCreate
import com.ing.zkflow.testing.fixtures.contract.TestContract.Move.Companion.verifyMove
import com.ing.zkflow.testing.fixtures.contract.TestContract.MoveBidirectional.Companion.verifyMoveBidirectional
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.LedgerTransaction
import java.io.File
import java.util.Random

public class TestContract : Contract {
    public companion object {
        public const val PROGRAM_ID: ContractClassName = "com.ing.zkflow.testing.fixtures.contract.TestContract"
    }

    @Serializable
    @BelongsToContract(TestContract::class)
    public data class TestState(
        override val owner: @Contextual AnonymousParty,
        val value: Int = Random().nextInt(1000)
    ) : ZKOwnableState {
        init {
            ContractStateSerializerMap.register(this::class)
        }

        public companion object {
            public const val PARTICIPANT_COUNT: Int = 1
        }

        @FixedLength([PARTICIPANT_COUNT])
        override val participants: List<@Contextual AnonymousParty> = listOf(owner)

        override fun withNewOwner(newOwner: AnonymousParty): CommandAndState =
            CommandAndState(Move(), copy(owner = newOwner))
    }

    // Commands
    @Serializable
    public class Create : ZKTransactionMetadataCommandData {
        override val transactionMetadata: ResolvedZKTransactionMetadata by transactionMetadata {
            network {
                attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
            }
            commands {
                +Create::class
            }
        }

        @Transient
        override val metadata: ResolvedZKCommandMetadata = commandMetadata {
            circuit {
                buildFolder =
                    File("${System.getProperty("user.dir")}/../zinc-platform-sources/build/circuits/create")
            }
            privateOutputs { 1 private TestState::class }
            numberOfSigners = 1
            attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
        }

        init {
            CommandDataSerializerMap.register(this::class)
        }

        public companion object {
            public fun verifyCreate(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                tx.zkFLowMetadata.verify(tx)

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
    public class SignOnly : ZKTransactionMetadataCommandData {
        override val transactionMetadata: ResolvedZKTransactionMetadata by transactionMetadata {
            network {
                attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
            }
            commands {
                +SignOnly::class
            }
        }

        @Transient
        override val metadata: ResolvedZKCommandMetadata = commandMetadata {
            privateOutputs { 1 private TestState::class }
            numberOfSigners = 2
            attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
        }

        init {
            CommandDataSerializerMap.register(this::class)
        }
    }

    @Serializable
    public class Move : ZKTransactionMetadataCommandData {
        override val transactionMetadata: ResolvedZKTransactionMetadata by transactionMetadata {
            network {
                attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
            }
            commands {
                +Move::class
            }
        }

        @Transient
        override val metadata: ResolvedZKCommandMetadata = commandMetadata {
            circuit {
                buildFolder =
                    File("${System.getProperty("user.dir")}/../zinc-platform-sources/build/circuits/move")
            }
            privateInputs { 1 private TestState::class }
            privateOutputs { 1 private TestState::class }
            numberOfSigners = 2
            attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
        }

        init {
            CommandDataSerializerMap.register(this::class)
        }

        public companion object {
            public fun verifyMove(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                tx.zkFLowMetadata.verify(tx)

                // Transaction contents
                val output = tx.getOutput(0) as TestState
                val input = tx.getInput(0) as TestState

                if (input.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: the input state is owned by a required command signer")
                if (output.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: the outputs state is owned by a required command signer")
                if (input.value != output.value) throw IllegalArgumentException("Failed requirement: the value of the input and out put should be equal")
            }
        }
    }

    @Serializable
    public class MoveBidirectional : ZKTransactionMetadataCommandData {
        override val transactionMetadata: ResolvedZKTransactionMetadata by transactionMetadata {
            network {
                attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
            }
            commands {
                +MoveBidirectional::class
            }
        }

        @Transient
        override val metadata: ResolvedZKCommandMetadata = commandMetadata {
            circuit {
                buildFolder =
                    File("${System.getProperty("user.dir")}/../zinc-platform-sources/build/circuits/move_bidirectional")
            }
            privateInputs {
                0 private TestState::class
                1 private TestState::class
            }
            privateOutputs {
                0 private TestState::class
                1 private TestState::class
            }
            numberOfSigners = 2
            attachmentConstraintType = AlwaysAcceptAttachmentConstraint::class
        }

        init {
            CommandDataSerializerMap.register(this::class)
        }

        public companion object {
            public fun verifyMoveBidirectional(
                tx: LedgerTransaction,
                command: CommandWithParties<CommandData>
            ) {
                // Transaction structure
                tx.zkFLowMetadata.verify(tx)

                // Transaction contents
                if (tx.inputsOfType<TestState>().sumBy { it.value } != tx.outputsOfType<TestState>().sumBy { it.value }) throw IllegalArgumentException(
                    "Failed requirement: amounts are not conserved for TestState"
                )

                tx.inputStates.forEachIndexed { index, input ->
                    val output = tx.getOutput(index) as ZKOwnableState
                    input as ZKOwnableState

                    if (input.owner.owningKey == output.owner.owningKey) throw IllegalArgumentException("Failed requirement: input state $index changes ownership")
                    if ((tx.outputStates.reversed()[index] as ZKOwnableState).owner.owningKey != input.owner.owningKey) throw IllegalArgumentException(
                        "Failed requirement: ownership of input $index should swap ownership"
                    )

                    if (input.owner.owningKey !in command.signers) throw IllegalArgumentException("Failed requirement: input state $index is owned by a required command signer")
                }
            }
        }
    }

    override fun verify(tx: LedgerTransaction) {
        // The transaction may have only one command, of a type defined above
        val command = tx.commands.first()

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
