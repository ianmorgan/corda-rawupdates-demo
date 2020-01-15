package com.example.states

import com.example.contracts.FooContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * The condition we want to force. Used to emulate
 * real world error
 */
@CordaSerializable
enum class Action {
    Nothing,
    ThrowHospitalizeFlowException,
    PartyAThrowHospitalizeFlowException,
    PartyBThrowHospitalizeFlowException
}

// *********
// * State *
// *********
@BelongsToContract(FooContract::class)
data class FooState(override val linearId: UniqueIdentifier,
                    val data: String,
                    val partyA: Party,
                    val partyB: Party,
                    val action: Action = Action.Nothing) : LinearState {
    override val participants: List<AbstractParty>
        get() = listOf(partyA, partyB)

}
