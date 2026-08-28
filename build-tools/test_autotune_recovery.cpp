// Standalone host test for the OpenCL autotuning recovery layer.
// Build:
//   g++ -std=c++11 -I <kata>/KataGo-1.17.2/cpp \
//       test_autotune_recovery.cpp \
//       <kata>/KataGo-1.17.2/cpp/neuralnet/opencltuning_recovery.cpp \
//       -o test_autotune_recovery
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <string>

#include "../KataGo-1.17.2/cpp/neuralnet/opencltuning_recovery.h"

using namespace OpenCLTuningRecovery;

static std::string slurp(const std::string& p) {
  std::ifstream in(p.c_str(), std::ios::binary);
  std::ostringstream ss;
  ss << in.rdbuf();
  return ss.str();
}

static int failures = 0;
static void check(bool cond, const char* name) {
  if(cond) printf("PASS %s\n", name);
  else { printf("FAIL %s\n", name); failures++; }
}

static Profile makeProfile() {
  Profile p;
  p.kataGoVersion = "1.17.2";
  p.gpuName = "Adreno (TM) 750";
  p.platformDesc = "clvk platform";
  p.deviceName = "Adreno (TM) 750";
  p.openclVersion = "OpenCL C 1.2";
  p.clDriverVersion = "Driver Build: f115f6bba1";
  p.clVendor = "clvk";
  p.vulkanDriver = "Vulkan 1.3.128";
  p.modelVersion = 8;
  p.trunkNumChannels = 128;
  p.midNumChannels = 128;
  p.regularNumChannels = 96;
  p.gpoolNumChannels = 32;
  p.transformerHeadDim = 0;
  p.transformerVHeadDim = 0;
  p.transformerNumHeads = 0;
  p.transformerNumKVHeads = 0;
  p.transformerFFNChannels = 0;
  p.nnXLen = 23;
  p.nnYLen = 23;
  p.batchSize = 8;
  p.androidModel = "PJD110";
  p.androidManufacturer = "OnePlus";
  p.androidDevice = "RE5C4FL1";
  return p;
}

