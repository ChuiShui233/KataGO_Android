package com.chuishui.katago.engine

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * In-process GO engine facade.
 *
 * Instead of exec()ing a packaged elf (whose new process runs in the linker
 * "(default)" namespace that forbids /vendor paths), the GPU engine is built as
 * a real shared library and loaded here with System.loadLibrary(), so the
 * loader resolves its DT_NEEDED entries (libOpenCL.so -> our forwarding shim)
 * through the app's own classloader namespace. PIPEs still carry the GTP
 * protocol, which keeps the rest of [GtpEngine] untouched.
 */
object KataNative {

    private const val TAG = "KataNative"

    init {
        // Loading is deferred to start(); see ensureLoaded() and
        // useImportedOpenCl(). The bundled libOpenCL.so (vendor forwarding shim)
        // would otherwise claim the soname before we can substitute an imported
        // clvk build.
    }

    private external fun startEngineInFd(
        inFd: Int,
        outFd: Int,
        errFd: Int,
        args: Array<String>,
    ): Int

    private external fun isEngineRunning(): Boolean

    /** Public wrapper so callers outside this file (GtpEngine) can poll engine
     *  thread liveness. `internal external` would be name-mangled by Kotlin
     *  (e.g. `isEngineRunning$app_release`), breaking JNI symbol resolution, so
     *  the external function stays private and only this wrapper is exposed. */
    fun nativeEngineRunning(): Boolean = isEngineRunning()

    private external fun setLogFile(path: String)

    /** True once the native library was actually mapped into this process. */
    @Volatile
    var loadFailed: String? = null
        private set

    private var engineLoaded = false

