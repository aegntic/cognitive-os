package com.thresholdinc.insidher.core

import com.thresholdinc.insidher.contracts.ContractsModule
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Core module setup")
class CoreModuleTest {

    @Test
    @DisplayName("CoreModule singleton is accessible")
    fun `core module is accessible`() {
        assertNotNull(CoreModule)
    }

    @Test
    @DisplayName(":core can access :contracts module")
    fun `core depends on contracts`() {
        assertNotNull(ContractsModule)
    }
}
