package com.example.flows

import co.paralleluniverse.fibers.Suspendable
import com.example.contracts.FooContract
import com.example.states.Action
import com.example.states.FooState
import net.corda.core.context.Actor
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateFoo(val id : UniqueIdentifier,
                    val data: String,
                    val otherParty: Party,
                    val action : String) : FlowLogic<SignedTransaction>() {
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
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val me = serviceHub.myInfo.legalIdentities.first()

        // Stage 1. Generate an unsigned transaction.
        progressTracker.currentStep = GENERATING_TRANSACTION
        val state = FooState(linearId = id,
                data = data,
                action = Action.valueOf(action),
                partyA = me,
                partyB = otherParty)

        val txCommand =
                Command(FooContract.Commands.Create(), state.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(state, FooContract.ID)
                .addCommand(txCommand)

        txBuilder.verify(serviceHub)

        // Stage 2. Sign the transaction.
        progressTracker.currentStep = SIGNING_TRANSACTION
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


        // Step 3. - Get signatures
        progressTracker.currentStep = GATHERING_SIGS
        // Send the state to the counterparty, and receive it back with their signature.
        val partyBSession = initiateFlow(otherParty)
        val fullySignedTx = subFlow(
                CollectSignaturesFlow(
                        partSignedTx,
                        setOf(partyBSession), Companion
                        .GATHERING_SIGS.childProgressTracker()
                )
        )


        // Stage 4. Notarise and record the transaction in the issuers vault
        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(
                FinalityFlow(
                        fullySignedTx,
                        sessions = setOf(partyBSession),
                        progressTracker = FINALISING_TRANSACTION.childProgressTracker()
                )
        )
    }
}

@InitiatedBy(CreateFoo::class)
class CreateFooResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a FooState!" using (output is FooState)
            }
        }
        val txId = subFlow(signTransactionFlow).id
        return subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = txId))
    }
}
