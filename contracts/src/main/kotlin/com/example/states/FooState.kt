package com.example.states

import com.example.contracts.FooContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(FooContract::class)
data class FooState(val data: String, val partyA: Party, val partyB: Party) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(partyA, partyB)

}
