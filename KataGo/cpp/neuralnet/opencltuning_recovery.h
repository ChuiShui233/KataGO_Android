#ifndef NEURALNET_OPENCLTUNING_RECOVERY_H_
#define NEURALNET_OPENCLTUNING_RECOVERY_H_

// ---------------------------------------------------------------------------
// Android stability layer for OpenCL autotuning.
//
// KataGo's OpenCL tuner benchmarks whole families of kernel/config combinations
// and only persists the *final* result at the very end (OpenCLTuneParams::save
// after OpenCLTuner::tune returns). On some Android SoCs (e.g. Adreno 750 via
// the CLVK -> Vulkan stack) particular (kernel, parameter) combinations make the
// native process SIGSEGV before that final save happens, so the next launch
// starts tuning from scratch and re-crashes forever.
//
// This module implements the "Android stability protection layer":
//
//   1. checkpoint  - every case that successfully benchmarked is persisted
//                    immediately (tuning_checkpoint.json), including a full
//                    "last successful full config" snapshot in KataGo's own
//                    tune-file text format for crash restoration.
//   2. blacklist   - cases that explicitly failed (or crashed, when recorded by
//                    the Android layer after a process kill) are persisted
//                    (tuning_blacklist.json) and skipped on the next run.
//   3. recovery    - a marker (autotune_recovery.marker) tells the tuner to
//                    restore the last successful profile instead of tuning.
//   4. safe mode   - a marker (use_safe_defaults.marker) tells the tuner to
//                    stop autotuning and fall back to known-good/default config
//                    (MAX_AUTOTUNE_RECOVERY exhaustion is tracked by Android).
//   5. hardware    - every record is bound to a hardware/profile fingerprint
//                    (GPU name, OpenCL platform/device/driver, Vulkan driver,
//                    KataGo version, model/network config, board size).
//   6. atomic      - all writes go through "<file>.tmp.<pid>" + fsync + rename.
//
// This module intentionally has NO KataGo dependencies (no Logger, no FileUtils,
// no OpenCL types) so it can be unit-tested in isolation on the host and cannot
// itself destabilize the KataGo build. Everything is passed in through the
// constructor and method arguments.
// ---------------------------------------------------------------------------

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <utility>
#include <vector>

namespace OpenCLTuningRecovery {

// Very small self-contained JSON value (only what we need for the checkpoint and
// blacklist files). Object key order is preserved. Throws std::runtime_error on
// parse errors so callers can simply ignore corrupt files.
class Json {
 public:
  enum class Type { Null, Bool, Number, String, Array, Object };

  Json();
  explicit Json(Type type);

  static Json boolean(bool v);
  static Json number(long long v);
  static Json number(double v);
  static Json string(const std::string& v);

  Type type() const;
  bool isNull() const;
  bool isBool() const;
  bool isNumber() const;
  bool isString() const;
  bool isArray() const;
  bool isObject() const;

  bool asBool() const;
  long long asInt() const;
  double asNumber() const;
  const std::string& asString() const;

  // Object access. If this node is not an object it is converted to one.
  Json& operator[](const std::string& key);
  bool has(const std::string& key) const;
  const Json* find(const std::string& key) const;
  Json* find(const std::string& key);
  std::vector<std::string> keys() const;

  // Array access.
  std::size_t size() const;
  void push_back(const Json& v);
  const Json& at(std::size_t i) const;

  std::string dump() const;

  // Parses a JSON document. Throws std::runtime_error on any syntax error.
  static Json parse(const std::string& text);

  // Convenience getters (our own files use these shapes).
  std::string getString(const std::string& key, const std::string& def) const;
  long long getInt(const std::string& key, long long def) const;

