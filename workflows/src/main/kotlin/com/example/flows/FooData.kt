package com.example.flows

import com.example.states.FooState
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class FooData(val inVault: List<FooState>, val rawUpdates: List<FooState>)