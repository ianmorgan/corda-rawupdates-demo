package com.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.states.FooState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker
import java.io.File
import java.util.*


// *********
// * Flows *
// *********
@StartableByRPC
class FindFoo(val id: UniqueIdentifier) : FlowLogic<FooData>() {
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
    override fun call(): FooData {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id),
                status = Vault.StateStatus.ALL)
        val items = serviceHub.vaultService.queryBy(contractStateType = FooState::class.java,
                criteria = queryCriteria).states

        val vault = items.sortedBy { it.ref.index }.map { it.state.data }
        return FooData(inVault = vault, rawUpdates = findInRawUpdate(id.id))
    }


    private fun findInRawUpdate(id: UUID): List<FooState> {
        val partyLookup = HashMap<String, Party>()

        // pull data out of the CSV string
        val deserialize: (String) -> FooState = { data ->
            logger.info(data)
            val parts = data.split(",")
            val orgA = parts[3].trim()
            val orgB = parts[4].trim()
            if (!partyLookup.containsKey(orgA)) {
                val party = serviceHub.networkMapCache.allNodes.single {
                    it.legalIdentities.first().name.organisation == orgA
                }.legalIdentities.first()
                partyLookup[orgA] = party
            }
            if (!partyLookup.containsKey(orgB)) {
                val party = serviceHub.networkMapCache.allNodes.single {
                    it.legalIdentities.first().name.organisation == orgB
                }.legalIdentities.first()
                partyLookup[orgB] = party
            }

            val state = FooState(linearId = UniqueIdentifier.fromString(parts[1].trim()),
                    partyA = partyLookup[orgA]!!,
                    partyB = partyLookup[orgB]!!,
                    data = parts[2].trim())

            state

        }
        val fooData = File("foo-data.txt")
        return if (fooData.exists()) {
            fooData.readLines().map { deserialize(it) }.filter { it.linearId.id == id }
        } else {
            emptyList()
        }
    }


}
