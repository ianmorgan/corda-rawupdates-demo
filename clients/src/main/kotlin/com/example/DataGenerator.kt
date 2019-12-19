package com.example

import com.example.flows.CreateFooFlow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Connects to a Corda node via RPC and performs RPC operations on the node.
 *
 * The RPC connection is configured using command line arguments.
 */
fun main(args: Array<String>) = DataGenerator().main(args)

private class DataGenerator {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())

    init {
        mapper.registerModule(KotlinModule())
    }


    companion object {
        val logger = loggerFor<DataGenerator>()
    }

    fun main(args: Array<String>) {
        // Create an RPC connection to the node.
        require(args.size == 1) { "Usage: DataGenerator <yaml-file-name>" }

        println("Reading config from ${args[0]}")
        val yaml = FileInputStream(args[0]).bufferedReader().use { it.readText() }
        val config = mapper
                .readValue(yaml, Config::class.java)

        println("CordaRPC connection to ${config.nodeAddress}")

        val client = CordaRPCClient(NetworkHostAndPort.parse(config.nodeAddress))
        val proxy = client.start(config.rpcUsername, config.rpcPassword).proxy

        val nodes = proxy.networkMapSnapshot()
        println("nodes are $nodes")

        println("Connected, proxy is ${proxy}")
        val me = proxy.nodeInfo().legalIdentities.first().name

        for (party in config.otherParties) {
            println("Sending Foos to $party")

            var counter = 1
            while (counter <= config.fooCount) {
                val otherParty = proxy.partiesFromName(party, false).single()

                val msg = "Foo #${counter++} from ${me.organisation} to ${otherParty.nameOrNull().organisation}"

                val handler = proxy.startFlowDynamic(CreateFooFlow::class.java, msg, otherParty)
                val result = handler.returnValue.get(30, TimeUnit.SECONDS)

                println(result)
            }
        }


//        proxy.stateMachinesSnapshot().forEach {
//            println(it)
//        }


//        proxy.rawUpdates.subscribe {
//            MessageRepository.log.info("something to subscribe to")
//            it.produced.forEach {
//                val data = it.state.data
//                if (data is FooState){
//                    MessageRepository.log.info(data.data)
//                }
//            }
//        }
    }
}


data class Config(
        val nodeAddress: String,
        val rpcUsername: String,
        val rpcPassword: String,
        val otherParties: List<String> = listOf("Bob"),
        val fooCount: Int = 100,
        val threadCount: Int = 1
)