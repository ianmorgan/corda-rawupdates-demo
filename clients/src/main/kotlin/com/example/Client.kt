package com.example

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.loggerFor

/**
 * Connects to a Corda node via RPC and performs RPC operations on the node.
 *
 * The RPC connection is configured using command line arguments.
 */
fun main(args: Array<String>) = Client().main(args)

private class Client {
    companion object {
        val logger = loggerFor<Client>()
    }

    fun main(args: Array<String>) {
        // Create an RPC connection to the node.
        require(args.size == 3) { "Usage: Client <node address> <rpc username> <rpc password>" }
        val nodeAddress = parse(args[0])
        val rpcUsername = args[1]
        val rpcPassword = args[2]
        val config = CordaRPCClientConfiguration(minimumServerProtocolVersion = 4)
        val client = CordaRPCClient(nodeAddress, config)
        val proxy = client.start(rpcUsername, rpcPassword).proxy

        // Interact with the node.
        // For example, here we print the nodes on the network.
        val nodes = proxy.networkMapSnapshot()
        logger.info("{}", nodes)


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