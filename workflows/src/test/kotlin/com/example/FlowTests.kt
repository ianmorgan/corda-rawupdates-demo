package com.example

import com.example.flows.CreateFoo
import com.example.states.Action
import com.example.states.FooState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.example.contracts"),
            TestCordapp.findCordapp("com.example.flows")),
            networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4)
    ))
    private val a = network.createNode()
    private val b = network.createNode()
    private val partyA = a.info.legalIdentities.first()
    private val partyB = b.info.legalIdentities.first()


    init {
        listOf(a, b).forEach {
            //     it.registerInitiatedFlow(Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `create a FooState`() {
        // create flow
        val createFlow = CreateFoo(UniqueIdentifier(), "some data", partyB, Action.Nothing.toString())

        // run the flow
        val future = a.startFlow(createFlow).toCompletableFuture()
        network.runNetwork()

        // assert results
        val stx = future.getOrThrow()
        assertEquals(stx.tx.outputs.size, 1)
        val output = stx.tx.outputStates[0] as FooState
        assertEquals(output.data, "some data")

    }
}