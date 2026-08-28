#include "../neuralnet/opencltuning_recovery.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <algorithm>
#include <cmath>

#ifdef _WIN32
#include <io.h>
#include <process.h>
#define RECOVERY_GETPID() ((long)_getpid())
#define RECOVERY_FSYNC(fd) (_commit(fd))
#else
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#define RECOVERY_GETPID() ((long)getpid())
#define RECOVERY_FSYNC(fd) ::fsync(fd)
#endif

namespace OpenCLTuningRecovery {

using std::string;

// ---------------------------------------------------------------------------
// Json
// ---------------------------------------------------------------------------

Json::Json() : type_(Type::Null), bool_(false), int_(0), num_(0.0), isInteger_(true) {}

Json::Json(Type type)
  : type_(type), bool_(false), int_(0), num_(0.0), isInteger_(true) {}

Json Json::boolean(bool v) {
  Json j(Type::Bool);
  j.bool_ = v;
  return j;
}

Json Json::number(long long v) {
  Json j(Type::Number);
  j.int_ = v;
  j.num_ = (double)v;
  j.isInteger_ = true;
  return j;
}

Json Json::number(double v) {
  Json j(Type::Number);
  j.int_ = (long long)v;
  j.num_ = v;
  j.isInteger_ = (j.int_ == (long long)v);
  return j;
}

Json Json::string(const std::string& v) {
  Json j(Type::String);
  j.str_ = v;
  return j;
}

Json::Type Json::type() const { return type_; }
bool Json::isNull() const { return type_ == Type::Null; }
bool Json::isBool() const { return type_ == Type::Bool; }
bool Json::isNumber() const { return type_ == Type::Number; }
bool Json::isString() const { return type_ == Type::String; }
bool Json::isArray() const { return type_ == Type::Array; }
bool Json::isObject() const { return type_ == Type::Object; }

bool Json::asBool() const { return bool_; }
long long Json::asInt() const { return int_; }
double Json::asNumber() const { return num_; }
const std::string& Json::asString() const { return str_; }

Json& Json::operator[](const std::string& key) {
  if(type_ != Type::Object) {
    type_ = Type::Object;
    bool_ = false; int_ = 0; num_ = 0.0; isInteger_ = true;
    str_.clear();
    arr_.clear();
    obj_.clear();
  }
  for(size_t i = 0; i < obj_.size(); i++) {
    if(obj_[i].first == key)
      return obj_[i].second;
  }
  obj_.push_back(std::make_pair(key, Json()));
  return obj_.back().second;
}

const Json* Json::find(const std::string& key) const {
  if(type_ != Type::Object) return NULL;
  for(size_t i = 0; i < obj_.size(); i++) {
    if(obj_[i].first == key)
      return &obj_[i].second;
  }
  return NULL;
}

Json* Json::find(const std::string& key) {
  if(type_ != Type::Object) return NULL;
  for(size_t i = 0; i < obj_.size(); i++) {
    if(obj_[i].first == key)
      return &obj_[i].second;
  }
  return NULL;
}

bool Json::has(const std::string& key) const {
  return find(key) != NULL;
}

std::vector<std::string> Json::keys() const {
  std::vector<std::string> result;
  if(type_ != Type::Object) return result;
  for(size_t i = 0; i < obj_.size(); i++)
    result.push_back(obj_[i].first);
  return result;
}

std::size_t Json::size() const {
  if(type_ == Type::Array) return arr_.size();
  if(type_ == Type::Object) return obj_.size();
  return 0;
}

void Json::push_back(const Json& v) {
  if(type_ != Type::Array) {
    type_ = Type::Array;
    bool_ = false; int_ = 0; num_ = 0.0; isInteger_ = true;
    str_.clear();
    obj_.clear();
    arr_.clear();
  }
  arr_.push_back(v);
}

const Json& Json::at(std::size_t i) const {
  if(type_ != Type::Array || i >= arr_.size())
    throw std::runtime_error("Json::at: index out of range");
  return arr_[i];
}

std::string Json::getString(const std::string& key, const std::string& def) const {
  const Json* j = find(key);
  if(j == NULL || !j->isString()) return def;
  return j->asString();
}

long long Json::getInt(const std::string& key, long long def) const {
  const Json* j = find(key);
  if(j == NULL || !j->isNumber()) return def;
  return j->asInt();
}
static void dumpTo(string& out, const Json& j) {
  switch(j.type()) {
    case Json::Type::Null: out += "null"; break;
    case Json::Type::Bool: out += j.asBool() ? "true" : "false"; break;
    case Json::Type::Number: {
      char buf[64];
      if(j.asNumber() == (double)j.asInt() && std::fabs(j.asNumber()) < 1e15) {
        std::snprintf(buf, sizeof(buf), "%lld", (long long)j.asInt());
        out += buf;
      }
      else {
        std::snprintf(buf, sizeof(buf), "%.17g", j.asNumber());
        out += buf;
      }
      break;
    }
    case Json::Type::String: {
      out += '"';
      const std::string& s = j.asString();
      for(size_t i = 0; i < s.size(); i++) {
        unsigned char c = (unsigned char)s[i];
        switch(c) {
          case '"': out += "\\\""; break;
          case '\\': out += "\\\\"; break;
          case '\n': out += "\\n"; break;
          case '\r': out += "\\r"; break;
          case '\t': out += "\\t"; break;
          default:
            if(c < 0x20) {
              char buf[8];
              std::snprintf(buf, sizeof(buf), "\\u%04x", (int)c);
              out += buf;
            }
            else {
              out += (char)c;
            }
        }
      }
      out += '"';
      break;
    }
    case Json::Type::Array: {
      out += '[';
      for(size_t i = 0; i < j.size(); i++) {
        if(i > 0) out += ',';
        dumpTo(out, j.at(i));
      }
      out += ']';
      break;
    }
    case Json::Type::Object: {
      out += '{';
      std::vector<std::string> keys = j.keys();
      for(size_t i = 0; i < keys.size(); i++) {
        if(i > 0) out += ',';
        Json keyObj = Json::string(keys[i]);
        dumpTo(out, keyObj);
        out += ':';
        const Json* v = j.find(keys[i]);
        dumpTo(out, *v);
      }
      out += '}';
      break;
    }
  }
}

std::string Json::dump() const {
  std::string out;
  dumpTo(out, *this);
  return out;
}
// ---- JSON parser -------------------------------------------------------- //

namespace {

struct JsonParser {
  const std::string& text;
  size_t pos;

