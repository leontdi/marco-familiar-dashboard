package com.lito.planify.dashboard

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.AuthRequest
import com.lito.planify.dashboard.data.Chores
import com.lito.planify.dashboard.data.Content
import com.lito.planify.dashboard.data.Energy
import com.lito.planify.dashboard.data.Forecast
import com.lito.planify.dashboard.data.GeoResult
import com.lito.planify.dashboard.data.HttpError
import com.lito.planify.dashboard.data.Location
import com.lito.planify.dashboard.data.ReferenceClock
import com.lito.planify.dashboard.data.Schedule
import com.lito.planify.dashboard.data.Store
import com.lito.planify.dashboard.data.Week
import com.lito.planify.dashboard.data.cache.DiskCache
import com.lito.planify.dashboard.data.ics.IcsConfig
import com.lito.planify.dashboard.data.ics.IcsSource
import com.lito.planify.dashboard.data.isUnauthorized
import com.lito.planify.dashboard.data.repository.CalendarRepository
import com.lito.planify.dashboard.data.repository.ChoresRepository
import com.lito.planify.dashboard.data.repository.ContentRepository
import com.lito.planify.dashboard.data.repository.EnergyRepository
import com.lito.planify.dashboard.data.repository.ShoppingRepository
import com.lito.planify.dashboard.data.ShoppingList
import com.lito.planify.dashboard.data.ShoppingRemoveRequest
import com.lito.planify.dashboard.data.repository.WeatherRepository
import com.lito.planify.dashboard.power.ScreenSchedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.File
import java.time.LocalDateTime

