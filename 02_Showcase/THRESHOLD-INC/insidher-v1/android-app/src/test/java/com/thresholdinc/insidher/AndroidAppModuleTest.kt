package com.thresholdinc.insidher

import com.thresholdinc.insidher.contracts.ContractsModule
import com.thresholdinc.insidher.core.CoreModule
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Android app module setup")
class AndroidAppModuleTest {

    @Test
    @DisplayName("AndroidAppModule singleton is accessible")
    fun `android app module is accessible`() {
        assertNotNull(AndroidAppModule)
    }

    @Test
    @DisplayName(":android-app can access :core module")
    fun `android app depends on core`() {
        assertNotNull(CoreModule)
    }

    @Test
    @DisplayName(":android-app can access :contracts module")
    fun `android app depends on contracts`() {
        assertNotNull(ContractsModule)
    }
}
