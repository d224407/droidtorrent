package com.droidtorrent

import android.content.Context
import android.os.Environment
import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionHandle
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus as LtStatus
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** libtorrent session wrapper; keeps torrents in state.json so they survive restarts. */
object TorrentEngine {
    private lateinit var dir: File
    private var sm: SessionManager? = null
    private var state = JSONObject()
    private var dirty = false
    private var poller: Job? = null
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wasComplete = HashMap<String, Boolean>()
    private val prioApplied = HashSet<String>()
    private val sawDownload = HashSet<String>()

    private val _torrents = MutableStateFlow<List<TorrentItem>>(emptyList())
    val torrents: StateFlow<List<TorrentItem>> = _torrents

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _completed = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val completed: SharedFlow<String> = _completed

    // ---- lifecycle -------------------------------------------------------

    fun start(context: Context) {
        synchronized(lock) { startLocked(context) }
    }

    private fun startLocked(context: Context) {
        if (sm != null) return
        dir = File(context.filesDir, "libtorrent").apply { mkdirs() }
        File(dir, "torrents").mkdirs()
        loadState()

        val manager = SessionManager(false)
        manager.start(SessionParams(buildPack(_settings.value)))
        sm = manager

        val all = state.getJSONObject("torrents")
        for (hash in all.keys().asSequence().toList()) {
            val m = all.getJSONObject(hash)
            try {
                val save = File(m.getString("savePath"))
                val tf = torrentFile(hash)
                if (tf.exists()) manager.download(TorrentInfo(tf), save)
                else manager.download(m.getString("magnet"), save, torrent_flags_t())
                if (m.optBoolean("paused")) pauseHandle(hash)
            } catch (_: Exception) {
                // Entry is kept and retried on next start.
            }
        }

        poller = scope.launch {
            while (isActive) {
                refresh()
                delay(2000)
            }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            poller?.cancel()
            saveState()
            sm?.stop()
            sm = null
        }
    }

    // ---- public API ------------------------------------------------------

    fun add(link: String): AddResult = synchronized(lock) {
        val manager = sm ?: error("Engine not started")
        if (!link.startsWith("magnet:")) error("Unsupported link")
        val hash = AddTorrentParams.parseMagnetUri(link).infoHashes.v1.toString()
        if (find(hash) != null) return AddResult.Duplicate
        val save = saveDir()
        manager.download(link, save, torrent_flags_t())
        register(hash, link, save)
        AddResult.Added
    }

    fun add(bytes: ByteArray): AddResult = synchronized(lock) {
        val manager = sm ?: error("Engine not started")
        val ti = TorrentInfo.bdecode(bytes)
        val hash = ti.infoHashV1().toString()
        if (find(hash) != null) return AddResult.Duplicate
        val save = saveDir()
        torrentFile(hash).writeBytes(ti.bencode())
        manager.download(ti, save)
        register(hash, "", save)
        AddResult.Added
    }

    fun start(id: Int) = withHash(id) { hash, m ->
        m.put("paused", false)
        resumeHandle(hash)
    }

    fun stop(id: Int) = withHash(id) { hash, m ->
        m.put("paused", true)
        pauseHandle(hash)
    }

    fun remove(id: Int, withData: Boolean) = withHash(id) { hash, _ ->
        find(hash)?.let { h ->
            if (withData) sm?.remove(h, SessionHandle.DELETE_FILES) else sm?.remove(h)
        }
        state.getJSONObject("torrents").remove(hash)
        torrentFile(hash).delete()
        wasComplete.remove(hash)
        prioApplied.remove(hash)
        sawDownload.remove(hash)
        _torrents.value = _torrents.value.filter { it.id != id }
    }

    fun files(id: Int): List<TorrentFile> = synchronized(lock) {
        val hash = hashOf(id) ?: return emptyList()
        val h = find(hash) ?: return emptyList()
        val ti = h.torrentFile() ?: return emptyList()
        val fs = ti.files()
        val prios = h.filePriorities()
        val progress = h.fileProgress(TorrentHandle.PIECE_GRANULARITY)
        List(fs.numFiles()) { i ->
            TorrentFile(
                index = i,
                name = fs.filePath(i),
                length = fs.fileSize(i),
                completed = progress.getOrElse(i) { 0L },
                wanted = prios.getOrElse(i) { Priority.NORMAL } != Priority.IGNORE,
            )
        }
    }

