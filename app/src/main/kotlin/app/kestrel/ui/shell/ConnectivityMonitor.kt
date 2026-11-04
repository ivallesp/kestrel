package app.kestrel.ui.shell

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.kestrel.ui.model.Connectivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone can reach the internet, for the offline banner and the "Phone offline" status. Offline carries
 * the moment the connection went ([nowUtcMillis] then, true time from the container's clock), for
 * "Offline since 18:02".
 */
object ConnectivityMonitor {
    fun observe(
        context: Context,
        nowUtcMillis: () -> Long,
    ): Flow<Connectivity> =
        callbackFlow {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            if (manager == null) {
                trySend(Connectivity.Online)
                awaitClose()
                return@callbackFlow
            }
            var offlineSince: Long? = null

            fun report(online: Boolean) {
                if (online) {
                    offlineSince = null
                    trySend(Connectivity.Online)
                } else {
                    val since = offlineSince ?: nowUtcMillis().also { offlineSince = it }
                    trySend(Connectivity.Offline(since))
                }
            }
            report(manager.getNetworkCapabilities(manager.activeNetwork).reachesTheInternet())
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = report(capabilities.reachesTheInternet())

                    override fun onLost(network: Network) = report(false)
                }
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    private fun NetworkCapabilities?.reachesTheInternet(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