  explicit JsonParser(const std::string& t) : text(t), pos(0) {}

  void skipWs() {
    while(pos < text.size()) {
      char c = text[pos];
      if(c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
      else break;
    }
  }

  bool eof() { skipWs(); return pos >= text.size(); }

  char peek() {
    skipWs();
    if(pos >= text.size()) throw std::runtime_error("Unexpected end of JSON");
    return text[pos];
  }

  void expect(char c) {
    if(peek() != c)
      throw std::runtime_error(std::string("Expected '") + c + std::string("' in JSON"));
    pos++;
  }

  Json parse() {
    Json v = parseValue();
    if(!eof())
      throw std::runtime_error("Trailing characters after JSON value");
    return v;
  }

  Json parseValue() {
    char c = peek();
    switch(c) {
      case '{': return parseObject();
      case '[': return parseArray();
      case '"': return Json::string(parseString());
      case 't':
      case 'f':
        return parseBoolLiteral();
      case 'n':
        return parseNullLiteral();
      default:
        if(c == '-' || (c >= '0' && c <= '9'))
          return parseNumber();
        throw std::runtime_error(std::string("Unexpected character in JSON: ") + c);
    }
  }

  std::string parseString() {
    expect('"');
    std::string out;
    while(true) {
      if(pos >= text.size())
        throw std::runtime_error("Unterminated string in JSON");
      unsigned char c = (unsigned char)text[pos++];
      if(c == '"') break;
      if(c == '\\') {
        if(pos >= text.size()) throw std::runtime_error("Unterminated escape in JSON");
        char e = text[pos++];
        switch(e) {
          case '"': out += '"'; break;
          case '\\': out += '\\'; break;
          case '/': out += '/'; break;
          case 'b': out += '\b'; break;
          case 'f': out += '\f'; break;
          case 'n': out += '\n'; break;
          case 'r': out += '\r'; break;
          case 't': out += '\t'; break;
          case 'u': {
            if(pos + 4 > text.size()) throw std::runtime_error("Bad \\u escape in JSON");
            unsigned int cp = 0;
            for(int i = 0; i < 4; i++) {
              char h = text[pos+i];
              cp <<= 4;
              if(h >= '0' && h <= '9') cp |= (unsigned int)(h - '0');
              else if(h >= 'a' && h <= 'f') cp |= (unsigned int)(h - 'a' + 10);
              else if(h >= 'A' && h <= 'F') cp |= (unsigned int)(h - 'A' + 10);
              else throw std::runtime_error("Bad \\u hex digit in JSON");
            }
            pos += 4;
            // Surrogate pair.
            if(cp >= 0xD800 && cp <= 0xDBFF && pos + 6 <= text.size() &&
               text[pos] == '\\' && text[pos+1] == 'u') {
              unsigned int lo = 0;
              for(int i = 0; i < 4; i++) {
                char h = text[pos+2+i];
                lo <<= 4;
                if(h >= '0' && h <= '9') lo |= (unsigned int)(h - '0');
                else if(h >= 'a' && h <= 'f') lo |= (unsigned int)(h - 'a' + 10);
                else if(h >= 'A' && h <= 'F') lo |= (unsigned int)(h - 'A' + 10);
                else throw std::runtime_error("Bad \\u hex digit in JSON");
              }
              if(lo >= 0xDC00 && lo <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                pos += 6;
              }
            }
            // Encode UTF-8.
            if(cp < 0x80) out += (char)cp;
            else if(cp < 0x800) {
              out += (char)(0xC0 | (cp >> 6));
              out += (char)(0x80 | (cp & 0x3F));
            }
            else if(cp < 0x10000) {
              out += (char)(0xE0 | (cp >> 12));
              out += (char)(0x80 | ((cp >> 6) & 0x3F));
              out += (char)(0x80 | (cp & 0x3F));
            }
            else {
              out += (char)(0xF0 | (cp >> 18));
              out += (char)(0x80 | ((cp >> 12) & 0x3F));
              out += (char)(0x80 | ((cp >> 6) & 0x3F));
              out += (char)(0x80 | (cp & 0x3F));
            }
            break;
          }
          default:
            throw std::runtime_error("Unknown escape in JSON");
        }
        continue;
      }
      out += (char)c;
    }
    return out;
  }

  Json parseObject() {
    expect('{');
    Json obj(Json::Type::Object);
    skipWs();
    if(peek() == '}') { pos++; return obj; }
    while(true) {
      if(peek() != '"')
        throw std::runtime_error("Expected string key in JSON object");
      std::string key = parseString();
      skipWs();
      expect(':');
      obj[key] = parseValue();
      skipWs();
      char c = peek();
      if(c == ',') { pos++; continue; }
      if(c == '}') { pos++; break; }
      throw std::runtime_error("Expected ',' or '}' in JSON object");
    }
    return obj;
  }

  Json parseArray() {
    expect('[');
    Json arr(Json::Type::Array);
    skipWs();
    if(peek() == ']') { pos++; return arr; }
    while(true) {
      arr.push_back(parseValue());
      skipWs();
      char c = peek();
      if(c == ',') { pos++; continue; }
      if(c == ']') { pos++; break; }
      throw std::runtime_error("Expected ',' or ']' in JSON array");
    }
    return arr;
  }

  Json parseNumber() {
    size_t start = pos;
    if(pos < text.size() && text[pos] == '-') pos++;
    bool sawDigit = false;
    while(pos < text.size() && text[pos] >= '0' && text[pos] <= '9') { pos++; sawDigit = true; }
    if(!sawDigit) throw std::runtime_error("Invalid number in JSON");
    bool isFp = false;
    if(pos < text.size() && text[pos] == '.') {
      isFp = true;
      pos++;
      bool any = false;
      while(pos < text.size() && text[pos] >= '0' && text[pos] <= '9') { pos++; any = true; }
      if(!any) throw std::runtime_error("Invalid number in JSON");
    }
    if(pos < text.size() && (text[pos] == 'e' || text[pos] == 'E')) {
      isFp = true;
      pos++;
      if(pos < text.size() && (text[pos] == '+' || text[pos] == '-')) pos++;
      bool any = false;
      while(pos < text.size() && text[pos] >= '0' && text[pos] <= '9') { pos++; any = true; }
      if(!any) throw std::runtime_error("Invalid number in JSON");
    }
    std::string numStr = text.substr(start, pos - start);
    if(isFp)
      return Json::number(std::strtod(numStr.c_str(), NULL));
    return Json::number((long long)std::strtoll(numStr.c_str(), NULL, 10));
  }

  Json parseBoolLiteral() {
    if(text.compare(pos, 4, "true") == 0) { pos += 4; return Json::boolean(true); }
    if(text.compare(pos, 5, "false") == 0) { pos += 5; return Json::boolean(false); }
    throw std::runtime_error("Invalid literal in JSON");
  }

  Json parseNullLiteral() {
    if(text.compare(pos, 4, "null") == 0) { pos += 4; return Json(); }
    throw std::runtime_error("Invalid literal in JSON");
  }
};

}  // namespace

Json Json::parse(const std::string& text) {
  JsonParser parser(text);
  return parser.parse();
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

Profile::Profile()
  : modelVersion(0), trunkNumChannels(0), midNumChannels(0), regularNumChannels(0),
    gpoolNumChannels(0), transformerHeadDim(0), transformerVHeadDim(0),
    transformerNumHeads(0), transformerNumKVHeads(0), transformerFFNChannels(0),
    nnXLen(0), nnYLen(0), batchSize(0) {}

Json Profile::toJson() const {
  Json j(Json::Type::Object);
  j["kataGoVersion"] = Json::string(kataGoVersion);
  j["gpuName"] = Json::string(gpuName);
  j["platformDesc"] = Json::string(platformDesc);
  j["deviceName"] = Json::string(deviceName);
  j["openclVersion"] = Json::string(openclVersion);
  j["clDriverVersion"] = Json::string(clDriverVersion);
  j["clVendor"] = Json::string(clVendor);
  j["vulkanDriver"] = Json::string(vulkanDriver);
  j["modelVersion"] = Json::number(modelVersion);
  j["trunkNumChannels"] = Json::number(trunkNumChannels);
  j["midNumChannels"] = Json::number(midNumChannels);
  j["regularNumChannels"] = Json::number(regularNumChannels);
  j["gpoolNumChannels"] = Json::number(gpoolNumChannels);
  j["transformerHeadDim"] = Json::number(transformerHeadDim);
  j["transformerVHeadDim"] = Json::number(transformerVHeadDim);
  j["transformerNumHeads"] = Json::number(transformerNumHeads);
  j["transformerNumKVHeads"] = Json::number(transformerNumKVHeads);
  j["transformerFFNChannels"] = Json::number(transformerFFNChannels);
  j["nnXLen"] = Json::number(nnXLen);
  j["nnYLen"] = Json::number(nnYLen);
  j["batchSize"] = Json::number(batchSize);
  j["androidModel"] = Json::string(androidModel);
  j["androidManufacturer"] = Json::string(androidManufacturer);
  j["androidDevice"] = Json::string(androidDevice);
  return j;
}

Profile Profile::fromJson(const Json& j) {
  Profile p;
  if(!j.isObject()) return p;
  p.kataGoVersion = j.getString("kataGoVersion", "");
  p.gpuName = j.getString("gpuName", "");
  p.platformDesc = j.getString("platformDesc", "");
  p.deviceName = j.getString("deviceName", "");
  p.openclVersion = j.getString("openclVersion", "");
  p.clDriverVersion = j.getString("clDriverVersion", "");
  p.clVendor = j.getString("clVendor", "");
  p.vulkanDriver = j.getString("vulkanDriver", "");
  p.modelVersion = j.getInt("modelVersion", 0);
  p.trunkNumChannels = j.getInt("trunkNumChannels", 0);
  p.midNumChannels = j.getInt("midNumChannels", 0);
  p.regularNumChannels = j.getInt("regularNumChannels", 0);
  p.gpoolNumChannels = j.getInt("gpoolNumChannels", 0);
  p.transformerHeadDim = j.getInt("transformerHeadDim", 0);
  p.transformerVHeadDim = j.getInt("transformerVHeadDim", 0);
  p.transformerNumHeads = j.getInt("transformerNumHeads", 0);
  p.transformerNumKVHeads = j.getInt("transformerNumKVHeads", 0);
  p.transformerFFNChannels = j.getInt("transformerFFNChannels", 0);
  p.nnXLen = j.getInt("nnXLen", 0);
  p.nnYLen = j.getInt("nnYLen", 0);
  p.batchSize = j.getInt("batchSize", 0);
  p.androidModel = j.getString("androidModel", "");
  p.androidManufacturer = j.getString("androidManufacturer", "");
  p.androidDevice = j.getString("androidDevice", "");
  return p;
}

// Every field that is present on BOTH sides must be equal. Fields only one side
// knows are ignored, which keeps a blacklist entry recorded before any
// checkpoint (fewer fields known) still effective on the device that created it.
bool Profile::profilesMatch(const Profile& a, const Profile& b) {
  Json ja = a.toJson();
  Json jb = b.toJson();
  std::vector<std::string> keys = ja.keys();
  for(size_t i = 0; i < keys.size(); i++) {
    const Json* bv = jb.find(keys[i]);
    if(bv == NULL) continue;  // b does not know this field
    const Json* av = ja.find(keys[i]);
    if(av == NULL) continue;
    if(av->isString() && bv->isString()) {
      if(!av->asString().empty() && !bv->asString().empty() && av->asString() != bv->asString()) return false;
    }
    else if(av->isNumber() && bv->isNumber()) {
      if(av->asNumber() != 0 && bv->asNumber() != 0 && av->asNumber() != bv->asNumber()) return false;
    }
    else if(av->isNull() && bv->isString() && !bv->asString().empty()) {
      return false;
    }
    else if(bv->isNull() && av->isString() && !av->asString().empty()) {
      return false;
    }
  }
  return true;
}
// ---------------------------------------------------------------------------
// File / timestamp helpers
// ---------------------------------------------------------------------------

std::string recoveryTrim(const std::string& s) {
  size_t start = 0;
  size_t end = s.size();
  while(start < end) {
    char c = s[start];
    if(c == ' ' || c == '\t' || c == '\r' || c == '\n') start++;
    else break;
  }
  while(end > start) {
    char c = s[end-1];
    if(c == ' ' || c == '\t' || c == '\r' || c == '\n') end--;
    else break;
  }
  return s.substr(start, end - start);
}

std::string recoveryEscapeJson(const std::string& s) {
  std::string out;
  for(size_t i = 0; i < s.size(); i++) {
    unsigned char c = (unsigned char)s[i];
    if(c == '"') out += "\\\"";
    else if(c == '\\') out += "\\\\";
    else if(c == '\n') out += "\\n";
    else if(c == '\r') out += "\\r";
    else if(c == '\t') out += "\\t";
    else if(c < 0x20) {
      char buf[8];
      std::snprintf(buf, sizeof(buf), "\\u%04x", (int)c);
      out += buf;
    }
    else out += (char)c;
  }
  return out;
}

long long recoveryEpochSeconds() {
  return (long long)std::time(NULL);
}

std::string recoveryTimestampUtc() {
  std::time_t now = std::time(NULL);
#ifdef _WIN32
  struct tm tmv;
  gmtime_s(&tmv, &now);
#else
  struct tm tmv;
  gmtime_r(&now, &tmv);
#endif
  char buf[64];
  std::snprintf(buf, sizeof(buf), "%04d-%02d-%02dT%02d:%02d:%02dZ",
    tmv.tm_year + 1900, tmv.tm_mon + 1, tmv.tm_mday,
    tmv.tm_hour, tmv.tm_min, tmv.tm_sec);
  return std::string(buf);
}

bool recoveryFileExists(const std::string& path) {
  std::ifstream in(path.c_str(), std::ios::binary);
  return in.good();
}

bool recoveryReadFile(const std::string& path, std::string& out) {
  std::ifstream in(path.c_str(), std::ios::binary);
  if(!in.good()) return false;
  std::ostringstream ss;
  ss << in.rdbuf();
  out = ss.str();
  return true;
}

bool recoveryRemoveFile(const std::string& path) {
  if(!recoveryFileExists(path)) return true;
#ifdef _WIN32
  return std::remove(path.c_str()) == 0;
#else
  return ::unlink(path.c_str()) == 0;
#endif
}

bool recoveryMakeDirectories(const std::string& path) {
#ifdef _WIN32
  // Windows: tuning directory is created by KataGo's MakeDir, nothing extra needed.
  return true;
#else
  if(path.empty()) return true;
  std::string cur;
  size_t start = 0;
  if(path[0] == '/') {
    cur = "/";
    start = 1;
  }
  for(size_t i = start; i < path.size(); i++) {
    if(path[i] == '/') continue;
    size_t j = i;
    while(j < path.size() && path[j] != '/') j++;
    std::string comp = cur + path.substr(i, j - i);
    if(!recoveryFileExists(comp)) {
      if(::mkdir(comp.c_str(), 0755) != 0 && errno != EEXIST) return false;
    }
    cur = comp + "/";
    i = j;
  }
  return true;
#endif
}

bool recoveryWriteFileAtomic(const std::string& path, const std::string& content) {
#ifdef _WIN32
  // Windows: no portable fsync-on-file; write via tmp + rename for atomicity.
  std::string tmp = path + ".tmp";
  {
    std::ofstream out(tmp.c_str(), std::ios::binary | std::ios::trunc);
    if(!out.good()) return false;
    out.write(content.data(), (std::streamsize)content.size());
    out.flush();
    out.close();
    if(!out.good()) return false;
  }
  if(std::rename(tmp.c_str(), path.c_str()) != 0) {
    std::remove(tmp.c_str());
    return false;
  }
  return true;
#else
  std::string tmp = path + ".tmp." + std::to_string(RECOVERY_GETPID());
  int fd = ::open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
  if(fd < 0) return false;
  bool ok = true;
  std::size_t off = 0;
  while(off < content.size()) {
    ssize_t n = ::write(fd, content.data() + off, content.size() - off);
    if(n < 0) {
      if(errno == EINTR) continue;
      ok = false;
      break;
    }
    off += (std::size_t)n;
  }
  if(ok && RECOVERY_FSYNC(fd) != 0) ok = false;
  if(::close(fd) != 0) ok = false;
  if(ok && ::rename(tmp.c_str(), path.c_str()) != 0) {
    ok = false;
    ::unlink(tmp.c_str());
  }
  if(ok) {
    // fsync the parent directory so the rename itself is durable.
    std::string dir = path;
    std::size_t slash = dir.find_last_of('/');
    if(slash != std::string::npos) dir = dir.substr(0, slash);
    else dir = ".";
    int dfd = ::open(dir.c_str(), O_RDONLY);
    if(dfd >= 0) {
      (void)::fsync(dfd);
      ::close(dfd);
    }
  }
  return ok;
#endif
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

State::State(
  const std::string& tuningDir,
  const Profile& profile,
  const std::string& tuneFileName,
  const std::string& safeDefaultText,
  LogFn logFn
) : tuningDir_(tuningDir),
    profile_(profile),
    tuneFileName_(tuneFileName),
    safeDefaultText_(safeDefaultText),
    logFn_(logFn),
    checkpointLoaded_(false),
    checkpointMatchesProfile_(false) {
  tuneFilePath_ = tuningDir_ + "/" + tuneFileName_;
  if(recoveryMakeDirectories(tuningDir_)) {
    loadBlacklist();
    loadFromDisk();
  }
}

State::~State() {}

void State::log(const std::string& msg) const {
  if(logFn_) logFn_("[AutotuneRecovery] " + msg);
}

std::string State::checkpointPath(const std::string& dir) { return dir + "/" + CHECKPOINT_FILENAME; }
std::string State::blacklistPath(const std::string& dir) { return dir + "/" + BLACKLIST_FILENAME; }
std::string State::recoveryMarkerPath(const std::string& dir) { return dir + "/" + RECOVERY_MARKER_FILENAME; }
std::string State::safeDefaultsMarkerPath(const std::string& dir) { return dir + "/" + SAFE_DEFAULTS_MARKER_FILENAME; }

std::string State::modelCheckpointPath() const {
  // Build a deterministic hash from model-specific profile fields so each
  // model gets its own checkpoint file.  This prevents one model's
  // beginTuning() from overwriting another model's lastSuccessfulConfig.
  std::ostringstream oss;
  oss << profile_.modelVersion << "_"
      << profile_.trunkNumChannels << "_"
      << profile_.midNumChannels << "_"
      << profile_.regularNumChannels << "_"
      << profile_.gpoolNumChannels << "_"
      << profile_.transformerHeadDim << "_"
      << profile_.transformerVHeadDim << "_"
      << profile_.transformerNumHeads << "_"
      << profile_.transformerNumKVHeads << "_"
      << profile_.transformerFFNChannels;
  std::string raw = oss.str();
  // Simple hash (djb2) to produce a compact hex suffix
  unsigned long h = 5381;
  for(size_t i = 0; i < raw.size(); i++)
    h = ((h << 5) + h) + (unsigned char)raw[i];
  char buf[64];
  std::snprintf(buf, sizeof(buf), "tuning_checkpoint_%08lx.json", (unsigned long)(h & 0xFFFFFFFF));
  return tuningDir_ + "/" + buf;
}

const Profile& State::profile() const { return profile_; }

bool State::isRecoveryRequested() const {
  return recoveryFileExists(recoveryMarkerPath(tuningDir_));
}

bool State::isSafeDefaultsRequested() const {
  return recoveryFileExists(safeDefaultsMarkerPath(tuningDir_));
}

bool State::hasLastSuccessful() const {
  const Json* v = checkpoint_.find("lastSuccessfulConfig");
  return v != NULL && v->isString() && !v->asString().empty();
}

std::string State::lastSuccessfulConfig() const {
  return checkpoint_.getString("lastSuccessfulConfig", "");
}

bool State::hasSafeDefault() const {
  std::string cfg = safeDefaultConfig();
  return !cfg.empty();
}

std::string State::safeDefaultConfig() const {
  if(!safeDefaultText_.empty()) return safeDefaultText_;
  return checkpoint_.getString("safeDefaultConfig", "");
}

std::string State::checkpointStatus() const {
  return checkpoint_.getString("status", "");
}

bool State::isCheckpointForThisProfile() const {
  return checkpointLoaded_ && checkpointMatchesProfile_;
}

std::string State::checkpointMostRecentAttemptKernel() const {
  const Json* la = checkpoint_.find("lastAttempted");
  if(la == NULL || !la->isObject()) return "";
  return la->getString("kernel", "");
}

std::string State::checkpointMostRecentAttemptParams() const {
  const Json* la = checkpoint_.find("lastAttempted");
  if(la == NULL || !la->isObject()) return "";
  return la->getString("params", "");
}

std::string State::checkpointLastSuccessfulParams(const std::string& kernel) const {
  const Json* ls = checkpoint_.find("lastSuccessful");
  if(ls == NULL || !ls->isObject()) return "";
  const Json* p = ls->find(kernel);
  if(p == NULL || !p->isString()) return "";
  return p->asString();
}

void State::loadFromDisk() {
  std::string txt;
  std::string cpPath = modelCheckpointPath();
  bool fromLegacy = false;
  // Try per-model checkpoint first; fall back to legacy single file
  if(!recoveryReadFile(cpPath, txt)) {
    std::string legacyPath = checkpointPath(tuningDir_);
    if(recoveryReadFile(legacyPath, txt)) {
      cpPath = legacyPath;
      fromLegacy = true;
    } else {
      return;
    }
  }
  try {
    Json j = Json::parse(txt);
    if(j.isObject() && j.getInt("schemaVersion", -1) == SCHEMA_VERSION) {
      checkpoint_ = j;
      checkpointLoaded_ = true;
      const Json* p = checkpoint_.find("profile");
      if(p != NULL && p->isObject()) {
        Profile stored = Profile::fromJson(*p);
        checkpointMatchesProfile_ = Profile::profilesMatch(stored, profile_);
      }
      // Migrate legacy checkpoint to per-model path if profile matches
      if(fromLegacy && checkpointMatchesProfile_) {
        saveCheckpoint();
        log("Migrated legacy checkpoint to per-model path");
      }
    }
    else {
      log("Ignoring checkpoint with wrong schema: " + cpPath);
      checkpoint_ = Json(Json::Type::Object);
      checkpoint_["schemaVersion"] = Json::number(SCHEMA_VERSION);
    }
  }
  catch(const std::exception& e) {
    log("Ignoring corrupt checkpoint (" + std::string(e.what()) + "): " + cpPath);
    checkpoint_ = Json(Json::Type::Object);
    checkpoint_["schemaVersion"] = Json::number(SCHEMA_VERSION);
  }
}

void State::saveCheckpoint() const {
  checkpoint_["timestamp"] = Json::string(recoveryTimestampUtc());
  std::string txt = checkpoint_.dump();
  std::string cpPath = modelCheckpointPath();
  if(!recoveryWriteFileAtomic(cpPath, txt))
    log("WARNING: failed to write checkpoint " + cpPath);
}

void State::loadBlacklist() {
  blacklist_ = Json(Json::Type::Object);
  blacklist_["schemaVersion"] = Json::number(SCHEMA_VERSION);
  blacklist_["entries"] = Json(Json::Type::Array);
  std::string txt;
  if(!recoveryReadFile(blacklistPath(tuningDir_), txt)) return;
  try {
    Json j = Json::parse(txt);
    if(j.isObject() && j.getInt("schemaVersion", -1) == SCHEMA_VERSION && j.find("entries") != NULL)
      blacklist_ = j;
    else
      log("Ignoring blacklist with wrong schema: " + blacklistPath(tuningDir_));
  }
  catch(const std::exception& e) {
    log("Ignoring corrupt blacklist (" + std::string(e.what()) + "): " + blacklistPath(tuningDir_));
  }
}

void State::addBlacklistEntry(
  const std::string& kernel,
  const std::string& params,
  long long caseIndex,
  const std::string& failureType,
  const std::string& source,
  const Profile& srcProfile
) const {
  Json entry(Json::Type::Object);
  entry["profile"] = srcProfile.toJson();
  entry["kernel"] = Json::string(kernel);
  entry["params"] = Json::string(params);
  entry["caseIndex"] = Json::number(caseIndex);
  entry["failureType"] = Json::string(failureType);
  entry["source"] = Json::string(source);
  entry["timestamp"] = Json::string(recoveryTimestampUtc());
  entry["timestampEpoch"] = Json::number(recoveryEpochSeconds());
  Json* entries = blacklist_.find("entries");
  if(entries == NULL || !entries->isArray()) {
    blacklist_["entries"] = Json(Json::Type::Array);
    entries = blacklist_.find("entries");
  }
  entries->push_back(entry);
  std::string txt = blacklist_.dump();
  if(!recoveryWriteFileAtomic(blacklistPath(tuningDir_), txt))
    log("WARNING: failed to write blacklist " + blacklistPath(tuningDir_));
}

void State::beginTuning() {
  checkpoint_["schemaVersion"] = Json::number(SCHEMA_VERSION);
  checkpoint_["profile"] = profile_.toJson();
  checkpointMatchesProfile_ = true;
  checkpoint_["kataGoVersion"] = Json::string((profile_.kataGoVersion.empty() ? std::string(KATAGO_VERSION) : profile_.kataGoVersion));
  checkpoint_["tuneFileName"] = Json::string(tuneFileName_);
  checkpoint_["status"] = Json::string("in_progress");
  if(!safeDefaultText_.empty() && !checkpoint_.has("safeDefaultConfig"))
    checkpoint_["safeDefaultConfig"] = Json::string(safeDefaultText_);
  if(!checkpoint_.has("lastSuccessful"))
    checkpoint_["lastSuccessful"] = Json(Json::Type::Object);
  if(!checkpoint_.has("lastAttempted"))
    checkpoint_["lastAttempted"] = Json(Json::Type::Object);
  saveCheckpoint();
  log("Beginning autotuning, writing checkpoint to " + modelCheckpointPath());
}

void State::setCurrentKernel(const std::string& kernel) {
  checkpoint_["currentKernel"] = Json::string(kernel);
  saveCheckpoint();
}

bool State::skipIfBlacklisted(const std::string& kernel, const std::string& params) {
  const Json* entries = blacklist_.find("entries");
  if(entries == NULL || !entries->isArray()) return false;
  for(size_t i = 0; i < entries->size(); i++) {
    const Json& e = entries->at(i);
    if(!e.isObject()) continue;
    if(e.getString("kernel", "") != kernel) continue;
    if(e.getString("params", "") != params) continue;
    const Json* p = e.find("profile");
    Profile ep;
    if(p != NULL && p->isObject()) ep = Profile::fromJson(*p);
    if(Profile::profilesMatch(ep, profile_)) {
      log("Skipping blacklisted tuning parameters: kernel=" + kernel + " params=" + params);
      return true;
    }
  }
  return false;
}

void State::recordCaseAttempt(const std::string& kernel, const std::string& params, long long caseIndex) {
  Json la(Json::Type::Object);
  la["kernel"] = Json::string(kernel);
  la["params"] = Json::string(params);
  la["caseIndex"] = Json::number(caseIndex);
  la["timestamp"] = Json::string(recoveryTimestampUtc());
  checkpoint_["lastAttempted"] = la;
  checkpoint_["status"] = Json::string("in_progress");
  saveCheckpoint();
}

void State::recordCaseSuccess(
  const std::string& kernel,
  const std::string& params,
  long long caseIndex,
  const std::string& fullConfigText
) {
  Json* ls = checkpoint_.find("lastSuccessful");
  if(ls == NULL || !ls->isObject()) {
    checkpoint_["lastSuccessful"] = Json(Json::Type::Object);
    ls = checkpoint_.find("lastSuccessful");
  }
  (*ls)[kernel] = Json::string(params);

  if(!fullConfigText.empty())
    checkpoint_["lastSuccessfulConfig"] = Json::string(fullConfigText);

  checkpoint_["lastAttempted"] = Json(Json::Type::Object);  // cleared: we got past it
  checkpoint_["status"] = Json::string("in_progress");
  saveCheckpoint();
  log("Benchmark successful kernel=" + kernel + " case=" + std::to_string(caseIndex));
  log("Saved last successful tuning parameters: kernel=" + kernel + " params=" + params);
}

void State::recordCaseFailure(
  const std::string& kernel,
  const std::string& params,
  long long caseIndex,
  const std::string& failureType
) {
  addBlacklistEntry(kernel, params, caseIndex, failureType, "native", profile_);
  log("Marking failed parameters as blacklist: kernel=" + kernel + " params=" + params + " failure=" + failureType);
}

void State::finishTuning(const std::string& tunedConfigText) {
  checkpoint_["status"] = Json::string("completed");
  checkpoint_["timestamp"] = Json::string(recoveryTimestampUtc());
  if(!tunedConfigText.empty()) {
    checkpoint_["lastSuccessfulConfig"] = Json::string(tunedConfigText);
    checkpoint_["safeDefaultConfig"] = Json::string(tunedConfigText);
  }
  saveCheckpoint();
  clearRecoveryMarkers();
  log("Autotuning completed");
  log("Safe tuning profile saved to " + tuneFileName_);
}

void State::saveIncrementalConfig(const std::string& configText) {
  if(!configText.empty()) {
    checkpoint_["lastSuccessfulConfig"] = Json::string(configText);
    checkpoint_["safeDefaultConfig"] = Json::string(configText);
    saveCheckpoint();
  }
}

bool State::restoreLastSuccessfulTuneFile() const {
  std::string cfg = lastSuccessfulConfig();
  if(cfg.empty()) return false;
  bool ok = recoveryWriteFileAtomic(tuneFilePath_, cfg);
  if(ok)
    log("Restoring last successful tuning profile -> " + tuneFilePath_);
  else
    log("WARNING: failed to restore last successful tuning profile -> " + tuneFilePath_);
  return ok;
}

bool State::restoreSafeDefaultTuneFile() const {
  std::string cfg = safeDefaultConfig();
  if(cfg.empty()) return false;
  bool ok = recoveryWriteFileAtomic(tuneFilePath_, cfg);
  if(ok)
    log("Restoring safe default tuning profile -> " + tuneFilePath_);
  else
    log("WARNING: failed to restore safe default tuning profile -> " + tuneFilePath_);
  return ok;
}

void State::clearRecoveryMarkers() const {
  recoveryRemoveFile(recoveryMarkerPath(tuningDir_));
  recoveryRemoveFile(safeDefaultsMarkerPath(tuningDir_));
}

}  // namespace OpenCLTuningRecovery