    fun setFilesWanted(id: Int, indices: List<Int>, wanted: Boolean) = withHash(id) { hash, m ->
        val h = find(hash) ?: return@withHash
        val ti = h.torrentFile() ?: return@withHash
        val current = h.filePriorities()
        val next = Array(ti.numFiles()) { current.getOrElse(it) { Priority.NORMAL } }
        for (i in indices) if (i in next.indices) next[i] = if (wanted) Priority.NORMAL else Priority.IGNORE
        h.prioritizeFiles(next)
        m.put("prios", JSONArray().also { a -> next.forEach { a.put(it.swig()) } })
    }

    fun updateSettings(change: (Settings) -> Settings) {
        synchronized(lock) {
            val next = change(_settings.value)
            _settings.value = next
            state.put("settings", settingsToJson(next))
            sm?.applySettings(buildPack(next))
            saveState()
        }
    }

    // ---- internals -------------------------------------------------------

    private fun withHash(id: Int, block: (String, JSONObject) -> Unit) {
        synchronized(lock) {
            val hash = hashOf(id) ?: return
            block(hash, state.getJSONObject("torrents").getJSONObject(hash))
            dirty = true
            saveState()
        }
    }

    private fun register(hash: String, magnet: String, save: File) {
        val id = state.getInt("nextId")
        state.put("nextId", id + 1)
        state.getJSONObject("torrents").put(hash, JSONObject()
            .put("id", id)
            .put("magnet", magnet)
            .put("savePath", save.absolutePath)
            .put("paused", false)
            .put("prios", JSONArray())
            .put("added", System.currentTimeMillis() / 1000))
        dirty = true
        saveState()
    }

    private fun saveDir(): File = File(_settings.value.downloadDir).apply { mkdirs() }

    private fun torrentFile(hash: String) = File(File(dir, "torrents"), "$hash.torrent")

    private fun find(hash: String): TorrentHandle? {
        val h = sm?.find(Sha1Hash(hash))
        return if (h != null && h.isValid) h else null
    }

    private fun hashOf(id: Int): String? {
        val all = state.getJSONObject("torrents")
        for (k in all.keys()) if (all.getJSONObject(k).getInt("id") == id) return k
        return null
    }

    private fun pauseHandle(hash: String) {
        val h = find(hash) ?: return
        h.unsetFlags(TorrentFlags.AUTO_MANAGED)
        h.pause()
    }

    private fun resumeHandle(hash: String) {
        val h = find(hash) ?: return
        h.setFlags(TorrentFlags.AUTO_MANAGED)
        h.resume()
    }

    private fun buildPack(s: Settings): SettingsPack {
        val sp = SettingsPack()
        sp.listenInterfaces("0.0.0.0:${s.port},[::]:${s.port}")
        sp.downloadRateLimit(if (s.limitDownEnabled) s.limitDown * 1024 else 0)
        sp.uploadRateLimit(if (s.limitUpEnabled) s.limitUp * 1024 else 0)
        sp.activeDownloads(if (s.queueSize > 0) s.queueSize else -1)
        return sp
    }

    private fun refresh() {
        synchronized(lock) {
            if (sm == null) return
            val all = state.getJSONObject("torrents")
            _torrents.value = all.keys().asSequence().toList()
                .mapNotNull { describe(it, all.getJSONObject(it)) }
            if (dirty) saveState()
        }
    }

