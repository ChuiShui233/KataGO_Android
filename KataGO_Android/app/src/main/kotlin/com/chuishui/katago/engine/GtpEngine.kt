package com.chuishui.katago.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.Writer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal GTP client that wraps the packaged `katago` executable.
 *
 * stdout is read continuously by a background thread. GTP replies (lines
 * starting with "=" or "?") are routed to their caller by command id: every
 * command is sent as "<id> <command>", and KataGo echoes the id back as
 * "=<id> ..." / "?<id> ..." (or a bare "=<id>" header for analysis commands
 * like kata-genmove_analyze). Replies are delivered per-id so commands may be
 * in flight concurrently: an analysis command (kata-search_analyze_cancellable)
 * runs in the background while the engine is idle and is silently dropped the
 * moment a real command (e.g. "play") arrives and interrupts it.
 *
 * Everything else that starts with "info " is forwarded to [onInfo] (used for
 * live analysis).
 */
class GtpEngine {

    @Volatile
    var onInfo: (String) -> Unit = {}

    @Volatile
    var onStderr: (String) -> Unit = {}

    private val lock = Any()
    private var process: Process? = null
    private var writer: Writer? = null
    private val nextCmdId = AtomicInteger(0)
    /** Command ids that have a send() currently waiting on their reply. */
    private val awaiting = HashSet<Int>()
    /** Delivered full replies (id -> reply lines). */
    private val replies = HashMap<Int, List<String>>()
    private val stderrBuffer = StringBuilder()

    fun start(binaryPath: String, configPath: String, modelPath: String, appDataDir: String) {
        stop()
        val args = arrayOf(binaryPath, "gtp", "-config", configPath, "-model", modelPath)
        val proc: Process
        // Engine built as a shared library loaded into this process (the GPU
        // build): run it in-process so the bigger never hits the linker
        // "(default)" namespace that blocks /vendor for exec'd children.
        if (isInProcessEngine(binaryPath)) {
            // If the user imported a clvk libOpenCL.so, load it first so the
            // engine's DT_NEEDED libOpenCL.so resolves to it (Vulkan-backed)
            // instead of the bundled vendor forwarding shim.
            val appDir = File(appDataDir)
            val importedOpenCl = File(appDir, "libOpenCL.so")
            if (importedOpenCl.exists()) {
                KataNative.useImportedOpenCl(importedOpenCl.absolutePath)
            }
            if (!KataNative.ensureLoaded()) {
                throw IllegalStateException("in-process engine unavailable: ${KataNative.loadFailed}")
            }
            KataNative.initLogging(appDir)
            proc = KataNative.start(args)
        } else {
            val pb = ProcessBuilder(args.toList())
            // The binary is exec'd directly, so the linker uses the default
            // namespace and won't search the app's nativeLibraryDir on its own.
            // Point LD_LIBRARY_PATH there so DT_NEEDED entries (e.g. our
            // libOpenCL.so shim co-located with the engine) can be resolved.
            val libDir = File(binaryPath).parentFile?.absolutePath ?: ""
            val env = pb.environment()
            val old = env["LD_LIBRARY_PATH"]
            // Point LD_LIBRARY_PATH at the binary's own dir so any co-located
            // DT_NEEDED dependencies resolve there.
            env["LD_LIBRARY_PATH"] = if (old.isNullOrEmpty()) libDir else "$libDir:$old"
            pb.redirectErrorStream(false)
            proc = try { pb.start() } catch (e: Exception) {
                throw IllegalStateException("Could not start katago: ${e.message}", e)
            } ?: throw IllegalStateException("katago did not start")
        }
        if (!proc.isAlive) {
            throw IllegalStateException("katago exited immediately (exit code ${proc.exitValue()})")
        }
        process = proc
        writer = proc.outputStream.bufferedWriter()

        Thread { readStderr(proc) }.apply {
            name = "katago-stderr"; isDaemon = true; start()
        }
        Thread { readLoop(proc) }.apply {
            name = "katago-stdout"; isDaemon = true; start()
        }
    }

    /** Last lines written to the engine's stderr, for diagnostics. */
    fun stderrLog(): String = synchronized(stderrBuffer) {
        stderrBuffer.toString()
    }

    /** Engine shipped as a JNI shared library (loaded in-process) vs. an
     *  executable that is exec()'d. */
    private fun isInProcessEngine(binaryPath: String): Boolean =
        File(binaryPath).name.contains("opencl")

    private fun readStderr(proc: Process) {
        val reader = proc.errorStream.bufferedReader()
        while (true) {
            val line = try { reader.readLine() } catch (_: Exception) { break } ?: break
            synchronized(stderrBuffer) {
                if (stderrBuffer.length > 32_000) stderrBuffer.setLength(0)
                stderrBuffer.append(line).append('\n')
            }
            onStderr(line)
        }
    }

    fun stop() {
        // Capture the process/writer we are stopping. A concurrent stop() from
        // an earlier cancelled load may still be waiting on native teardown
        // while this engine has already been restarted; never destroy or clear
        // the *new* process's pipes/state.
        val p = process
        val w = writer
        try {
            synchronized(lock) {
                w?.let {
                    it.write("quit\n"); it.flush()
                }
            }
        } catch (_: Exception) {
        }
        // For the in-process (JNI) engine, "quit" is processed asynchronously by
        // the native thread. Closing the pipes right away would kill the native
        // side's stdio mid-shutdown and could leave it wedged. Wait for the
        // native engine thread to actually exit before tearing down the pipes.
        // CLVK/Vulkan resource teardown can take tens of seconds, so be patient.
        if (p is KataNative.KataProcess) {
            var waited = 0
            while (waited++ < 800 && KataNative.nativeEngineRunning()) {
                try { Thread.sleep(50) } catch (_: InterruptedException) { break }
            }
        }
        p?.destroy()
        p?.waitFor(2, TimeUnit.SECONDS)
        p?.destroyForcibly()
        synchronized(lock) {
            if (process === p) process = null
            if (writer === w) writer = null
        }
        if (process !== p) return
        synchronized(awaiting) { awaiting.clear() }
        synchronized(replies) { replies.clear() }
    }

