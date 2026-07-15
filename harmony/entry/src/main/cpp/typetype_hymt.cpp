#define LOG_DOMAIN 0x54595045
#define LOG_TAG "TypeTypeHyMT"

#include <node_api.h>
#include <hilog/log.h>
#include <rawfile/raw_file_manager.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <mutex>
#include <sstream>
#include <string>
#include <unistd.h>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

namespace {

constexpr int N_THREADS_MIN = 2;
constexpr int N_THREADS_MAX = 4;
constexpr int N_THREADS_HEADROOM = 2;
constexpr int DEFAULT_CONTEXT_SIZE = 2048;
constexpr int OVERFLOW_HEADROOM = 4;
constexpr int BATCH_SIZE = 128;
constexpr float DEFAULT_SAMPLER_TEMP = 0.3f;

std::mutex g_mutex;
bool g_backend_initialized = false;
std::string g_model_path;
llama_model *g_model = nullptr;
llama_context *g_context = nullptr;
llama_batch g_batch{};
common_chat_templates_ptr g_chat_templates;
common_sampler *g_sampler = nullptr;
std::vector<common_chat_msg> g_chat_msgs;
llama_pos g_system_prompt_position = 0;
llama_pos g_current_position = 0;
llama_pos g_stop_generation_position = 0;
std::string g_cached_token_chars;
std::ostringstream g_assistant_ss;

void LlamaLogCallback(enum ggml_log_level level, const char *text, void *) {
    if (text == nullptr) {
        return;
    }
    LogLevel log_level = LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR) {
        log_level = LOG_ERROR;
    } else if (level == GGML_LOG_LEVEL_WARN) {
        log_level = LOG_WARN;
    } else if (level == GGML_LOG_LEVEL_DEBUG) {
        log_level = LOG_DEBUG;
    }
    OH_LOG_Print(LOG_APP, log_level, LOG_DOMAIN, LOG_TAG, "%{public}s", text);
}

void Throw(napi_env env, const std::string &message) {
    napi_throw_error(env, nullptr, message.c_str());
}

bool GetString(napi_env env, napi_value value, std::string &out) {
    size_t length = 0;
    if (napi_get_value_string_utf8(env, value, nullptr, 0, &length) != napi_ok) {
        return false;
    }
    out.resize(length);
    size_t copied = 0;
    return napi_get_value_string_utf8(env, value, out.data(), length + 1, &copied) == napi_ok;
}

napi_value MakeString(napi_env env, const std::string &value) {
    napi_value result;
    napi_create_string_utf8(env, value.c_str(), value.size(), &result);
    return result;
}

napi_value MakeBool(napi_env env, bool value) {
    napi_value result;
    napi_get_boolean(env, value, &result);
    return result;
}

void EnsureBackendInitialized() {
    if (g_backend_initialized) {
        return;
    }
    llama_log_set(LlamaLogCallback, nullptr);
    llama_backend_init();
    g_backend_initialized = true;
}

int ThreadCount() {
    const int cpu_count = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, cpu_count - N_THREADS_HEADROOM));
}

llama_context *CreateContext(llama_model *model) {
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = DEFAULT_CONTEXT_SIZE;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = ThreadCount();
    ctx_params.n_threads_batch = ctx_params.n_threads;
    return llama_init_from_model(model, ctx_params);
}

void ResetLongTermStates(bool clear_kv_cache = true) {
    g_chat_msgs.clear();
    g_system_prompt_position = 0;
    g_current_position = 0;
    if (clear_kv_cache && g_context != nullptr) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

void ResetShortTermStates() {
    g_stop_generation_position = 0;
    g_cached_token_chars.clear();
    g_assistant_ss.str("");
    g_assistant_ss.clear();
}

void FreeModelLocked(bool free_backend) {
    ResetShortTermStates();
    ResetLongTermStates(false);
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    if (g_batch.token != nullptr) {
        llama_batch_free(g_batch);
        g_batch = {};
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_model_path.clear();
    if (free_backend && g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }
}

common_sampler *CreateSampler(float temp) {
    common_params_sampling params;
    params.temp = temp;
    return common_sampler_init(g_model, params);
}

void ShiftContext() {
    const int n_discard = (g_current_position - g_system_prompt_position) / 2;
    if (n_discard <= 0) {
        llama_memory_clear(llama_get_memory(g_context), false);
        g_current_position = 0;
        g_system_prompt_position = 0;
        return;
    }
    llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_prompt_position,
                        g_system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_prompt_position + n_discard,
                         g_current_position, -n_discard);
    g_current_position -= n_discard;
}

std::string ChatAddAndFormat(const std::string &role, const std::string &content) {
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    auto formatted = common_chat_format_single(
        g_chat_templates.get(), g_chat_msgs, msg, role == "user", false);
    g_chat_msgs.push_back(msg);
    return formatted;
}

int DecodeTokensInBatches(const llama_tokens &tokens, llama_pos start_pos, bool compute_last_logit) {
    for (int i = 0; i < static_cast<int>(tokens.size()); i += BATCH_SIZE) {
        const int cur_batch_size = std::min(static_cast<int>(tokens.size()) - i, BATCH_SIZE);
        common_batch_clear(g_batch);
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            ShiftContext();
        }
        for (int j = 0; j < cur_batch_size; ++j) {
            const bool want_logit = compute_last_logit && (i + j == static_cast<int>(tokens.size()) - 1);
            common_batch_add(g_batch, tokens[i + j], start_pos + i + j, {0}, want_logit);
        }
        if (llama_decode(g_context, g_batch) != 0) {
            return 1;
        }
    }
    return 0;
}

