package com.example.services

import com.example.states.Action
import com.example.states.FooState
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.File
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashSet

@CordaService
class FooTrackerService(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    private val fooData = File("foo-data.txt")
    private val fooErrors = File("foo-errors.txt")
    private val triggered = File("triggered.txt")
    private val seen = File("seen.txt")
    private val alreadySeen = HashSet<UUID>()


    private companion object {
        val log = loggerFor<FooTrackerService>()
    }

    init {
        log.info("FooTrackerService is registering")
        loadSeen()
        trackFoos()
        log.info("FooTrackerService registered, data will be at ${fooData.absolutePath}")
    }


    private fun trackFoos() {
        serviceHub.vaultService.rawUpdates.subscribe {
            val me = serviceHub.myInfo.legalIdentities.single()
            log.info("rawUpdate observer triggered for flow ${it.flowId} for $me")
            triggered.appendText("${it.flowId} triggered at ${System.currentTimeMillis()}\n")

            it.produced.forEach { produced ->
                val data = produced.state.data
                if (data is FooState) {

                    // test for special conditions
                    when (data.action) {
                        Action.ThrowHospitalizeFlowException -> {
                            if (!alreadySeen(it.flowId!!)) {
                                markAsSeen(it.flowId!!)
                                fooErrors.appendText("${it.flowId},${data.linearId},${System.currentTimeMillis()},ThrowHospitalizeFlowException\n")
                                throw HospitalizeFlowException("Triggered for ${data.linearId} on flow ${it.flowId}")
                            }
                        }
                        Action.PartyAThrowHospitalizeFlowException -> {
                            if (data.partyA == me) {
                                if (!alreadySeen(it.flowId!!)) {
                                    markAsSeen(it.flowId!!)
                                    fooErrors.appendText("${it.flowId},${data.linearId},${System.currentTimeMillis()},ThrowHospitalizeFlowException\n")
                                    throw HospitalizeFlowException("Triggered for ${data.linearId} on flow ${it.flowId}")
                                }
                            }
                        }
                        Action.PartyBThrowHospitalizeFlowException -> {
                            if (data.partyB == me) {
                                if (!alreadySeen(it.flowId!!)) {
                                    markAsSeen(it.flowId!!)
                                    fooErrors.appendText("${it.flowId},${data.linearId},${System.currentTimeMillis()},ThrowHospitalizeFlowException\n")
                                    throw HospitalizeFlowException("Triggered for ${data.linearId} on flow ${it.flowId}")
                                }
                            }
                        }
                    }
                    fooData.appendText("${it.flowId},${fooToCSV(data)},${it.type}\n")
                }
            }
        }
    }

    private fun alreadySeen(flowId: UUID): Boolean {
        return alreadySeen.contains(flowId)
    }

    private fun markAsSeen(flowId: UUID) {
        alreadySeen.add(flowId)
        seen.appendText("${flowId}\n")
    }

    private fun loadSeen() {
        if (seen.exists()) {
            seen.readLines().forEach { alreadySeen.add(UUID.fromString(it)) }
            log.info("Loaded ${alreadySeen.size} from ${seen.name}")
        }
    }

    private fun fooToCSV(foo: FooState): String {
        val sb = StringBuilder()
        sb.append(foo.linearId)
        sb.append(",")
        sb.append(foo.data)
        sb.append(",")
        sb.append(foo.partyA.name.organisation)
        sb.append(",")
        sb.append(foo.partyB.name.organisation)
        sb.append(",")
        sb.append(System.currentTimeMillis())
        return sb.toString()

    }
}