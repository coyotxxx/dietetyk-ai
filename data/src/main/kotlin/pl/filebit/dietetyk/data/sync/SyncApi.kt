package pl.filebit.dietetyk.data.sync

/**
 * Furtka WWW (decyzja Macieja: „zostawić furtkę na zarządzanie przez stronę").
 *
 * MVP: local-first, zero backendu — `NoopSyncApi`. Interfejs istnieje OD DNIA 1, żeby architektura
 * była gotowa; w Fazie 5 dojdzie implementacja REST na VPS + panel WWW. Encje mają `updatedAt`+`dirty`
 * pod push/pull; sam mechanizm sync podłączymy później bez zmiany reszty.
 */
interface SyncApi {
    /** Czy sync jest aktywny (MVP: false). */
    val enabled: Boolean

    /** Wypchnij lokalne zmiany (dirty) na serwer. MVP: no-op. */
    suspend fun push()

    /** Pobierz zmiany z serwera. MVP: no-op. */
    suspend fun pull()
}

/** MVP: brak backendu — nic nie synchronizuje. */
object NoopSyncApi : SyncApi {
    override val enabled: Boolean = false
    override suspend fun push() { /* seam — Faza 5 */ }
    override suspend fun pull() { /* seam — Faza 5 */ }
}