bool IsValidUtf8(const char *string) {
    if (string == nullptr) {
        return true;
    }
    const auto *bytes = reinterpret_cast<const unsigned char *>(string);
    while (*bytes != 0x00) {
        int num = 0;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

bool LoadModelLocked(const std::string &model_path, std::string &error) {
    if (g_model != nullptr && g_context != nullptr && g_model_path == model_path) {
        return true;
    }

    FreeModelLocked(false);
    EnsureBackendInitialized();

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;

    g_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (g_model == nullptr) {
        error = "llama_model_load_from_file returned null";
        return false;
    }

    g_context = CreateContext(g_model);
    if (g_context == nullptr) {
        error = "llama_init_from_model returned null";
        FreeModelLocked(false);
        return false;
    }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = CreateSampler(DEFAULT_SAMPLER_TEMP);
    if (g_sampler == nullptr) {
        error = "common_sampler_init returned null";
        FreeModelLocked(false);
        return false;
    }

    g_model_path = model_path;
    return true;
}

std::string TranslateLocked(const std::string &prompt, int n_predict, std::string &error) {
    if (g_model == nullptr || g_context == nullptr || g_sampler == nullptr) {
        error = "HY-MT2 model is not loaded";
        return "";
    }

    ResetLongTermStates();
    ResetShortTermStates();
    common_sampler_reset(g_sampler);

    std::string formatted_prompt = prompt;
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_prompt = ChatAddAndFormat("user", prompt);
    }

    auto tokens = common_tokenize(g_context, formatted_prompt, has_chat_template, has_chat_template);
    const int max_prompt_tokens = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (static_cast<int>(tokens.size()) > max_prompt_tokens) {
        tokens.resize(max_prompt_tokens);
    }

    if (DecodeTokensInBatches(tokens, g_current_position, true) != 0) {
        error = "llama_decode failed while processing prompt";
        return "";
    }

    g_current_position += static_cast<llama_pos>(tokens.size());
    g_stop_generation_position = g_current_position + std::max(1, n_predict);

    std::string output;
    while (g_current_position < g_stop_generation_position) {
        if (g_current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            ShiftContext();
        }

        const auto token = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, token, true);

        common_batch_clear(g_batch);
        common_batch_add(g_batch, token, g_current_position, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            error = "llama_decode failed while generating token";
            return "";
        }
        g_current_position++;

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) {
            break;
        }

        g_cached_token_chars += common_token_to_piece(g_context, token);
        if (IsValidUtf8(g_cached_token_chars.c_str())) {
            output += g_cached_token_chars;
            g_cached_token_chars.clear();
        }
    }

    return output;
}