// ---------------------------------------------------------------------------
// JSON round-trip + Profile matching
// ---------------------------------------------------------------------------
static void testJsonAndProfile() {
  {
    Json j(Json::Type::Object);
    j["schemaVersion"] = Json::number((long long)1);
    j["status"] = Json::string("in_progress");
    j["count"] = Json::number((long long)42);
    j["fp"] = Json::number(3.25);
    j["flag"] = Json::boolean(true);
    Json arr(Json::Type::Array);
    arr.push_back(Json::string("a\"b\\c"));
    arr.push_back(Json::number((long long)7));
    j["list"] = arr;
    std::string dump = j.dump();
    printf("  json dump: %s\n", dump.c_str());
    Json back = Json::parse(dump);
    check(back.getInt("schemaVersion", -1) == 1, "json: schemaVersion round-trip");
    check(back.getString("status", "") == "in_progress", "json: status round-trip");
    check(back.getInt("count", -1) == 42, "json: count round-trip");
    check(back.find("fp")->asNumber() == 3.25, "json: fp round-trip");
    check(back.find("flag")->asBool() == true, "json: bool round-trip");
    check(back.find("list")->at(0).asString() == "a\"b\\c", "json: escaped string");
    check(back.find("list")->at(1).asInt() == 7, "json: array int");
    bool threw = false;
    try { Json::parse("{\"a\": 1, broken"); } catch(const std::exception&) { threw = true; }
    check(threw, "json: corrupt input throws");
    Json o(Json::Type::Object);
    o["profile"]["gpu"] = Json::string("Adreno 750");
    o["profile"]["ok"] = Json::boolean(true);
    Json op = Json::parse(o.dump());
    check(op.find("profile")->getString("gpu", "") == "Adreno 750", "json: nested object");
  }
  {
    Profile a = makeProfile();
    Profile b = a;
    check(Profile::profilesMatch(a, b), "profile: identical matches");
    Profile c = a;
    c.clDriverVersion = "Driver Build: DIFFERENT";
    check(!Profile::profilesMatch(a, c), "profile: different driver rejected");
    Profile d = a;
    d.gpuName = "Mali-G720";
    check(!Profile::profilesMatch(a, d), "profile: different gpu rejected");
    Profile e = a;
    e.vulkanDriver.clear();
    check(Profile::profilesMatch(a, e), "profile: unknown field on one side tolerated");
    Profile empty;
    check(Profile::profilesMatch(empty, a), "profile: empty profile matches anything");
  }
}
// ---------------------------------------------------------------------------
// State / checkpoint / blacklist / recovery
// ---------------------------------------------------------------------------
static void testState() {
  std::string dir = "/tmp/autotune_recovery_test";
  system("rm -rf /tmp/autotune_recovery_test");
  Profile prof = makeProfile();
  std::string tuneFile = "tune13_gpuAdrenoTM750_x23_y23_c128_mv8.txt";

  {
    State st(dir, prof, tuneFile, "SAFE-DEFAULT-CONFIG", [](const std::string& m){ printf("  [native]%s\n", m.c_str()); });
    st.beginTuning();
    check(recoveryFileExists(State::checkpointPath(dir)), "state: checkpoint created");
    check(st.checkpointStatus() == "in_progress", "state: status in_progress");

    st.recordCaseFailure("xGemm", "MWG=8 NWG=8 KWG=8", 3, "CL_ERROR");
    check(st.skipIfBlacklisted("xGemm", "MWG=8 NWG=8 KWG=8"), "blacklist: skip hit");
    check(!st.skipIfBlacklisted("xGemm", "MWG=16 NWG=16 KWG=8"), "blacklist: no false hit");

    st.recordCaseAttempt("xGemm", "MWG=16 NWG=16 KWG=8", 5);
    st.recordCaseSuccess("xGemm", "MWG=16 NWG=16 KWG=8", 5, "FULLCONFIG5");
    st.recordCaseSuccess("transform", "transLocalSize0=1", 1, "FULLCONFIG5B");
    st.finishTuning("FINALCONFIG");
    check(st.checkpointStatus() == "completed", "state: status completed after finish");
    check(st.isRecoveryRequested() == false, "state: no recovery marker by default");
  }

  {
    State st2(dir, prof, tuneFile, "", NULL);
    check(st2.isCheckpointForThisProfile(), "state: checkpoint re-read matches profile");
    check(st2.checkpointStatus() == "completed", "state: re-read status completed");
    check(st2.hasLastSuccessful(), "state: re-read has lastSuccessful");
    check(st2.checkpointLastSuccessfulParams("xGemm") == "MWG=16 NWG=16 KWG=8", "state: per-kernel lastSuccessful");
    check(st2.hasSafeDefault(), "state: safe default available after completion");
  }

  {
    State st3(dir, prof, tuneFile, "", NULL);
    std::ofstream m(State::recoveryMarkerPath(dir).c_str());
    m << "crash\n"; m.close();
    check(st3.isRecoveryRequested(), "recovery: marker detected");
    check(st3.restoreLastSuccessfulTuneFile(), "recovery: restored tune file");
    std::string restored = slurp(dir + "/" + tuneFile);
    check(restored == "FINALCONFIG", "recovery: restored content correct");
    st3.clearRecoveryMarkers();
    check(!recoveryFileExists(State::recoveryMarkerPath(dir)), "recovery: marker removed");
  }

  {
    Profile other = makeProfile();
    other.gpuName = "Mali-G720";
    State st4(dir, other, tuneFile, "", NULL);
    check(st4.isCheckpointForThisProfile() == false, "recovery: other-profile checkpoint ignored");
  }

  {
    State st5(dir, prof, tuneFile, "SAFE-DEFAULT-CONFIG", NULL);
    std::ofstream m(State::safeDefaultsMarkerPath(dir).c_str());
    m << "max\n"; m.close();
    check(st5.isSafeDefaultsRequested(), "safe: marker detected");
    check(st5.restoreSafeDefaultTuneFile(), "safe: safe default restored");
    std::string restored = slurp(dir + "/" + tuneFile);
    check(restored == "SAFE-DEFAULT-CONFIG", "safe: restored safe default content");
    st5.clearRecoveryMarkers();
  }
}

static void testCorruptFiles() {
  std::string dir = "/tmp/autotune_recovery_test_corrupt";
  system("rm -rf /tmp/autotune_recovery_test_corrupt");
  system("mkdir -p /tmp/autotune_recovery_test_corrupt");
  {
    std::ofstream c(State::checkpointPath(dir).c_str());
    c << "{ this is not json";
    c.close();
  }
  Profile prof = makeProfile();
  State st(dir, prof, "tune13_x.txt", "", NULL);
  check(!st.isCheckpointForThisProfile(), "corrupt: checkpoint rejected safely");
  st.beginTuning();
  check(st.checkpointStatus() == "in_progress", "corrupt: fresh checkpoint usable after reject");
}

int main(int argc, char** argv) {
  (void)argc; (void)argv;
  printf("=== OpenCLTuningRecovery unit tests ===\n");
  testJsonAndProfile();
  testState();
  testCorruptFiles();
  printf(failures == 0 ? "ALL TESTS PASSED\n" : "TESTS FAILED: %d\n", failures);
  return failures == 0 ? 0 : 1;
}