    /**
     * Substitute an externally provided libOpenCL.so (e.g. an imported clvk
     * build) before the engine library loads. Since both register the same
     * soname "libOpenCL.so", loading this one first makes the engine's
     * DT_NEEDED resolve to it instead of the bundled vendor shim. No-op if the
     * engine library is already loaded.
     */
    fun useImportedOpenCl(path: String) {
        if (engineLoaded) return
        val f = File(path)
        if (!f.exists()) return
        try {
            System.load(f.absolutePath)
            Log.i(TAG, "using imported libOpenCL: ${f.absolutePath}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "imported libOpenCL failed: ${e.message}")
        }
    }

    /**
     * Map libkatago_opencl.so if not loaded yet. Call after any
     * [useImportedOpenCl] so the imported libOpenCL.so (if present) wins over
     * the bundled shim. Returns false and sets [loadFailed] on failure.
     */
    fun ensureLoaded(): Boolean {
        if (engineLoaded) return true
        try {
            System.loadLibrary("katago_opencl")
            engineLoaded = true
            Log.i(TAG, "loaded libkatago_opencl.so")
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = e.message
            Log.e(TAG, "System.loadLibrary failed: ${e.message}")
        }
        return engineLoaded
    }

    /** Engine-side native log file, kept so crash/step diagnostics survive a
     *  process-killing segfault that Java can never observe. */
    private var engineLogFile: File? = null

    /** Install crash handlers + point native logging at [dir]. Call once. */
    fun initLogging(dir: File) {
        val logFile = File(dir, "engine_native.log")
        engineLogFile = logFile
        try {
            // Archive the previous run's log instead of deleting it: if the
            // engine crashed mid-start, that record is the only evidence (the
            // marker file also captures it, but a handled-signal backtrace is
            // far richer).
            if (logFile.exists()) {
                val prev = File(dir, "engine_native.prev.log")
                if (prev.exists()) prev.delete()
                try {
                    logFile.copyTo(prev, overwrite = true)
                } catch (_: Exception) {
                }
                logFile.delete()
            }
            setLogFile(logFile.absolutePath)
            Log.i(TAG, "native logging -> ${logFile.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "setLogFile failed: ${e.message}")
        }
    }

    /**
     * Start katago.main() on a detached native thread with stdio wired to
     * freshly created pipes.
     *
     * @return a [Process]-compatible handle so the calling code can keep using
     * the existing reader loops. Its output stream feeds the engine's stdin;
     * its input/error streams carry the engine's stdout/stderr.
     */
    fun start(args: Array<String>): Process {
        if (!ensureLoaded()) {
            throw IllegalStateException("engine unavailable: $loadFailed")
        }
        // Each pipe pair: [0] = read end, [1] = write end (arm64 as Page
        // vended by createPipe). Java keeps the ends it will not hand to the
        // engine thread; native dup2()s its ends onto 0/1/2.
        val stdin = ParcelFileDescriptor.createPipe()   // Java writes [1]; engine reads [0]
        val stdout = ParcelFileDescriptor.createPipe()  // engine writes [1]; Java reads [0]
        val stderr = ParcelFileDescriptor.createPipe()  // engine writes [1]; Java reads [0]

        val proc = KataProcess()
        // NB: we must NOT close stdin[0]/stdout[1]/stderr[1] here: the engine
        // thread dup2()s them asynchronously; closing the original first would
        // either EBADF the dup2 or, worse, let the JVM reuse the fd number for
        // an unrelated file. All six ends are closed by KataProcess.destroy()
        // (or by the engine thread when main() returns).
        Log.i(TAG, "start: fds stdin=${stdin[0].fd}/${stdin[1].fd} " +
            "stdout=${stdout[0].fd}/${stdout[1].fd} stderr=${stderr[0].fd}/${stderr[1].fd}")
        val rc = startEngineInFd(stdin[0].fd, stdout[1].fd, stderr[1].fd, args)
        if (rc != 0) {
            stdin[0].close(); stdin[1].close()
            stdout[0].close(); stdout[1].close()
            stderr[0].close(); stderr[1].close()
            throw IllegalStateException("katago native start returned $rc")
        }
        proc.attach(stdin, stdout, stderr)
        return proc
    }

    /** Process handle backed by pipe fds + the native thread's liveness. */
    class KataProcess : Process() {

        private val exited = CountDownLatch(1)
        @Volatile
        private var exitCode = 0

        private val allPairs = mutableListOf<Array<ParcelFileDescriptor>>()
        private var inp: InputStream? = null
        private var outp: OutputStream? = null
        private var errp: InputStream? = null

        fun attach(
            stdin: Array<ParcelFileDescriptor>,
            stdout: Array<ParcelFileDescriptor>,
            stderr: Array<ParcelFileDescriptor>,
        ) {
            allPairs.clear()
            allPairs.add(stdin)
            allPairs.add(stdout)
            allPairs.add(stderr)
            inp = FileInputStream(stdout[0].fileDescriptor)
            errp = FileInputStream(stderr[0].fileDescriptor)
            outp = FileOutputStream(stdin[1].fileDescriptor)
            // Poll the native running counter; the pipes stay untouched so
            // GtpEngine's own readers get every byte.
            Thread {
                while (true) {
                    try {
                        if (!isEngineRunning()) {
                            exitCode = 0
                            break
                        }
                    } catch (e: Exception) {
                        exitCode = 1
                        break
                    }
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                exited.countDown()
            }.apply { name = "katago-watch"; isDaemon = true; start() }
        }

        override fun getOutputStream(): OutputStream = outp ?: DummyOutputStream()
        override fun getInputStream(): InputStream = inp ?: java.io.ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = errp ?: java.io.ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int {
            exited.await()
            return exitCode
        }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exited.await(timeout, unit)
        override fun exitValue(): Int {
            if (!exited.await(0, TimeUnit.MILLISECONDS)) {
                throw IllegalThreadStateException("katago has not exited")
            }
            return exitCode
        }
        override fun destroy() {
            try {
                outp?.let { it.flush() }
            } catch (_: Exception) {
            }
            for (p in allPairs) {
                for (x in p) {
                    try {
                        x.close()
                    } catch (_: Exception) {
                    }
                }
            }
            allPairs.clear()
            exited.countDown()
        }
        override fun destroyForcibly(): Process {
            destroy()
            return this
        }
    }

    private class DummyOutputStream : OutputStream() {
        override fun write(b: Int) {}
    }

    private fun closeQuietly(p: ParcelFileDescriptor?) {
        try {
            p?.close()
        } catch (_: Exception) {
        }
    }
}