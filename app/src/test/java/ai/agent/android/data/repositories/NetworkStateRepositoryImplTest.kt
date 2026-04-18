package ai.agent.android.data.repositories

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ai.agent.android.domain.models.NetworkState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkStateRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()

    @Before
    fun setup() {
        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers { self as NetworkRequest.Builder }
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk()

        connectivityManager = mockk(relaxed = true)
        context = mockk()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit
        every { connectivityManager.activeNetwork } returns null
    }

    @After
    fun teardown() {
        unmockkConstructor(NetworkRequest.Builder::class)
    }

    @Test
    fun `given no active network when repository initialized then networkState is disconnected`() {
        every { connectivityManager.activeNetwork } returns null

        val repo = NetworkStateRepositoryImpl(context)

        assertEquals(NetworkState(isConnected = false, isWifiConnected = false), repo.networkState.value)
    }

    @Test
    fun `given wifi network when onAvailable fires then networkState is connected and wifi`() {
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        val repo = NetworkStateRepositoryImpl(context)
        callbackSlot.captured.onAvailable(mockNetwork)

        assertEquals(NetworkState(isConnected = true, isWifiConnected = true), repo.networkState.value)
    }

    @Test
    fun `given connected network when onLost fires then networkState becomes disconnected`() {
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns mockNetwork
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false

        val repo = NetworkStateRepositoryImpl(context)
        callbackSlot.captured.onAvailable(mockNetwork)
        assertEquals(NetworkState(isConnected = true, isWifiConnected = false), repo.networkState.value)

        every { connectivityManager.activeNetwork } returns null
        callbackSlot.captured.onLost(mockNetwork)

        assertEquals(NetworkState(isConnected = false, isWifiConnected = false), repo.networkState.value)
    }
}
