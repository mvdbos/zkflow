package com.ing.zknotary.common.transactions

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.transactions.TransactionWithSignatures
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

data class SignedZKProverTransaction(val tx: ZKProverTransaction, override val sigs: List<TransactionSignature>) : TransactionWithSignatures, NamedByZKMerkleTree {

    override val merkleTree: TransactionMerkleTree
        get() = tx.merkleTree

    override val id: SecureHash
        get() = tx.id

    override val requiredSigningKeys: Set<PublicKey>
        get() = tx.command.signers.toSet()

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
        return keys.map { it.toBase58String() }
    }

    operator fun plus(sig: TransactionSignature) = copy(sigs = sigs + listOf(sig))

    operator fun plus(sigList: Collection<TransactionSignature>) = copy(sigs = sigs + sigList)
}
