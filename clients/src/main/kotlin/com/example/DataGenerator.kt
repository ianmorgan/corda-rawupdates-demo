package com.example

import com.example.flows.CreateFoo
import com.example.flows.FindFoo
import com.example.flows.FindFooAcrossNetwork
import com.example.states.Action
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import java.io.File
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
        require(args.size == 1) { "Usage: DataGenerator <yaml-file-name> | <uuid of FooState> " }

        val connection = loadConnection()

        if (isUUID(args[0])) {
            viewStatesInVault(connection, UUID.fromString(args[0]))
        } else {
            runDataGenerator(connection, args[0])
        }

        // need to force the exit - something unknown is holding onto resources
        System.exit(0)
    }

    private fun loadConnection(): Connection {
        println("Reading config from 'configs/connection.yaml'")
        val yaml = FileInputStream("configs/connection.yaml").bufferedReader().use { it.readText() }
        val connection = mapper
                .readValue(yaml, Connection::class.java)

        println("CordaRPC connection to ${connection.nodeAddress}")
        return connection
    }

    private fun runDataGenerator(connection: Connection, yamlFile: String) {
        val f = File("generated-data.txt")
        println("Reading config from $yamlFile")
        val yaml = FileInputStream(yamlFile).bufferedReader().use { it.readText() }
        val config = mapper
                .readValue(yaml, Config::class.java)

        val cordaConfig = CordaRPCClientConfiguration(minimumServerProtocolVersion = 4)
        val client = CordaRPCClient(NetworkHostAndPort.parse(connection.nodeAddress), cordaConfig)
        val proxy = client.start(connection.rpcUsername, connection.rpcPassword).proxy

        val nodes = proxy.networkMapSnapshot().map { it.legalIdentities.first().name }
        println("nodes are:\n${nodes.joinToString(separator = "\n", transform = { "  $it" })}")

        println("Connected, proxy is ${proxy}")
        val executors = Executors.newFixedThreadPool(config.threadCount)

        val futures = ArrayList<Future<*>>()

        for (i in 1..config.threadCount) {
            futures.add(executors.submit { doLoadTest(i, connection, config, proxy, f) })
            // random delay to avoid flooding server
            Thread.sleep(Random().nextInt(1000).toLong())
        }

        println("waiting for all futures to complete")
        futures.forEach { it.get() }
        println("all futures done")

        // wait for them all to finish
        val stillRunning = executors.shutdownNow()
        println("all done, ${stillRunning.size} threads had not finished")
    }

    private fun viewStatesInVault(connection: Connection, id: UUID) {
        val cordaConfig = CordaRPCClientConfiguration(minimumServerProtocolVersion = 4)
        val client = CordaRPCClient(NetworkHostAndPort.parse(connection.nodeAddress), cordaConfig)
        val proxy = client.start(connection.rpcUsername, connection.rpcPassword).proxy

        val other = proxy.partiesFromName(connection.otherParty, false).single()
        val handler = proxy.startFlowDynamic(FindFooAcrossNetwork::class.java,
                UniqueIdentifier(id = id), other)


        val result = handler.returnValue.get(30, TimeUnit.SECONDS)

        result.forEach { (k, v) ->
            println("Data for node $k:")
            println("In vault")
            v.inVault.forEach { println("  $it") }
            println("rawUpdates")
            v.rawUpdates.forEach { println("  $it") }
            println("")
        }
    }

    private fun doLoadTest(threadNumber: Int, connection: Connection, config: Config, proxy: CordaRPCOps, f : File) {
        println("Thread $threadNumber - Started")
        val partyLookup = HashMap<String, Party>()
        var counter = 0
        try {
            val me = proxy.nodeInfo().legalIdentities.first().name

            while (counter < config.fooCount) {

                for (party in listOf(connection.otherParty)) {

                    if (!partyLookup.containsKey(party)) {
                        partyLookup.put(party, proxy.partiesFromName(party, false).single())
                    }

                    val otherParty = partyLookup[party]!!

                    val msg = "Thread $threadNumber - Foo #${++counter} from ${me.organisation} to ${otherParty.nameOrNull().organisation}"

                    val id = UniqueIdentifier()
                    f.appendText("${id.id},Sending\n")
                    val handler = proxy.startFlowDynamic(CreateFoo::class.java,
                            id,
                            msg,
                            otherParty,
                            config.action.name)

                    val result = handler.returnValue.get(10, TimeUnit.SECONDS)
                    f.appendText("${id.id},Sent,${handler.id.uuid},${result.txBits.hash}\n")
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


    private fun isUUID(str: String): Boolean {
        try {
            UUID.fromString(str)
            return true
        } catch (ex: Exception) {
            return false
        }

    }
}


data class Config(
        val fooCount: Int = 100,
        val threadCount: Int = 1,
        val action: Action = Action.Nothing
)


data class Connection(
        val nodeAddress: String,
        val rpcUsername: String,
        val rpcPassword: String,
        val otherParty: String = "Bob"
)