 private:
  Type type_;
  bool bool_;
  long long int_;
  double num_;
  std::string str_;
  std::vector<Json> arr_;
  std::vector<std::pair<std::string, Json> > obj_;
  bool isInteger_;
};
// Hardware / model fingerprint that every checkpoint and blacklist entry is
// bound to. profilesMatch requires that every field that is non-empty/meaningful
// on BOTH sides is equal; fields that only one side knows are ignored so that a
// blacklist entry recorded before the first checkpoint still works on the device
// that wrote it.
struct Profile {
  std::string kataGoVersion;
  std::string gpuName;         // OpenCL device name (e.g. "Adreno (TM) 750")
  std::string platformDesc;    // OpenCL platform description / vendor string
  std::string deviceName;      // OpenCL device name (== gpuName in practice)
  std::string openclVersion;   // CL_DEVICE_OPENCL_C_VERSION or CL_PLATFORM_VERSION
  std::string clDriverVersion; // CL_DRIVER_VERSION (over CLVK this embeds the Vulkan driver)
  std::string clVendor;        // CL_DEVICE_VENDOR
  std::string vulkanDriver;    // Android side may fill this in from logs

  // Model / network configuration.
  long long modelVersion;
  long long trunkNumChannels;
  long long midNumChannels;
  long long regularNumChannels;
  long long gpoolNumChannels;
  long long transformerHeadDim;
  long long transformerVHeadDim;
  long long transformerNumHeads;
  long long transformerNumKVHeads;
  long long transformerFFNChannels;
  long long nnXLen;
  long long nnYLen;
  long long batchSize;

  // Android device identity (filled in by the Android layer).
  std::string androidModel;
  std::string androidManufacturer;
  std::string androidDevice;

  Profile();

  Json toJson() const;
  static Profile fromJson(const Json& j);

  // True when every field that is set on both sides is equal.
  static bool profilesMatch(const Profile& a, const Profile& b);
};

// Constants that both the native layer and the Android layer agree on.
static const int MAX_AUTOTUNE_RECOVERY = 3;
static const long long SCHEMA_VERSION = 1;
static const char* CHECKPOINT_FILENAME = "tuning_checkpoint.json";
static const char* BLACKLIST_FILENAME = "tuning_blacklist.json";
static const char* RECOVERY_MARKER_FILENAME = "autotune_recovery.marker";
static const char* SAFE_DEFAULTS_MARKER_FILENAME = "use_safe_defaults.marker";
static const char* KATAGO_VERSION = "1.17.2";

// Utility helpers (kept here so the module stays dependency-free).
std::string recoveryTrim(const std::string& s);
std::string recoveryEscapeJson(const std::string& s);
std::string recoveryTimestampUtc();
long long recoveryEpochSeconds();
bool recoveryWriteFileAtomic(const std::string& path, const std::string& content);
bool recoveryReadFile(const std::string& path, std::string& out);
bool recoveryFileExists(const std::string& path);
bool recoveryRemoveFile(const std::string& path);
bool recoveryMakeDirectories(const std::string& path);
// ---------------------------------------------------------------------------
// AutotuneRecovery::State owns the recovery bookkeeping for one tuning session.
// ---------------------------------------------------------------------------

class State {
 public:
  typedef std::function<void(const std::string&)> LogFn;

  // tuningDir       - directory that holds checkpoints/blacklist/markers and the
  //                   tune files (e.g. `<homeDataDir>/opencltuning`).
  // profile         - hardware/model fingerprint of the device being tuned.
  // tuneFileName    - base name of the tune file this session produces.
  // safeDefaultText - may be empty; set when the caller can serialize a valid
  //                   default config (validated earlier).
  // logFn           - optional logger callback (wrapped by the caller).
  State(
    const std::string& tuningDir,
    const Profile& profile,
    const std::string& tuneFileName,
    const std::string& safeDefaultText,
    LogFn logFn = LogFn()
  );

  ~State();

  State(const State&) = delete;
  State& operator=(const State&) = delete;

