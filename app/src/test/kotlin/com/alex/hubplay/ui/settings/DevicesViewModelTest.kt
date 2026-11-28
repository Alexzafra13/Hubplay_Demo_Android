package com.alex.hubplay.ui.settings

import com.alex.hubplay.data.api.dto.MySessionDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Dispositivos conectados: esta tele primero, luego el más reciente. */
class DevicesViewModelTest {

    @Test
    fun `this TV goes first, then most recently active, and fields map from the wire`() {
        val rows = listOf(
            MySessionDto(id = "old", deviceName = "Móvil", lastActiveAt = "2026-11-20T10:00:00Z", authMethod = "password"),
            MySessionDto(
                id = "tv",
                deviceName = "Mi TV",
                lastActiveAt = "2026-11-01T10:00:00Z",
                current = true,
                authMethod = "device_link",
                ipAddress = "192.168.1.132",
            ),
            MySessionDto(id = "new", deviceName = " ", createdAt = "2026-11-25T10:00:00Z"),
        )

        val devices = DevicesViewModel.sortDevices(rows.map(DevicesViewModel::toDevice))

        assertThat(devices.map { it.id }).containsExactly("tv", "new", "old").inOrder()
        assertThat(devices[0].linked).isTrue()
        assertThat(devices[0].ip).isEqualTo("192.168.1.132")
        // Nombre en blanco → sin nombre (la pantalla pone "Dispositivo sin nombre").
        assertThat(devices[1].name).isNull()
        // Sin last_active_at se usa created_at.
        assertThat(devices[1].lastActive).isNotNull()
        assertThat(devices[2].linked).isFalse()
    }
}
