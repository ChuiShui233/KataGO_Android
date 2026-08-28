#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <cstdio>
#include <cstring>
#include <atomic>
#include <thread>
#include <string>
#include <mutex>
#include <condition_variable>

#include "main.h"
#include "core/mainargs.h"

// Global state for the engine thread
static std::atomic<bool> g_engineRunning{false};
static std::thread g_engineThread;
static std::string g_logFilePath;
static std::mutex g_logMutex;
static FILE* g_logFile = nullptr;
static std::mutex g_engineMutex;
static std::condition_variable g_engineCv;

// Crash handler that writes to the log file
static void crashHandler(int sig, siginfo_t* info, void* context) {
  (void)context;
  if (g_logFile) {
    fprintf(g_logFile, "CRASH signal=%d si_code=%d addr=%p\n",
            sig, info ? info->si_code : 0, info ? info->si_ptr : nullptr);
    fflush(g_logFile);
  }
  // Re-raise with default handler to get proper tombstone
  signal(sig, SIG_DFL);
  raise(sig);
}

// Redirect stderr/stdout to the log file
static void redirectStdioToLog() {
  std::lock_guard<std::mutex> lock(g_logMutex);
  if (g_logFilePath.empty()) return;
  if (g_logFile) fclose(g_logFile);
  g_logFile = fopen(g_logFilePath.c_str(), "a");
  if (g_logFile) {
    fprintf(g_logFile, "[android_jni] Logging to %s\n", g_logFilePath.c_str());
    fflush(g_logFile);
  }
}

#define JNI_FUNC __attribute__((used)) __attribute__((visibility("default")))

extern "C" {

JNIEXPORT jint JNICALL JNI_FUNC
Java_com_chuishui_katago_engine_KataNative_startEngineInFd(
    JNIEnv* env, jobject /* this */,
    jint inFd, jint outFd, jint errFd,
    jobjectArray argsArray) {

  if (g_engineRunning.load()) {
    // A previous engine thread may still be shutting down (e.g. the GTP quit
    // was handled but the thread has not finished unwinding, or the Kotlin
    // layer reused the process quickly after stop()). Wait for it to exit
    // before starting a new engine thread instead of failing outright.
    fprintf(stderr, "[android_jni] previous engine still running, waiting for it to exit\n");
    std::unique_lock<std::mutex> lock(g_engineMutex);
    bool exited = g_engineCv.wait_for(lock, std::chrono::seconds(30), [] {
      return !g_engineRunning.load();
    });
    if (!exited) {
      fprintf(stderr, "[android_jni] previous engine did not exit within 30s, aborting start\n");
      return -1; // Already running
    }
  }

  // Convert Java String[] to C++ vector<string>
  jsize argc = env->GetArrayLength(argsArray);
  std::vector<std::string> cppArgs;
  cppArgs.reserve(argc);
  for (jsize i = 0; i < argc; i++) {
    auto jstr = (jstring)env->GetObjectArrayElement(argsArray, i);
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    cppArgs.emplace_back(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
  }

  // Install crash handler
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = crashHandler;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sigaction(SIGSEGV, &sa, nullptr);
  sigaction(SIGBUS, &sa, nullptr);
  sigaction(SIGABRT, &sa, nullptr);

  fprintf(stderr, "[android_jni] crash handlers installed\n");
  if (g_logFile) {
    fprintf(g_logFile, "[android_jni] crash handlers installed\n");
    fflush(g_logFile);
  }

  // Save fd values (dup them so they survive if the caller closes the originals)
  int fd_in  = dup(inFd);
  int fd_out = dup(outFd);
  int fd_err = dup(errFd);

  g_engineRunning.store(true);

  g_engineThread = std::thread([cppArgs, fd_in, fd_out, fd_err]() {
    fprintf(stderr, "[android_jni] engine_thread: dup2 stdio\n");
    if (g_logFile) {
      fprintf(g_logFile, "[android_jni] engine_thread: dup2 stdio\n");
      fflush(g_logFile);
    }

    // Redirect stdin/stdout/stderr to the provided pipe fds
    dup2(fd_in,  STDIN_FILENO);
    dup2(fd_out, STDOUT_FILENO);
    dup2(fd_err, STDERR_FILENO);
    close(fd_in);
    close(fd_out);
    close(fd_err);

    // Initialize UTF-8 for cout/cerr on Android
    MainArgs::makeCoutAndCerrAcceptUTF8();

    fprintf(stderr, "[android_jni] engine_thread: calling main()\n");
    if (g_logFile) {
      fprintf(g_logFile, "[android_jni] engine_thread: calling main()\n");
      fflush(g_logFile);
    }

    // Kotlin passes [binaryPath, "gtp", "-config", config, "-model", model]
    // main.cpp strips binaryPath and passes ["gtp", "-config", ...] to gtp()
    std::vector<std::string> gtpArgs(cppArgs.begin() + 1, cppArgs.end());

    // Call KataGo's GTP main
    try {
      MainCmds::gtp(gtpArgs);
    } catch (...) {
      fprintf(stderr, "[android_jni] engine_thread: exception caught\n");
      if (g_logFile) {
        fprintf(g_logFile, "[android_jni] engine_thread: exception caught\n");
        fflush(g_logFile);
      }
    }

    fprintf(stderr, "[android_jni] engine_thread: main() returned\n");
    if (g_logFile) {
      fprintf(g_logFile, "[android_jni] engine_thread: main() returned\n");
      fflush(g_logFile);
    }

    g_engineRunning.store(false);
    g_engineCv.notify_all();
  });
  g_engineThread.detach();

  return 0;
}

JNIEXPORT jboolean JNICALL JNI_FUNC
Java_com_chuishui_katago_engine_KataNative_isEngineRunning(
    JNIEnv* /* env */, jobject /* this */) {
  return g_engineRunning.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FUNC
Java_com_chuishui_katago_engine_KataNative_setLogFile(
    JNIEnv* env, jobject /* this */, jstring path) {
  const char* cpath = env->GetStringUTFChars(path, nullptr);
  {
    std::lock_guard<std::mutex> lock(g_logMutex);
    g_logFilePath = cpath;
  }
  redirectStdioToLog();
  fprintf(stderr, "[android_jni] KataNative.setLogFile: %s\n", cpath);
  if (g_logFile) {
    fprintf(g_logFile, "[android_jni] KataNative.setLogFile: %s\n", cpath);
    fflush(g_logFile);
  }
  env->ReleaseStringUTFChars(path, cpath);
}

} // extern "C"