  // ---- Directory / file paths (also used by the Android layer) -------- //
  static std::string checkpointPath(const std::string& tuningDir);
  static std::string blacklistPath(const std::string& tuningDir);
  static std::string recoveryMarkerPath(const std::string& tuningDir);
  static std::string safeDefaultsMarkerPath(const std::string& tuningDir);
  // Per-model checkpoint path (includes model profile hash in filename)
  std::string modelCheckpointPath() const;

  // ---- Status queries ------------------------------------------------ //
  bool isRecoveryRequested() const;      // autotune_recovery.marker exists
  bool isSafeDefaultsRequested() const;  // use_safe_defaults.marker exists
  bool hasLastSuccessful() const;        // checkpoint holds lastSuccessfulConfig
  std::string lastSuccessfulConfig() const;
  bool hasSafeDefault() const;
  std::string safeDefaultConfig() const;
  std::string checkpointStatus() const;
  bool isCheckpointForThisProfile() const;
  std::string checkpointMostRecentAttemptKernel() const;
  std::string checkpointMostRecentAttemptParams() const;
  const Profile& profile() const;
  std::string checkpointLastSuccessfulParams(const std::string& kernel) const;

  // ---- Tuning lifecycle ---------------------------------------------- //
  // Call once before the tuner runs. Records status=in_progress and caches the
  // safe-default config so a crash during this run can still restore something.
  void beginTuning();

  // Called every time the tuner moves to a new kernel family (from the tuner's
  // kernel name we pass down to testAllConfigs).
  void setCurrentKernel(const std::string& kernel);

  // Returns true when the (kernel, params) combination is blacklisted for this
  // profile; the tuner must skip it without benchmarking.
  bool skipIfBlacklisted(const std::string& kernel, const std::string& params);

  // Call right before benchmarking one candidate config. Persisted so a crash
  // right after this point can be attributed to these exact parameters.
  void recordCaseAttempt(const std::string& kernel, const std::string& params, long long caseIndex);

  // Call after a candidate config produced a *valid* benchmark result. The
  // full config text snapshot is what the Android layer restores after a crash.
  void recordCaseSuccess(const std::string& kernel, const std::string& params, long long caseIndex, const std::string& fullConfigText);

  // Call when a candidate config explicitly failed (OpenCL error, timeout,
  // wrong output ...). The parameters are blacklisted immediately so later runs
  // skip them.
  void recordCaseFailure(const std::string& kernel, const std::string& params, long long caseIndex, const std::string& failureType);

  // Call after the whole tuning run completed and the tune file was written.
  void finishTuning(const std::string& tunedConfigText);

  // Save the current config text incrementally so that a crash mid-tune can
  // still restore the best partial config.  Called after each kernel family
  // tune step completes in OpenCLTuner::tune().
  void saveIncrementalConfig(const std::string& configText);

  // ---- Recovery actions (native side of the restore flow) ------------ //
  // Atomically writes the last-successful full config (KataGo tune-file text)
  // into dir + tuneFileName so the next load skips autotuning entirely.
  bool restoreLastSuccessfulTuneFile() const;
  // Same but with the safe default config.
  bool restoreSafeDefaultTuneFile() const;
  void clearRecoveryMarkers() const;

  void log(const std::string& msg) const;

 private:
  void loadFromDisk();
  void saveCheckpoint() const;
  void loadBlacklist();
  void addBlacklistEntry(
    const std::string& kernel,
    const std::string& params,
    long long caseIndex,
    const std::string& failureType,
    const std::string& source,
    const Profile& srcProfile
  ) const;

  std::string tuningDir_;
  Profile profile_;
  std::string tuneFileName_;
  std::string tuneFilePath_;
  std::string safeDefaultText_;
  LogFn logFn_;

  mutable Json checkpoint_;
  mutable Json blacklist_;

  bool checkpointLoaded_;
  bool checkpointMatchesProfile_;
};

}  // namespace OpenCLTuningRecovery

#endif  // NEURALNET_OPENCLTUNING_RECOVERY_H_