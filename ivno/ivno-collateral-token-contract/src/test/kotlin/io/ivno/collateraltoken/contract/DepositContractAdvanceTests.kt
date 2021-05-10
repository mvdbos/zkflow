package io.ivno.collateraltoken.contract

import io.dasl.contracts.v1.token.TokenContract
import io.onixlabs.corda.bnms.contract.Network
import io.onixlabs.corda.identityframework.contract.AttestationStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.ledger
import org.junit.jupiter.api.Test
import java.time.Instant

class DepositContractAdvanceTests : ContractTest() {

    @Test
    fun `On deposit advancing, the transaction must include the Advance command`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                fails()
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                verifies()
            }
        }
    }

    @Test
    fun `On deposit advancing, only one deposit state must be consumed`() {
        services.ledger {
            transaction {
                input(DepositContract.ID, DEPOSIT)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_DEPOSIT_INPUTS)
            }
        }
    }

    @Test
    fun `On deposit advancing, only one deposit state must be created`() {
        services.ledger {
            transaction {
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_DEPOSIT_OUTPUTS)
            }
        }
    }

    @Test
    fun `On deposit advancing, only one token state must be created when the advance status is PAYMENT_ACCEPTED`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                command(keysOf(CUSTODIAN, TOKEN_ISSUING_ENTITY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_TOKEN_OUTPUTS)
            }
        }
    }

    @Test
    fun `On deposit advancing, only one Ivno token type must be referenced`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                output(TokenContract.CONTRACT_ID, TOKEN_100GBP_BANK_A)
                command(keysOf(CUSTODIAN, TOKEN_ISSUING_ENTITY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_TOKEN_TYPE_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership state must be referenced for each deposit participant (BANK_A missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership state must be referenced for each deposit participant (CUSTODIAN missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership state must be referenced for each deposit participant (TOKEN_ISSUING_ENTITY missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership attestation state must be referenced for each deposit participant (BANK_A missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership attestation state must be referenced for each deposit participant (CUSTODIAN missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, a membership attestation state must be referenced for each deposit participant (TOKEN_ISSUING_ENTITY missing)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership attestation status must be ACCEPTED`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships(status = AttestationStatus.REJECTED)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_STATUS)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership's network must be equal to the Ivno token type network (BANK_A invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (invalidMembership, _) = createMembership(BANK_A.party, network = network)
                reference(invalidMembership.ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership's network must be equal to the Ivno token type network (CUSTODIAN invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (invalidMembership, _) = createMembership(CUSTODIAN.party, network = network)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(invalidMembership.ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership's network must be equal to the Ivno token type network (TOKEN_ISSING_ENTITY invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (invalidMembership, _) = createMembership(TOKEN_ISSUING_ENTITY.party, network = network)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(invalidMembership.ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership attestation's network must be equal to the Ivno token type network (BANK_A invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (_, invalidAttestation) = createMembership(BANK_A.party, network = network)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(invalidAttestation.ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership attestation's network must be equal to the Ivno token type network (CUSTODIAN invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (_, invalidAttestation) = createMembership(CUSTODIAN.party, network = network)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(invalidAttestation.ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership attestation's network must be equal to the Ivno token type network (TOKEN_ISSUING_ENTITY invalid)`() {
        services.ledger {
            transaction {
                val network = Network("INVALID_NETWORK", BNO.party)
                val memberships = createAllMemberships()
                val (_, invalidAttestation) = createMembership(TOKEN_ISSUING_ENTITY.party, network = network)
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(invalidAttestation.ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATION_NETWORK)
            }
        }
    }

    @Test
    fun `On deposit advancing, every membership attestation state must point to a referenced membership state`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                val (bankMembership, bankAttestation) = createMembership(BANK_A.party, evolveMembership = true)
                reference(bankMembership.ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(bankAttestation.ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_MEMBERSHIP_ATTESTATIONS_POINT_TO_MEMBERSHIP_REFERENCES)
            }
        }
    }

    @Test
    fun `On deposit advancing, the depositor, custodian, amount and linearId are not allowed to change`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit().copy(linearId = UniqueIdentifier()))
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_CHANGES)
            }
        }
    }

    @Test
    fun `On deposit advancing, the output state must be able to advance from the input state`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.cancelDeposit())
                output(DepositContract.ID, DEPOSIT.acceptDeposit())
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_CAN_ADVANCE)
            }
        }
    }

    @Test
    fun `On deposit advancing, the reference must only change if the advanced status is DEPOSIT_ACCEPTED`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.rejectDeposit().copy(reference = "NOT NULL"))
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_REFERENCE)
            }
        }
    }

    @Test
    fun `On deposit advancing, the reference must only change if the advanced status is DEPOSIT_ACCEPTED, and must not be null`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit().copy(reference = null))
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_REFERENCE)
            }
        }
    }

    @Test
    fun `On deposit advancing, the created timestamp must be after the consumed timestamp`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit().copy(timestamp = Instant.MIN))
                command(keysOf(CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_TIMESTAMP)
            }
        }
    }

    @Test
    fun `On deposit advancing, the referenced token type and the deposit token issuing entity must be equal`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE.copy(tokenIssuingEntity = RANDOM_PARTY.party))
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                output(TokenContract.CONTRACT_ID, TOKEN_100GBP_BANK_A)
                command(keysOf(TOKEN_ISSUING_ENTITY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_TOKEN_ISSUING_ENTITY)
            }
        }
    }

    @Test
    fun `On deposit advancing, the token amount must be of equal value to the transfer amount`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                output(TokenContract.CONTRACT_ID, TOKEN_50GBP_BANK_A)
                command(keysOf(TOKEN_ISSUING_ENTITY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_TOKEN_AMOUNT)
            }
        }
    }

    @Test
    fun `On deposit advancing, the required signing participants must sign the transaction (depositor must sign when issuing payment)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF"))
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                command(keysOf(RANDOM_PARTY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_SIGNERS)
            }
        }
    }

    @Test
    fun `On deposit advancing, the required signing participants must sign the transaction (custodian must sign when accepting a deposit)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT)
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF"))
                command(keysOf(RANDOM_PARTY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_SIGNERS)
            }
        }
    }

    @Test
    fun `On deposit advancing, the required signing participants must sign the transaction (custodian must sign when accepting a payment)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                output(TokenContract.CONTRACT_ID, TOKEN_100GBP_BANK_A)
                command(keysOf(RANDOM_PARTY, TOKEN_ISSUING_ENTITY), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_SIGNERS)
            }
        }
    }

    @Test
    fun `On deposit advancing, the required signing participants must sign the transaction (token issuing entity must sign when accepting a payment)`() {
        services.ledger {
            transaction {
                val memberships = createAllMemberships()
                reference(memberships.membershipFor(BANK_A).ref)
                reference(memberships.membershipFor(CUSTODIAN).ref)
                reference(memberships.membershipFor(TOKEN_ISSUING_ENTITY).ref)
                reference(memberships.attestationFor(BANK_A).ref)
                reference(memberships.attestationFor(CUSTODIAN).ref)
                reference(memberships.attestationFor(TOKEN_ISSUING_ENTITY).ref)
                reference(IvnoTokenTypeContract.ID, IVNO_TOKEN_TYPE)
                input(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment())
                output(DepositContract.ID, DEPOSIT.acceptDeposit("REF").issuePayment().acceptPayment())
                output(TokenContract.CONTRACT_ID, TOKEN_100GBP_BANK_A)
                command(keysOf(RANDOM_PARTY, CUSTODIAN), DepositContract.Advance)
                failsWith(DepositContract.Advance.CONTRACT_RULE_SIGNERS)
            }
        }
    }
}