data class UiState(
    val token: String? = null,
    val email: String? = null,
    /** Calendarios iCal en lugar de Planify: sin sesión, con turnos, menú del cole y precio de la luz. */
    val ics: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    /** false mientras el reloj no se haya sincronizado (el RTC vuelve a la fecha de fábrica). */
    val clockValid: Boolean = false,
    val content: Content? = null,
    val forecast: Forecast? = null,
    /** Precio de la luz de hoy y mañana (solo con calendarios iCal). */
    val energy: Energy? = null,
    /** Listas de la familia (compra de Bring! y tareas de Todoist); null si no están configuradas. */
    val shopping: ShoppingList? = null,
    /** Tareas de los niños (TaskMate); null si no están configuradas. */
    val chores: Chores? = null,
    /** Horario en que se puede cargar el coche: «Mejores horas» solo recomienda ahí; null, a cualquier hora. */
    val carCharge: com.lito.planify.dashboard.data.ChargeHours? = null,
    /** Tarea (o hecha, al deshacer) que se está enviando: su botón espera y no se puede volver a pulsar. */
    val choreBusy: String? = null,
    /** Por qué falló lo último que se envió de tareas o recompensas («PIN incorrecto»…); null si fue bien. */
    val choreError: String? = null,
    val location: Location? = null,
    val schedule: Schedule = Schedule(),
    val brightness: Float? = null,
    /** false si el último intento de sincronizar falló: se muestra lo que hay en caché. */
    val online: Boolean = true,
    /** Hay una red con salida a Internet (sin ella, el login muestra la espera y "Ir a Ajustes"). */
    val connected: Boolean = false,
    /** Sincronización manual en curso (botón de la barra lateral). */
    val syncing: Boolean = false,
    /** Semanas del calendario descargadas bajo demanda, por su lunes. */
    val weeks: Map<LocalDate, Week> = emptyMap(),
    val loginError: String? = null,
    val loggingIn: Boolean = false,
    val geoResults: List<GeoResult> = emptyList(),
    val geoSearching: Boolean = false,
    /** Tocar el reloj nocturno enseña el dashboard hasta esta hora. */
    val peekUntil: LocalDateTime? = null,
    /** Reloj nocturno pedido con la luna de la barra lateral: hasta que se toque o hasta el siguiente encendido. */
    val nightUntil: LocalDateTime? = null
) {
    /** Reloj nocturno: fuera de horario (salvo mientras se mira el dashboard un momento) o pedido con la luna. */
    val night: Boolean
        get() = clockValid && token != null && (
            (nightUntil != null && now.isBefore(nightUntil)) ||
                (ScreenSchedule.isNight(schedule, now) && (peekUntil == null || !now.isBefore(peekUntil)))
            )
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    private val api = Api.client
    private val cache = DiskCache(File(application.filesDir, "cache"))
    /** Calendarios iCal de dashboard.properties: si los hay, sustituyen a Planify y no hace falta iniciar sesión. */
    private val icsConfig = IcsConfig()
    private val ics = if (icsConfig.enabled) IcsSource(icsConfig) else null
    private val contentRepository = ContentRepository(api, cache, ics)
    private val weatherRepository = WeatherRepository(api, cache)
    private val calendarRepository = CalendarRepository(api, cache, ics = ics)
    private val energyRepository = if (ics != null) EnergyRepository(api, cache) else null
    private val shoppingRepository = icsConfig.shoppingUrl?.takeIf { ics != null }?.let { ShoppingRepository(api, cache, it) }
    private val choresRepository = icsConfig.choresUrl?.takeIf { ics != null }?.let { url ->
        icsConfig.choresToken?.let { ChoresRepository(api, cache, url, it) }
    }
    /** Productos (en minúsculas) quitados en el marco que el servidor aún no ha sacado de compra.json. */
    private var hiddenShopping: Set<String> = emptySet()
    /** Habituales (en minúsculas) añadidos en el marco que el servidor aún no trae en compra.json. */
    private var addedShopping: Set<String> = emptySet()
    private val clock = ReferenceClock(BuildConfig.BUILD_TIME, { store.lastValidTime }, { store.lastValidTime = it })

    private val _state = MutableStateFlow(
        clock.current().let { time ->
            UiState(
                token = if (ics != null) ICS_TOKEN else store.token,
                email = if (ics != null) null else store.email,
                ics = ics != null,
                now = time.now,
                clockValid = time.valid,
                content = contentRepository.content.value?.value,
                forecast = weatherRepository.forecast.value?.value,
                energy = energyRepository?.energy?.value?.value,
                shopping = shoppingRepository?.let { it.list.value?.value ?: ShoppingList() },
                chores = choresRepository?.let { it.chores.value?.value ?: Chores() },
                carCharge = icsConfig.carCharge,
                weeks = calendarRepository.weeks.value,
                location = store.location,
                schedule = store.schedule,
                brightness = store.brightness
            )
        }
    )
    /** Lo que se pinta: el estado tal cual o, en modo demostración, con los nombres cambiados. */
    val state: StateFlow<UiState> = icsConfig.demo?.let { demo ->
        _state.map { demo.apply(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, demo.apply(_state.value))
    } ?: _state

    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            setConnected(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        override fun onLost(network: Network) = setConnected(false)
    }

    init {
        // Los repositorios publican la copia en disco y cada descarga buena: la pantalla siempre tiene lo último.
        viewModelScope.launch { contentRepository.content.collect { c -> _state.update { it.copy(content = c?.value) } } }
        viewModelScope.launch { weatherRepository.forecast.collect { f -> _state.update { it.copy(forecast = f?.value) } } }
        viewModelScope.launch { calendarRepository.weeks.collect { w -> _state.update { it.copy(weeks = w) } } }
        energyRepository?.let { repo -> viewModelScope.launch { repo.energy.collect { e -> _state.update { it.copy(energy = e?.value) } } } }
        shoppingRepository?.let { repo ->
            viewModelScope.launch {
                repo.list.collect { l ->
                    val list = l?.value ?: ShoppingList()
                    // Lo quitado en el marco deja de ocultarse cuando la lista del servidor ya no lo trae
                    val names = list.shopping().map { it.producto.lowercase() }.toSet()
                    hiddenShopping = hiddenShopping intersect names
                    addedShopping = addedShopping - names
                    _state.update { it.copy(shopping = list.adjusted(hiddenShopping, addedShopping)) }
                }
            }
        }
        choresRepository?.let { repo -> viewModelScope.launch { repo.chores.collect { c -> _state.update { it.copy(chores = c?.value ?: Chores()) } } } }
        setConnected(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onCleared() {
        connectivity.unregisterNetworkCallback(networkCallback)
    }

    /** Al recuperar la red se sincroniza enseguida, sin esperar al siguiente ciclo. */
    private fun setConnected(connected: Boolean) {
        val was = _state.value.connected
        _state.update { it.copy(connected = connected) }
        if (connected && !was) refreshNow()
    }

    /** Bucle mientras la actividad está visible: reloj cada minuto; contenido y tiempo cuando caducan (5 y 30 min). */
    suspend fun run() {
        while (true) {
            tick()
            if (_state.value.clockValid) {
                if (contentRepository.isStale()) refreshContent()
                if (weatherRepository.isStale()) refreshWeather()
                if (energyRepository?.isStale() == true) refreshEnergy()
                if (shoppingRepository?.isStale() == true) refreshShopping()
                if (choresRepository?.isStale() == true) refreshChores()
            }
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    private fun tick() {
        val time = clock.current()
        _state.update { it.copy(now = time.now, clockValid = time.valid) }
    }

    /** Fuerza la sincronización (al recuperar la red, tras iniciar sesión...). */
    fun refreshNow() {
        viewModelScope.launch {
            tick()
            if (!_state.value.clockValid) return@launch
            refreshContent()
            refreshWeather()
            refreshEnergy()
            refreshShopping()
            refreshChores()
        }
    }

    /** Sin hora válida no se pide nada: las fechas de la petición serían las de fábrica. */
    private suspend fun refreshContent() {
        val token = _state.value.token ?: return
        report(contentRepository.refresh(token, LocalDate.now()))
    }

    private suspend fun refreshShopping() {
        val repo = shoppingRepository ?: return
        report(repo.refresh())
    }

    private suspend fun refreshChores() {
        val repo = choresRepository ?: return
        report(repo.refresh())
    }

    /** Un niño marca [chore] como hecha por él. */
    fun choreDone(chore: String, kid: String) = sendChore(chore) { it.done(chore, kid) }

    /** Deshace una tarea hecha hoy ([done] es el id de la hecha). */
    fun choreUndo(done: String) = sendChore(done) { it.undo(done) }

    /** Un niño pide canjear [reward]. */
    fun rewardClaim(reward: String, kid: String) = sendChore(reward) { it.claim(reward, kid) }

    /** Un padre aprueba o rechaza el canje [claim] con su [pin]. */
    fun rewardDecide(claim: String, pin: String, approve: Boolean) = sendChore(claim) { it.decide(claim, pin, approve) }

    fun clearChoreError() = _state.update { it.copy(choreError = null) }

    /** Añade un habitual a la compra (Bring!; Alexa lo recibe al minuto). Sale «en la lista» enseguida. */
    fun shoppingAdd(producto: String) {
        val url = icsConfig.choresUrl ?: return
        val token = icsConfig.choresToken ?: return
        val key = producto.lowercase()
        addedShopping = addedShopping + key
        hiddenShopping = hiddenShopping - key
        shoppingRepository?.list?.value?.value?.let { list -> _state.update { it.copy(shopping = list.adjusted(hiddenShopping, addedShopping)) } }
        viewModelScope.launch {
            val ok = runCatching { api.shoppingAdd(url + "compra/anadir", token, ShoppingRemoveRequest(producto)) }.getOrNull()?.isSuccessful == true
            if (!ok) {
                addedShopping = addedShopping - key
                shoppingRepository?.list?.value?.value?.let { list -> _state.update { it.copy(shopping = list.adjusted(hiddenShopping, addedShopping)) } }
            }
            refreshShopping()
        }
    }

    /** Consigna del termostato, de medio en medio grado (15–25 °C). */
    fun homeSetpoint(degrees: Double) {
        val g = (Math.round(degrees * 2) / 2.0).coerceIn(15.0, 25.0)
        updateHome { it.copy(clima = it.clima?.copy(consigna = g)) }
        sendHome("consigna", mapOf("grados" to g))
    }

    /** Modo del termostato: home / away / night / freeze. */
    fun homePreset(mode: String) {
        updateHome { it.copy(clima = it.clima?.copy(modo = mode)) }
        sendHome("modo", mapOf("modo" to mode))
    }

    /** Toldo: «open» (sacar), «close» (recoger) o «stop». */
    fun homeAwning(action: String) {
        val accion = when (action) {
            "open" -> "sacar"
            "close" -> "recoger"
            else -> "parar"
        }
        sendHome("toldo", mapOf("accion" to accion))
    }

    /** Música en un Echo: play / pause / next / previous / volume_up / volume_down. El volumen va como valor final. */
    fun homeMusic(action: String, speaker: String) {
        if (speaker.isEmpty()) return
        val music = _state.value.shopping?.casa?.musica
        when (action) {
            "volume_up", "volume_down" -> {
                val level = ((music?.volumen ?: 0.0) + if (action == "volume_up") 0.1 else -0.1).coerceIn(0.0, 1.0)
                updateHome { it.copy(musica = it.musica?.copy(volumen = level)) }
                sendHome("musica", mapOf("accion" to "volumen", "nivel" to level, "altavoz" to speaker))
            }
            else -> {
                if (action == "play" || action == "pause") updateHome { it.copy(musica = it.musica?.copy(sonando = action == "play")) }
                sendHome("musica", mapOf("accion" to action, "altavoz" to speaker))
            }
        }
    }

    /** Lo pulsado se ve enseguida: HA tarda unos segundos en confirmarlo y compra.json, hasta un minuto. */
    private fun updateHome(change: (com.lito.planify.dashboard.data.HomeStatus) -> com.lito.planify.dashboard.data.HomeStatus) {
        _state.update { s -> s.copy(shopping = s.shopping?.let { list -> list.casa?.let { list.copy(casa = change(it)) } ?: list }) }
    }

    /** Manda la orden a familia-tareas; si falla, se vuelve a pedir el estado real para deshacer lo pintado. */
    private fun sendHome(what: String, body: Map<String, Any>) {
        val url = icsConfig.choresUrl ?: return
        val token = icsConfig.choresToken ?: return
        viewModelScope.launch {
            val ok = runCatching { api.homeAction(url + "casa/" + what, token, body) }.getOrNull()?.isSuccessful == true
            if (!ok) {
                shoppingRepository?.list?.value?.value?.let { list -> _state.update { it.copy(shopping = list.without(hiddenShopping)) } }
                refreshShopping()
            }
        }
    }

    /**
     * Quita [producto] de la compra (Bring! lo da por comprado; de Alexa lo quita compra-sync al minuto). Se oculta
     * enseguida; si el servidor no responde, vuelve a aparecer.
     */
    fun shoppingRemove(producto: String) {
        val url = icsConfig.choresUrl ?: return
        val token = icsConfig.choresToken ?: return
        val key = producto.lowercase()
        hiddenShopping = hiddenShopping + key
        _state.update { it.copy(shopping = it.shopping?.adjusted(hiddenShopping, addedShopping)) }
        viewModelScope.launch {
            val ok = runCatching { api.shoppingRemove(url + "compra/quitar", token, ShoppingRemoveRequest(producto)) }
                .getOrNull()?.let { it.isSuccessful || it.code() == 409 } == true  // 409: ya no estaba
            if (!ok) {
                hiddenShopping = hiddenShopping - key
                shoppingRepository?.list?.value?.value?.let { list -> _state.update { it.copy(shopping = list.adjusted(hiddenShopping, addedShopping)) } }
            }
            refreshShopping()
        }
    }

    private fun sendChore(busy: String, send: suspend (ChoresRepository) -> Result<Chores>) {
        val repo = choresRepository ?: return
        if (_state.value.choreBusy != null) return
        _state.update { it.copy(choreBusy = busy, choreError = null) }
        viewModelScope.launch {
            val result = send(repo)
            val error = result.exceptionOrNull()
            // El servidor contestó (PIN malo, ya hecha...): no es falta de conexión
            if (error !is HttpError) report(result)
            _state.update {
                it.copy(
                    choreBusy = null,
                    choreError = when {
                        error == null -> null
                        error is HttpError && error.code == 403 -> "PIN incorrecto (o bloqueado unos minutos)"
                        error is HttpError -> "No se ha podido: puede que ya estuviera hecho"
                        else -> "Sin conexión"
                    }
                )
            }
        }
    }

    private suspend fun refreshEnergy() {
        val repo = energyRepository ?: return
        report(repo.refresh(LocalDate.now()))
    }

    private suspend fun refreshWeather() {
        val location = _state.value.location ?: return
        report(weatherRepository.refresh(location))
    }

    /** Carga la semana del calendario que empieza en [monday] si no está en caché o ha caducado. */
    fun loadWeek(monday: LocalDate) {
        val token = _state.value.token ?: return
        if (!calendarRepository.isStale(monday)) return
        viewModelScope.launch { report(calendarRepository.refresh(token, monday)) }
    }

    /**
     * Botón de sincronizar: vuelve a pedir el contenido de Hoy, las semanas del calendario ya cargadas y el
     * tiempo, aunque no hayan caducado. Dura al menos un segundo para que se vea el giro.
     */
    fun syncAll() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val started = System.currentTimeMillis()
            tick()
            if (_state.value.clockValid) {
                ics?.invalidate()
                refreshContent()
                _state.value.token?.let { token -> calendarRepository.loaded().forEach { report(calendarRepository.refresh(token, it)) } }
                refreshWeather()
                refreshEnergy()
                refreshShopping()
                refreshChores()
            }
            delay((1000 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(syncing = false) }
        }
    }

    /** Estado de la conexión según la última petición. Si falla, la pantalla sigue con la copia en caché. */
    private fun report(result: Result<*>) {
        val error = result.exceptionOrNull()
        when {
            error == null -> _state.update { it.copy(online = true) }
            error.isUnauthorized -> logout()
            else -> _state.update { it.copy(online = false) }
        }
    }

    fun login(email: String, password: String) {
        _state.update { it.copy(loggingIn = true, loginError = null) }
        viewModelScope.launch {
            try {
                val response = api.auth(AuthRequest(email.trim(), password))
                val token = response.body()?.token
                if (!response.isSuccessful || token == null) {
                    _state.update { it.copy(loggingIn = false, loginError = "Email o contraseña incorrectos") }
                    return@launch
                }
                store.token = token
                store.email = email.trim()
                _state.update { it.copy(token = token, email = email.trim(), loggingIn = false) }
                refreshNow()
            } catch (e: Exception) {
                _state.update { it.copy(loggingIn = false, loginError = "Sin conexión con el servidor") }
            }
        }
    }

    /** Con calendarios iCal no hay sesión: borra lo descargado y vuelve a pedirlo («Borrar datos» en Ajustes). */
    fun logout() {
        contentRepository.clear()
        calendarRepository.clear()
        if (ics != null) {
            viewModelScope.launch {
                ics.invalidate()
                refreshNow()
            }
            return
        }
        store.logout()
        _state.update { it.copy(token = null) }
    }

    private companion object {
        /** Sesión ficticia con calendarios iCal: la pantalla trata al marco como con la sesión iniciada. */
        const val ICS_TOKEN = "ics"
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(geoSearching = true) }
        viewModelScope.launch {
            val results = runCatching { api.geocode(Api.geocodeUrl(query.trim())).body()?.results }.getOrNull().orEmpty()
            _state.update { it.copy(geoResults = results, geoSearching = false) }
        }
    }

    fun selectLocation(result: GeoResult) {
        val location = Location(
            name = result.name,
            detail = listOfNotNull(result.admin1, result.country).joinToString(", "),
            latitude = result.latitude,
            longitude = result.longitude
        )
        store.location = location
        weatherRepository.clear()
        _state.update { it.copy(location = location, geoResults = emptyList()) }
        viewModelScope.launch { refreshWeather() }
    }

    fun setSchedule(schedule: Schedule) {
        store.schedule = schedule
        _state.update { it.copy(schedule = schedule) }
        ScreenSchedule.reschedule(getApplication())
    }

    /**
     * Desde el reloj nocturno: vuelve el dashboard. Fuera de horario, solo durante [ScreenSchedule.GRACE_MINUTES];
     * el reloj pedido con la luna se quita del todo.
     */
    fun peek() {
        _state.update { it.copy(peekUntil = LocalDateTime.now().plusMinutes(ScreenSchedule.GRACE_MINUTES), nightUntil = null) }
    }

    /**
     * La luna de la barra lateral. Con el horario y el reloj nocturno activos, pasa al reloj (hasta que se toque o
     * hasta el siguiente encendido) y devuelve true; si no, false y la pantalla se apaga de verdad.
     */
    fun nightNow(): Boolean {
        val schedule = _state.value.schedule
        if (!schedule.enabled || schedule.screenOff || !_state.value.clockValid) return false
        _state.update { it.copy(nightUntil = ScreenSchedule.nextOn(schedule, it.now), peekUntil = null) }
        return true
    }

    fun setBrightness(value: Float?) {
        store.brightness = value
        _state.update { it.copy(brightness = value) }
    }
}