napi_value CopyRawFileToSandbox(napi_env env, napi_callback_info info) {
    size_t argc = 4;
    napi_value args[4];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 4) {
        Throw(env, "copyRawFileToSandbox requires resourceManager, rawPath, destPath, expectedSize");
        return nullptr;
    }

    std::string raw_path;
    std::string dest_path;
    double expected_size = 0;
    if (!GetString(env, args[1], raw_path) || !GetString(env, args[2], dest_path) ||
        napi_get_value_double(env, args[3], &expected_size) != napi_ok) {
        Throw(env, "invalid copyRawFileToSandbox arguments");
        return nullptr;
    }

    namespace fs = std::filesystem;
    const auto destination = fs::path(dest_path);
    if (fs::exists(destination) && fs::is_regular_file(destination) &&
        static_cast<double>(fs::file_size(destination)) == expected_size) {
        return MakeBool(env, true);
    }

    NativeResourceManager *mgr = OH_ResourceManager_InitNativeResourceManager(env, args[0]);
    if (mgr == nullptr) {
        Throw(env, "failed to initialize native resource manager");
        return nullptr;
    }

    RawFile64 *raw_file = OH_ResourceManager_OpenRawFile64(mgr, raw_path.c_str());
    if (raw_file == nullptr) {
        OH_ResourceManager_ReleaseNativeResourceManager(mgr);
        Throw(env, "failed to open HY-MT2 rawfile");
        return nullptr;
    }

    const int64_t raw_size = OH_ResourceManager_GetRawFileSize64(raw_file);
    if (raw_size != static_cast<int64_t>(expected_size)) {
        OH_ResourceManager_CloseRawFile64(raw_file);
        OH_ResourceManager_ReleaseNativeResourceManager(mgr);
        Throw(env, "HY-MT2 rawfile size mismatch");
        return nullptr;
    }

    fs::create_directories(destination.parent_path());
    const auto temp_path = destination.string() + ".tmp";
    FILE *out = std::fopen(temp_path.c_str(), "wb");
    if (out == nullptr) {
        OH_ResourceManager_CloseRawFile64(raw_file);
        OH_ResourceManager_ReleaseNativeResourceManager(mgr);
        Throw(env, std::string("failed to create model file: ") + std::strerror(errno));
        return nullptr;
    }

    std::vector<char> buffer(1024 * 1024);
    int64_t copied = 0;
    bool ok = true;
    while (copied < raw_size) {
        const int64_t to_read = std::min<int64_t>(buffer.size(), raw_size - copied);
        const int64_t read = OH_ResourceManager_ReadRawFile64(raw_file, buffer.data(), to_read);
        if (read <= 0) {
            ok = false;
            break;
        }
        if (std::fwrite(buffer.data(), 1, static_cast<size_t>(read), out) != static_cast<size_t>(read)) {
            ok = false;
            break;
        }
        copied += read;
    }
    std::fclose(out);
    OH_ResourceManager_CloseRawFile64(raw_file);
    OH_ResourceManager_ReleaseNativeResourceManager(mgr);

    if (!ok || copied != raw_size) {
        std::remove(temp_path.c_str());
        Throw(env, "failed to copy HY-MT2 model to sandbox");
        return nullptr;
    }

    if (fs::exists(destination)) {
        fs::remove(destination);
    }
    fs::rename(temp_path, destination);
    return MakeBool(env, true);
}

napi_value LoadModel(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 1) {
        Throw(env, "loadModel requires modelPath");
        return nullptr;
    }

    std::string model_path;
    if (!GetString(env, args[0], model_path)) {
        Throw(env, "invalid modelPath");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    std::string error;
    if (!LoadModelLocked(model_path, error)) {
        Throw(env, error);
        return nullptr;
    }
    return MakeBool(env, true);
}

napi_value Translate(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 2) {
        Throw(env, "translate requires prompt and predictLength");
        return nullptr;
    }

    std::string prompt;
    int32_t predict_length = 0;
    if (!GetString(env, args[0], prompt) ||
        napi_get_value_int32(env, args[1], &predict_length) != napi_ok) {
        Throw(env, "invalid translate arguments");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    std::string error;
    std::string output = TranslateLocked(prompt, predict_length, error);
    if (!error.empty()) {
        Throw(env, error);
        return nullptr;
    }
    return MakeString(env, output);
}

napi_value SystemInfo(napi_env env, napi_callback_info) {
    std::lock_guard<std::mutex> lock(g_mutex);
    EnsureBackendInitialized();
    return MakeString(env, llama_print_system_info());
}

napi_value Release(napi_env env, napi_callback_info) {
    std::lock_guard<std::mutex> lock(g_mutex);
    FreeModelLocked(false);
    return MakeBool(env, true);
}

napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor descriptors[] = {
        {"copyRawFileToSandbox", nullptr, CopyRawFileToSandbox, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"loadModel", nullptr, LoadModel, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"translate", nullptr, Translate, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"systemInfo", nullptr, SystemInfo, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"release", nullptr, Release, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(descriptors) / sizeof(descriptors[0]), descriptors);
    return exports;
}

} // namespace

NAPI_MODULE(typetype_hymt, Init)
