package com.example.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class FindFooAcrossNetwork(val id: UniqueIdentifier,
                           val others: List<Party>) : FlowLogic<Map<String, FooData>>() {
    constructor(id: UniqueIdentifier, other: Party) : this(id, listOf(other))

    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating Transaction")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): Map<String, FooData> {
        val results = LinkedHashMap<String, FooData>()

        val me = serviceHub.myInfo.legalIdentities.first()
        val mine = subFlow(FindFoo(id))
        results[me.name.organisation] = mine

        for (other in others) {
            val partyBSession = initiateFlow(other)
            val otherPartyFoos: FooData =
                    partyBSession.sendAndReceive<FooData>(id).unwrap { data -> data }

            results[other.name.organisation] = otherPartyFoos
        }

        return results

    }
}


@InitiatedBy(FindFooAcrossNetwork::class)
class FindFooAcrossNetworkResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        val id: UniqueIdentifier = counterpartySession.receive<UniqueIdentifier>().unwrap { data -> data }
        val foos = subFlow(FindFoo(id = id))
        counterpartySession.send(foos)
    }
}
