package com.example

import com.example.flows.CreateFooFlow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

        val executors = Executors.newFixedThreadPool(config.threadCount)

        val futures = ArrayList<Future<*>>()

        for (i in 1..config.threadCount) {
            futures.add(executors.submit { doLoadTest(i, config, proxy) })
            // random delay to avoid flooding server
            Thread.sleep(Random().nextInt(1000).toLong())
        }

        // wait for them all to finish
        executors.shutdown()
    }

    private fun doLoadTest(threadNumber: Int, config: Config, proxy: CordaRPCOps) {
        println("Thread $threadNumber - Started")
        val partyLookup = HashMap<String, Party>()
        var counter = 0
        try {
            val me = proxy.nodeInfo().legalIdentities.first().name

            while (counter < config.fooCount) {

                for (party in config.otherParties) {

                    if (!partyLookup.containsKey(party)) {
                        partyLookup.put(party, proxy.partiesFromName(party, false).single())
                    }

                    val otherParty = partyLookup[party]!!

                    val msg = "Thread $threadNumber - Foo #${++counter} from ${me.organisation} to ${otherParty.nameOrNull().organisation}"

                    val handler = proxy.startFlowDynamic(CreateFooFlow::class.java, msg, otherParty)
                    val result = handler.returnValue.get(30, TimeUnit.SECONDS)
                    //println(result)

                    if (counter % 10 == 0) {
                        println("Thread $threadNumber - has now sent $counter Foos to $party")
                    }
                }
            }

            println("Thread $threadNumber - Finished ")
        } catch (ex: Exception) {
            logger.error("Thread $threadNumber - Failed sending Foos, thread: $threadNumber, iteration:$counter", ex)
        }

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