    val isRunning: Boolean get() = (process?.isAlive == true)

    private fun readLoop(proc: Process) {
        val reader = proc.inputStream.bufferedReader()
        var currentId: Int? = null
        var collected = mutableListOf<String>()
        while (true) {
            val line = try { reader.readLine() } catch (_: Exception) { break } ?: break
            if (line.isEmpty()) {
                // Blank line terminates the current reply. Deliver whatever was
                // collected to the caller waiting on this id (if any).
                val id = currentId
                val done = collected
                currentId = null
                collected = mutableListOf()
                if (id != null && synchronized(awaiting) { awaiting.contains(id) }) {
                    synchronized(replies) { replies[id] = done }
                }
                continue
            }
            // kata-genmove_analyze / kata-search_analyze_cancellable print a
            // bare "=N" (or "=" without an id) header first, then info lines
            // during search, then the reply body ("play <move>"), then a blank
            // line. Check this before the numbered-reply pattern so a bare
            // "=N" (no trailing space) is not mistaken for an empty numbered
            // reply. Track the header's id and buffer the non-info body lines.
            if (line == "=" || (line.length >= 2 && line[0] == '=' &&
                    line.substring(1).all { it.isDigit() })) {
                currentId = line.substring(1).ifEmpty { null }?.toIntOrNull()
                collected = mutableListOf()
                continue
            }
            // Standard reply: "=N <rest>" / "?N <rest>" (or "= <rest>" without
            // an id). Ids are only present when the command was sent with an id
            // prefix, which our [send]/[sendAsync] always do. An empty reply is
            // "=N " (trailing space); deliver "= " so callers keep seeing the
            // classic GTP shape.
            val m = REPLY_RE.matchEntire(line)
            if (m != null) {
                val isError = m.groupValues[1] == "?"
                val id = m.groupValues[2].ifEmpty { null }?.toInt()
                val rest = m.groupValues[3].trim()
                currentId = null
                collected = mutableListOf()
                if (id != null) {
                    val body = if (rest.isEmpty()) (if (isError) "? " else "= ") else
                        (if (isError) "? $rest" else "= $rest")
                    if (synchronized(awaiting) { awaiting.contains(id) }) {
                        synchronized(replies) { replies[id] = listOf(body) }
                    }
                }
                continue
            }
            if (line.startsWith("info ")) {
                onInfo(line)
            } else {
                collected.add(line)
            }
        }
    }

    /** Sends one GTP command and waits for the full reply (terminated by a blank line). */
    suspend fun send(command: String): String {
        return withContext(Dispatchers.IO) {
            val id = nextCmdId.incrementAndGet()
            try {
                synchronized(lock) {
                    val proc = process ?: return@withContext "= ERR engine not running"
                    if (!proc.isAlive) return@withContext "= ERR engine not running"
                    val w = writer ?: return@withContext "= ERR engine not running"
                    synchronized(awaiting) { awaiting.add(id) }
                    w.write("$id $command\n")
                    w.flush()
                }
            } catch (e: Exception) {
                return@withContext "= ERR write failed: ${e.message}"
            }
            var waited = 0
            while (true) {
                val r = synchronized(replies) { replies.remove(id) }
                if (r != null) {
                    synchronized(awaiting) { awaiting.remove(id) }
                    return@withContext r.joinToString("\n")
                }
                if (process?.isAlive != true) {
                    synchronized(awaiting) { awaiting.remove(id) }
                    return@withContext "= ERR engine not running"
                }
                if (waited++ > 15000) {
                    synchronized(awaiting) { awaiting.remove(id) }
                    return@withContext "= timeout"
                }
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE")
            "= timeout"
        }
    }

    /**
     * Sends one GTP command without waiting for its reply. The reply (and any
     * interleaved info lines) is dropped once read. Used for background
     * analysis that is safe to interrupt: the next numbered command (e.g.
     * "play") preempts the in-flight cancellable search and its reply is
     * simply never awaited.
     */
    fun sendAsync(command: String) {
        try {
            synchronized(lock) {
                val proc = process ?: return
                if (!proc.isAlive) return
                val w = writer ?: return
                val id = nextCmdId.incrementAndGet()
                w.write("$id $command\n")
                w.flush()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Writes a bare newline to the engine, cancelling any in-flight cancellable
     * analysis/search (KataGo stops analysis on any input line, see gtp.cpp).
     * Nothing is written to [awaiting]/[replies]: the interrupted search prints
     * "play cancelled" plus a terminating blank line, which the read loop drops.
     * No-op if no process is running.
     */
    fun interrupt() {
        try {
            synchronized(lock) {
                val proc = process ?: return
                if (!proc.isAlive) return
                val w = writer ?: return
                w.write("\n")
                w.flush()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        /** "=12 D4", "?12 bad move", "= D4", or an empty "=12 " numbered reply. */
        private val REPLY_RE = Regex("""^([=?])(\d*)(.*)$""")
    }
}