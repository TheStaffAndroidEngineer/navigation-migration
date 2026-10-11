package staff.android.navigation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.cartDataStore by preferencesDataStore("navigation-cart")
interface CartRepository {
    val records: Flow<Map<String, AcceptedQuantity>>
    suspend fun confirm(id: String, quantity: Int, revision: Long)
    suspend fun reset()
}
class RevisionMismatch : IllegalStateException("Accepted revision changed")
class DataStoreCartRepository(private val context: Context) : CartRepository {
    private fun quantityKey(id: String) = intPreferencesKey("$id.quantity")
    private fun revisionKey(id: String) = longPreferencesKey("$id.revision")
    override val records = context.cartDataStore.data.map { values ->
        Products.all.associate { product ->
            product.id to AcceptedQuantity(
                values[quantityKey(product.id)] ?: 2,
                values[revisionKey(product.id)] ?: 0,
            )
        }
    }
    override suspend fun confirm(id: String, quantity: Int, revision: Long) {
        require(Products.find(id) != null && quantity in 0..5)
        context.cartDataStore.edit { values ->
            val current = values[revisionKey(id)] ?: 0
            if (current != revision) throw RevisionMismatch()
            if ((values[quantityKey(id)] ?: 2) != quantity) {
                values[quantityKey(id)] = quantity
                values[revisionKey(id)] = current + 1
            }
        }
    }
    override suspend fun reset() { context.cartDataStore.edit { it.clear() } }
}