    private fun describe(hash: String, m: JSONObject): TorrentItem? {
        val h = find(hash)
        val paused = m.optBoolean("paused")
        var item = TorrentItem(
            id = m.getInt("id"), name = hash, progress = 0f,
            status = if (paused) TorrentStatus.Stopped else TorrentStatus.Checking,
            size = 0, rateDown = 0, rateUp = 0, downloaded = 0, uploaded = 0, etaSeconds = -1,
            peers = 0, error = "", location = m.getString("savePath"), addedDate = m.getLong("added"),
            magnet = m.optString("magnet"), pieceCount = 0, pieceSize = 0, comment = "",
            creator = "", isPrivate = false,
        )
        if (h == null) return item

        val st: LtStatus = h.status()
        val lt = st.state()
        val hasMeta = h.torrentFile() != null
        val complete = hasMeta && (
            st.isFinished || st.isSeeding ||
                lt == LtStatus.State.FINISHED || lt == LtStatus.State.SEEDING ||
                (st.totalWanted() > 0 && st.totalWantedDone() >= st.totalWanted()))

        val before = wasComplete[hash]
        if (!complete && lt == LtStatus.State.DOWNLOADING) sawDownload.add(hash)
        if (before == false && complete && sawDownload.remove(hash)) {
            _completed.tryEmit(st.name())
        }
        wasComplete[hash] = complete

        val remaining = st.totalWanted() - st.totalWantedDone()
        val ec = st.swig().errc
        val checking = !complete && (lt == LtStatus.State.CHECKING_FILES ||
            lt == LtStatus.State.CHECKING_RESUME_DATA)

        item = item.copy(
            name = st.name().ifEmpty { hash },
            progress = if (complete) 1f else st.progress(),
            status = when {
                paused -> TorrentStatus.Stopped
                checking -> TorrentStatus.Checking
                complete -> TorrentStatus.Seeding
                else -> TorrentStatus.Downloading
            },
            rateDown = st.downloadRate(),
            rateUp = st.uploadRate(),
            downloaded = st.allTimeDownload(),
            uploaded = st.allTimeUpload(),
            peers = st.numPeers(),
            location = h.savePath(),
            etaSeconds = if (st.downloadRate() > 0 && !complete) remaining / st.downloadRate() else -1,
            error = if (ec.value() != 0) ec.message() else "",
            magnet = h.makeMagnetUri(),
        )

        val ti = h.torrentFile() ?: return item

        // Restore saved file priorities once metadata is available.
        if (prioApplied.add(hash)) {
            val saved = m.optJSONArray("prios")
            if (saved != null && saved.length() == ti.numFiles()) {
                h.prioritizeFiles(Array(saved.length()) { Priority.fromSwig(saved.getInt(it)) })
            }
        }

        return item.copy(
            size = ti.totalSize(),
            pieceCount = ti.numPieces(),
            pieceSize = ti.pieceLength(),
            comment = ti.comment() ?: "",
            creator = ti.creator() ?: "",
            isPrivate = ti.isPrivate,
        )
    }

    // ---- persistence -----------------------------------------------------

    private fun defaultSettings() = Settings(
        downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
    )

    private fun settingsToJson(s: Settings) = JSONObject()
        .put("downloadDir", s.downloadDir)
        .put("queueSize", s.queueSize)
        .put("port", s.port)
        .put("limitDownEnabled", s.limitDownEnabled)
        .put("limitUpEnabled", s.limitUpEnabled)
        .put("limitDown", s.limitDown)
        .put("limitUp", s.limitUp)
        .put("accent", s.accent)

    private fun settingsFromJson(j: JSONObject, d: Settings) = Settings(
        downloadDir = j.optString("downloadDir", d.downloadDir),
        queueSize = j.optInt("queueSize", d.queueSize),
        port = j.optInt("port", d.port),
        limitDownEnabled = j.optBoolean("limitDownEnabled", d.limitDownEnabled),
        limitUpEnabled = j.optBoolean("limitUpEnabled", d.limitUpEnabled),
        limitDown = j.optInt("limitDown", d.limitDown),
        limitUp = j.optInt("limitUp", d.limitUp),
        accent = j.optInt("accent", d.accent),
    )

    private fun loadState() {
        val f = File(dir, "state.json")
        state = if (f.exists()) JSONObject(f.readText()) else JSONObject()
        if (!state.has("torrents")) state.put("torrents", JSONObject())
        if (!state.has("nextId")) state.put("nextId", 1)
        val d = defaultSettings()
        _settings.value = state.optJSONObject("settings")?.let { settingsFromJson(it, d) } ?: d
        state.put("settings", settingsToJson(_settings.value))
    }

    private fun saveState() {
        File(dir, "state.json").writeText(state.toString())
        dirty = false
    }
}
