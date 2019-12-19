package com.example.services

import com.example.states.FooState
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.File

@CordaService
class FooTrackerService(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val fooData = File("foodata.txt")

    private companion object {
        val log = loggerFor<FooTrackerService>()
    }

    init {
        log.info("FooTrackerService is registering")
        trackFoos()
        log.info("FooTrackerService registered, data will be at ${fooData.absolutePath}")
    }


    private fun trackFoos() {
        serviceHub.vaultService.rawUpdates.subscribe {
            it.produced.forEach {
                val data = it.state.data
                if (data is FooState) {
                    fooData.appendText("${data.data}\n")
                    log.info("Here is another Foo!: ${data.data}")
                }
            }
        }
    }
}