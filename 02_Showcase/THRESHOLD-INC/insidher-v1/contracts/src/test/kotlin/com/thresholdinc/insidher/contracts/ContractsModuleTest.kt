package com.thresholdinc.insidher.contracts

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Contracts module setup")
class ContractsModuleTest {

    @Test
    @DisplayName("ContractsModule singleton is accessible")
    fun `contracts module is accessible`() {
        assertNotNull(ContractsModule)
    }
}
