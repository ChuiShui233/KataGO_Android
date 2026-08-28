/* libOpenCL.so forwarding shim for kataa (see build notes). */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>
#include <sys/stat.h>
#include <CL/cl.h>
#include <dlfcn.h>
#include <stddef.h>
#if defined(__ANDROID__)
#include <android/dlext.h>
#endif
#include <elf.h>

/* Copy src into dst (app-private dir, so the "(default)" namespace may admit
 * it even though /vendor paths are rejected out of hand). */
static int copy_file(const char *src, const char *dst) {
    FILE *in = fopen(src, "rb");
    if (!in) return -1;
    FILE *out = fopen(dst, "wb");
    if (!out) { fclose(in); return -1; }
    char buf[65536];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
        if (fwrite(buf, 1, n, out) != n) {
            fclose(in); fclose(out); remove(dst); return -1;
        }
    }
    fclose(in);
    fclose(out);
    return 0;
}

/*
 * android_create_namespace() is exported by the device's libdl.so (API 28+)
 * but hidden from NDK headers. Resolve it at runtime so we are not caught by a
 * link-time "cannot locate symbol" error (as happened with
 * android_get_exported_namespace, which IS exported nowhere on this device).
 */
typedef struct android_namespace_t *(*create_ns_fn)(
    const char *name, const char *ld_library_path,
    const char *default_library_path, uint64_t type,
    const char *permitted_when_isolated,
    struct android_namespace_t *parent_namespace);

static void *ocl_handle = NULL;

static FILE *diag_fp = NULL;

/* Log to both stderr (for the Java reader) and an app-visible file, so a
 * later process-killing crash doesn't lose the shim's diagnostics. */
static void diag(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
    if (diag_fp != NULL) {
        va_start(ap, fmt);
        vfprintf(diag_fp, fmt, ap);
        va_end(ap);
        fputc('\n', diag_fp);
        fflush(diag_fp);
    }
}

/* Called before any diag(): try to mirror output into the app's writable dir. */
static void init_diag_file(void) {
    const char *appdir = getenv("KATAA_APP_DATA_DIR");
    if (appdir == NULL || appdir[0] == '\0') return;
    char path[640];
    snprintf(path, sizeof(path), "%s/opencl-shim/shim_diag.log", appdir);
    diag_fp = fopen(path, "w");
}

/* Probe why the (loaded) vendor driver still fails to enumerate platforms. */
static void diagnose(const char *loaded_via) {
    diag("[opencl-shim] loaded via %s", loaded_via);
    /* GPU device nodes an app may need to touch */
    const char *nodes[] = {"/dev/kgsl-3d0", "/dev/kgsl-3d1", "/dev/adreno"};
    for (size_t i = 0; i < sizeof(nodes)/sizeof(nodes[0]); i++)
        diag("[opencl-shim] node %s: %s", nodes[i],
             access(nodes[i], R_OK | W_OK) == 0 ? "accessible" : "missing/denied");

    /* What the vendor driver has to fall back on if it dlopens egl blobs */
    const char *rel[] = {
        "libGLESv2_adreno.so", "libEGL_adreno.so", "librs-adreno_sha1.so",
        "libllvm-qcom.so", "libgsl.so", "libadreno_utils.so",
    };
    for (size_t i = 0; i < sizeof(rel)/sizeof(rel[0]); i++) {
        void *h = dlopen(rel[i], RTLD_LAZY | RTLD_LOCAL); /* relative -> LD_LIBRARY_PATH */
        const char *d = dlerror();
        diag("[opencl-shim] dlopen(%s) rel: %s", rel[i],
             h != NULL ? "OK" : (d ? d : "unknown error"));
        dlerror();
        char absp[256];
        snprintf(absp, sizeof(absp), "/vendor/lib64/egl/%s", rel[i]);
        if (access(absp, F_OK) == 0) {
            void *ha = dlopen(absp, RTLD_LAZY | RTLD_LOCAL); /* absolute -> blocked? */
            const char *da = dlerror();
            diag("[opencl-shim] dlopen(%s) ABS: %s", absp,
                 ha != NULL ? "OK" : (da ? da : "unknown error"));
            dlerror();
        }
    }

    /* Ask the driver directly how many platforms it can see */
    cl_int (*gp)(cl_uint, cl_platform_id *, cl_uint *) =
        (cl_int (*)(cl_uint, cl_platform_id *, cl_uint *))dlsym(ocl_handle, "clGetPlatformIDs");
    if (gp != NULL) {
        cl_uint n = 0;
        cl_int err = gp(0, NULL, &n);
        diag("[opencl-shim] real clGetPlatformIDs -> err=%d platforms=%u", err, n);
        if (n > 0) {
            cl_platform_id plats[8];
            err = gp(n > 8 ? 8 : n, plats, NULL);
            for (cl_uint i = 0; i < n && i < 8; i++) {
                char buf[128] = {0};
                cl_int (*gpi)(cl_platform_id, cl_platform_info, size_t, void *, size_t *) =
                    (cl_int (*)(cl_platform_id, cl_platform_info, size_t, void *, size_t *))dlsym(ocl_handle, "clGetPlatformInfo");
                if (gpi) gpi(plats[i], CL_PLATFORM_NAME, sizeof(buf), buf, NULL);
                diag("[opencl-shim]   platform %u: %s", i, buf);
            }
        }
    }
}

static char staged_lib[640] = {0};
static void *ocl_sym(const char *name) {
    if (ocl_handle == NULL) {
        init_diag_file();
        static const char *const paths[] = {
#ifdef __LP64__
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/odm/lib64/libOpenCL.so",
            "/system/odm/lib64/libOpenCL.so",
            "/vendor/lib64/egl/libOpenCL.so",
            "/system/vendor/lib64/egl/libOpenCL.so",
#else
            "/vendor/lib/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            "/system/lib/libOpenCL.so",
            "/odm/lib/libOpenCL.so",
            "/system/odm/lib/libOpenCL.so",
            "/vendor/lib/egl/libOpenCL.so",
            "/system/vendor/lib/egl/libOpenCL.so",
#endif
        };

        /* In the in-process engine the app's own classloader namespace may
         * already admit /vendor paths (that's the whole point of the
         * uses-native-library whitelist + the app namespace), so try plain
         * dlopen FIRST. The android_create_namespace dance is only a fallback
         * for the exec'd-child "(default)" namespace, and creating a namespace
         * from inside an app process can itself crash (NULL parent is invalid
         * there). */
        void *h = NULL;
        for (size_t i = 0; i < sizeof(paths)/sizeof(paths[0]); i++) {
            int exists = access(paths[i], F_OK) == 0;
            if (exists) {
                h = dlopen(paths[i], RTLD_LAZY | RTLD_LOCAL);
                const char *d = dlerror();
                diag("[opencl-shim] dlopen %s -> %s",
                     paths[i], h != NULL ? "OK" : (d ? d : "unknown error"));
                dlerror();
                if (h != NULL) break;
            }
        }
        if (h == NULL) {
            create_ns_fn create_namespace =
                (create_ns_fn)dlsym(RTLD_DEFAULT, "android_create_namespace");
            struct android_namespace_t *ns = NULL;
            if (create_namespace != NULL) {
                ns = create_namespace(
                    "kataa_opencl",
                    "/vendor/lib64:/vendor/lib:/system/lib64:/system/lib:"
                    "/odm/lib64:/odm/lib",
                    "/system/lib64:/system/lib:/apex/com.android.runtime/lib64:"
                    "/apex/com.android.vndk.v33/lib64",
                    0, /* default type */
                    "/vendor/lib64:/vendor/lib:/odm/lib64:/odm/lib",
                    NULL /* parent = default namespace */);
                diag("[opencl-shim] android_create_namespace: %s",
                     ns != NULL ? "OK" : "failed");
            } else {
                diag("[opencl-shim] android_create_namespace: not exported");
            }

            for (size_t i = 0; i < sizeof(paths)/sizeof(paths[0]); i++) {
                int exists = access(paths[i], F_OK) == 0;
                if (exists) {
                    if (ns != NULL) {
                        android_dlextinfo info;
                        memset(&info, 0, sizeof(info));
                        info.flags = ANDROID_DLEXT_USE_NAMESPACE;
                        info.library_namespace = ns;
                        h = android_dlopen_ext(paths[i], RTLD_NOW | RTLD_LOCAL, &info);
                    }
                    if (h == NULL) {
                        h = dlopen(paths[i], RTLD_LAZY | RTLD_LOCAL);
                    }
                }
                const char *d = dlerror();
                diag("[opencl-shim] ns-attempt %s -> %s",
                     paths[i], h != NULL ? "OK" : (d ? d : "unknown error"));
                dlerror();
                if (h != NULL) break;
            }
        }
        if (h != NULL) {
            diag("[opencl-shim] LOADED %s", "vendor libOpenCL.so");
            ocl_handle = h;
            return dlsym(ocl_handle, name);
        }
        diag("[opencl-shim] FATAL: no vendor libOpenCL.so could be loaded");

        /* Path-staging experiment: the (default) namespace rejects /vendor
         * paths outright, but might admit a copy under the app's own writable
         * dir (KATAA_APP_DIR is exported by the app into this child). If this
         * loads, the way forward is to stage the whole vendor dependency
         * closure (libgsl.so, egl blobs, ...) the same way. */
        const char *appdir = getenv("KATAA_APP_DATA_DIR");
        if (appdir != NULL && appdir[0] != '\0') {
            char subdir[512];
            snprintf(subdir, sizeof(subdir), "%s/opencl-shim", appdir);
            mkdir(subdir, 0755);
            for (size_t i = 0; i < sizeof(paths)/sizeof(paths[0]); i++) {
                if (access(paths[i], F_OK) != 0) continue;
                char dst[640];
                snprintf(dst, sizeof(dst), "%s/libOpenCL.so", subdir);
                if (copy_file(paths[i], dst) != 0) {
                    diag("[opencl-shim] COPY %s -> %s : failed", paths[i], dst);
                    continue;
                }
                void *h = dlopen(dst, RTLD_LAZY | RTLD_LOCAL);
                const char *d = dlerror();
                diag("[opencl-shim] COPY %s -> %s : %s",
                     paths[i], dst, h != NULL ? "LOADED" : (d ? d : "unknown error"));
                if (h != NULL) {
                    ocl_handle = h;
                    strcpy(staged_lib, dst);
                    diagnose("staged copy");
                    return dlsym(ocl_handle, name);
                }
            }
        }

        /* VendorStager pre-copied the whole closure into the app dir (and we
         * may be shadowed by it on LD_LIBRARY_PATH). Load the staged copy. */
        const char *appdir2 = getenv("KATAA_APP_DATA_DIR");
        if (appdir2 != NULL && appdir2[0] != '\0') {
            char sub2[512];
            snprintf(sub2, sizeof(sub2), "%s/opencl-shim/libOpenCL.so", appdir2);
            if (access(sub2, F_OK) == 0) {
                void *h = dlopen(sub2, RTLD_LAZY | RTLD_LOCAL);
                const char *d = dlerror();
                diag("[opencl-shim] staged %s : %s",
                     sub2, h != NULL ? "LOADED" : (d ? d : "unknown error"));
                if (h != NULL) {
                    ocl_handle = h;
                    strcpy(staged_lib, sub2);
                    diagnose("staged lib");
                    return dlsym(ocl_handle, name);
                }
            }
        }
    }
    return ocl_handle != NULL ? dlsym(ocl_handle, name) : NULL;
}

static cl_int(*ocl_clBuildProgram)(cl_program program,  cl_uint num_devices,  const cl_device_id * device_list,  const char * options,  void (CL_CALLBACK * pfn_notify)(cl_program program, void * user_data),  void * user_data) = NULL;
cl_int clBuildProgram(cl_program program,  cl_uint num_devices,  const cl_device_id * device_list,  const char * options,  void (CL_CALLBACK * pfn_notify)(cl_program program, void * user_data),  void * user_data) {
    if (ocl_clBuildProgram == NULL) *(void **)&ocl_clBuildProgram = ocl_sym("clBuildProgram");
    if (ocl_clBuildProgram != NULL) return ocl_clBuildProgram(program, num_devices, device_list, options, pfn_notify, user_data);
    return (cl_int)0;
}

static cl_mem(*ocl_clCreateBuffer)(cl_context context,  cl_mem_flags flags,  size_t size,  void * host_ptr,  cl_int * errcode_ret) = NULL;
cl_mem clCreateBuffer(cl_context context,  cl_mem_flags flags,  size_t size,  void * host_ptr,  cl_int * errcode_ret) {
    if (ocl_clCreateBuffer == NULL) *(void **)&ocl_clCreateBuffer = ocl_sym("clCreateBuffer");
    if (ocl_clCreateBuffer != NULL) return ocl_clCreateBuffer(context, flags, size, host_ptr, errcode_ret);
    return (cl_mem)0;
}

static cl_command_queue(*ocl_clCreateCommandQueue)(cl_context context,  cl_device_id device,  cl_command_queue_properties properties,  cl_int * errcode_ret) = NULL;
cl_command_queue clCreateCommandQueue(cl_context context,  cl_device_id device,  cl_command_queue_properties properties,  cl_int * errcode_ret) {
    if (ocl_clCreateCommandQueue == NULL) *(void **)&ocl_clCreateCommandQueue = ocl_sym("clCreateCommandQueue");
    if (ocl_clCreateCommandQueue != NULL) return ocl_clCreateCommandQueue(context, device, properties, errcode_ret);
    return (cl_command_queue)0;
}

static cl_context(*ocl_clCreateContext)(const cl_context_properties * properties,  cl_uint num_devices,  const cl_device_id * devices,  void (CL_CALLBACK * pfn_notify)(const char * errinfo, const void * private_info, size_t cb, void * user_data),  void * user_data,  cl_int * errcode_ret) = NULL;
cl_context clCreateContext(const cl_context_properties * properties,  cl_uint num_devices,  const cl_device_id * devices,  void (CL_CALLBACK * pfn_notify)(const char * errinfo, const void * private_info, size_t cb, void * user_data),  void * user_data,  cl_int * errcode_ret) {
    if (ocl_clCreateContext == NULL) *(void **)&ocl_clCreateContext = ocl_sym("clCreateContext");
    if (ocl_clCreateContext != NULL) return ocl_clCreateContext(properties, num_devices, devices, pfn_notify, user_data, errcode_ret);
    return (cl_context)0;
}

static cl_kernel(*ocl_clCreateKernel)(cl_program program,  const char * kernel_name,  cl_int * errcode_ret) = NULL;
cl_kernel clCreateKernel(cl_program program,  const char * kernel_name,  cl_int * errcode_ret) {
    if (ocl_clCreateKernel == NULL) *(void **)&ocl_clCreateKernel = ocl_sym("clCreateKernel");
    if (ocl_clCreateKernel != NULL) return ocl_clCreateKernel(program, kernel_name, errcode_ret);
    return (cl_kernel)0;
}

static cl_program(*ocl_clCreateProgramWithSource)(cl_context context,  cl_uint count,  const char ** strings,  const size_t * lengths,  cl_int * errcode_ret) = NULL;
cl_program clCreateProgramWithSource(cl_context context,  cl_uint count,  const char ** strings,  const size_t * lengths,  cl_int * errcode_ret) {
    if (ocl_clCreateProgramWithSource == NULL) *(void **)&ocl_clCreateProgramWithSource = ocl_sym("clCreateProgramWithSource");
    if (ocl_clCreateProgramWithSource != NULL) return ocl_clCreateProgramWithSource(context, count, strings, lengths, errcode_ret);
    return (cl_program)0;
}

static cl_int(*ocl_clEnqueueNDRangeKernel)(cl_command_queue command_queue,  cl_kernel kernel,  cl_uint work_dim,  const size_t * global_work_offset,  const size_t * global_work_size,  const size_t * local_work_size,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) = NULL;
cl_int clEnqueueNDRangeKernel(cl_command_queue command_queue,  cl_kernel kernel,  cl_uint work_dim,  const size_t * global_work_offset,  const size_t * global_work_size,  const size_t * local_work_size,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) {
    if (ocl_clEnqueueNDRangeKernel == NULL) *(void **)&ocl_clEnqueueNDRangeKernel = ocl_sym("clEnqueueNDRangeKernel");
    if (ocl_clEnqueueNDRangeKernel != NULL) return ocl_clEnqueueNDRangeKernel(command_queue, kernel, work_dim, global_work_offset, global_work_size, local_work_size, num_events_in_wait_list, event_wait_list, event);
    return (cl_int)0;
}

static cl_int(*ocl_clEnqueueReadBuffer)(cl_command_queue command_queue,  cl_mem buffer,  cl_bool blocking_read,  size_t offset,  size_t size,  void * ptr,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) = NULL;
cl_int clEnqueueReadBuffer(cl_command_queue command_queue,  cl_mem buffer,  cl_bool blocking_read,  size_t offset,  size_t size,  void * ptr,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) {
    if (ocl_clEnqueueReadBuffer == NULL) *(void **)&ocl_clEnqueueReadBuffer = ocl_sym("clEnqueueReadBuffer");
    if (ocl_clEnqueueReadBuffer != NULL) return ocl_clEnqueueReadBuffer(command_queue, buffer, blocking_read, offset, size, ptr, num_events_in_wait_list, event_wait_list, event);
    return (cl_int)0;
}

static cl_int(*ocl_clEnqueueWriteBuffer)(cl_command_queue command_queue,  cl_mem buffer,  cl_bool blocking_write,  size_t offset,  size_t size,  const void * ptr,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) = NULL;
cl_int clEnqueueWriteBuffer(cl_command_queue command_queue,  cl_mem buffer,  cl_bool blocking_write,  size_t offset,  size_t size,  const void * ptr,  cl_uint num_events_in_wait_list,  const cl_event * event_wait_list,  cl_event * event) {
    if (ocl_clEnqueueWriteBuffer == NULL) *(void **)&ocl_clEnqueueWriteBuffer = ocl_sym("clEnqueueWriteBuffer");
    if (ocl_clEnqueueWriteBuffer != NULL) return ocl_clEnqueueWriteBuffer(command_queue, buffer, blocking_write, offset, size, ptr, num_events_in_wait_list, event_wait_list, event);
    return (cl_int)0;
}

static cl_int(*ocl_clFinish)(cl_command_queue command_queue) = NULL;
cl_int clFinish(cl_command_queue command_queue) {
    if (ocl_clFinish == NULL) *(void **)&ocl_clFinish = ocl_sym("clFinish");
    if (ocl_clFinish != NULL) return ocl_clFinish(command_queue);
    return (cl_int)0;
}

static cl_int(*ocl_clFlush)(cl_command_queue command_queue) = NULL;
cl_int clFlush(cl_command_queue command_queue) {
    if (ocl_clFlush == NULL) *(void **)&ocl_clFlush = ocl_sym("clFlush");
    if (ocl_clFlush != NULL) return ocl_clFlush(command_queue);
    return (cl_int)0;
}

static cl_int(*ocl_clGetDeviceIDs)(cl_platform_id platform,  cl_device_type device_type,  cl_uint num_entries,  cl_device_id * devices,  cl_uint * num_devices) = NULL;
cl_int clGetDeviceIDs(cl_platform_id platform,  cl_device_type device_type,  cl_uint num_entries,  cl_device_id * devices,  cl_uint * num_devices) {
    if (ocl_clGetDeviceIDs == NULL) *(void **)&ocl_clGetDeviceIDs = ocl_sym("clGetDeviceIDs");
    if (ocl_clGetDeviceIDs != NULL) return ocl_clGetDeviceIDs(platform, device_type, num_entries, devices, num_devices);
    return (cl_int)0;
}

static cl_int(*ocl_clGetDeviceInfo)(cl_device_id device,  cl_device_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) = NULL;
cl_int clGetDeviceInfo(cl_device_id device,  cl_device_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) {
    if (ocl_clGetDeviceInfo == NULL) *(void **)&ocl_clGetDeviceInfo = ocl_sym("clGetDeviceInfo");
    if (ocl_clGetDeviceInfo != NULL) return ocl_clGetDeviceInfo(device, param_name, param_value_size, param_value, param_value_size_ret);
    return (cl_int)0;
}

static cl_int(*ocl_clGetEventProfilingInfo)(cl_event event,  cl_profiling_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) = NULL;
cl_int clGetEventProfilingInfo(cl_event event,  cl_profiling_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) {
    if (ocl_clGetEventProfilingInfo == NULL) *(void **)&ocl_clGetEventProfilingInfo = ocl_sym("clGetEventProfilingInfo");
    if (ocl_clGetEventProfilingInfo != NULL) return ocl_clGetEventProfilingInfo(event, param_name, param_value_size, param_value, param_value_size_ret);
    return (cl_int)0;
}

static cl_int(*ocl_clGetPlatformIDs)(cl_uint num_entries,  cl_platform_id * platforms,  cl_uint * num_platforms) = NULL;
cl_int clGetPlatformIDs(cl_uint num_entries,  cl_platform_id * platforms,  cl_uint * num_platforms) {
    if (ocl_clGetPlatformIDs == NULL) *(void **)&ocl_clGetPlatformIDs = ocl_sym("clGetPlatformIDs");
    if (ocl_clGetPlatformIDs != NULL) return ocl_clGetPlatformIDs(num_entries, platforms, num_platforms);
    return (cl_int)0;
}

static cl_int(*ocl_clGetPlatformInfo)(cl_platform_id platform,  cl_platform_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) = NULL;
cl_int clGetPlatformInfo(cl_platform_id platform,  cl_platform_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) {
    if (ocl_clGetPlatformInfo == NULL) *(void **)&ocl_clGetPlatformInfo = ocl_sym("clGetPlatformInfo");
    if (ocl_clGetPlatformInfo != NULL) return ocl_clGetPlatformInfo(platform, param_name, param_value_size, param_value, param_value_size_ret);
    return (cl_int)0;
}

static cl_int(*ocl_clGetProgramBuildInfo)(cl_program program,  cl_device_id device,  cl_program_build_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) = NULL;
cl_int clGetProgramBuildInfo(cl_program program,  cl_device_id device,  cl_program_build_info param_name,  size_t param_value_size,  void * param_value,  size_t * param_value_size_ret) {
    if (ocl_clGetProgramBuildInfo == NULL) *(void **)&ocl_clGetProgramBuildInfo = ocl_sym("clGetProgramBuildInfo");
    if (ocl_clGetProgramBuildInfo != NULL) return ocl_clGetProgramBuildInfo(program, device, param_name, param_value_size, param_value, param_value_size_ret);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseCommandQueue)(cl_command_queue command_queue) = NULL;
cl_int clReleaseCommandQueue(cl_command_queue command_queue) {
    if (ocl_clReleaseCommandQueue == NULL) *(void **)&ocl_clReleaseCommandQueue = ocl_sym("clReleaseCommandQueue");
    if (ocl_clReleaseCommandQueue != NULL) return ocl_clReleaseCommandQueue(command_queue);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseContext)(cl_context context) = NULL;
cl_int clReleaseContext(cl_context context) {
    if (ocl_clReleaseContext == NULL) *(void **)&ocl_clReleaseContext = ocl_sym("clReleaseContext");
    if (ocl_clReleaseContext != NULL) return ocl_clReleaseContext(context);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseEvent)(cl_event event) = NULL;
cl_int clReleaseEvent(cl_event event) {
    if (ocl_clReleaseEvent == NULL) *(void **)&ocl_clReleaseEvent = ocl_sym("clReleaseEvent");
    if (ocl_clReleaseEvent != NULL) return ocl_clReleaseEvent(event);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseKernel)(cl_kernel kernel) = NULL;
cl_int clReleaseKernel(cl_kernel kernel) {
    if (ocl_clReleaseKernel == NULL) *(void **)&ocl_clReleaseKernel = ocl_sym("clReleaseKernel");
    if (ocl_clReleaseKernel != NULL) return ocl_clReleaseKernel(kernel);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseMemObject)(cl_mem memobj) = NULL;
cl_int clReleaseMemObject(cl_mem memobj) {
    if (ocl_clReleaseMemObject == NULL) *(void **)&ocl_clReleaseMemObject = ocl_sym("clReleaseMemObject");
    if (ocl_clReleaseMemObject != NULL) return ocl_clReleaseMemObject(memobj);
    return (cl_int)0;
}

static cl_int(*ocl_clReleaseProgram)(cl_program program) = NULL;
cl_int clReleaseProgram(cl_program program) {
    if (ocl_clReleaseProgram == NULL) *(void **)&ocl_clReleaseProgram = ocl_sym("clReleaseProgram");
    if (ocl_clReleaseProgram != NULL) return ocl_clReleaseProgram(program);
    return (cl_int)0;
}

static cl_int(*ocl_clSetKernelArg)(cl_kernel kernel,  cl_uint arg_index,  size_t arg_size,  const void * arg_value) = NULL;
cl_int clSetKernelArg(cl_kernel kernel,  cl_uint arg_index,  size_t arg_size,  const void * arg_value) {
    if (ocl_clSetKernelArg == NULL) *(void **)&ocl_clSetKernelArg = ocl_sym("clSetKernelArg");
    if (ocl_clSetKernelArg != NULL) return ocl_clSetKernelArg(kernel, arg_index, arg_size, arg_value);
    return (cl_int)0;
}

static cl_int(*ocl_clWaitForEvents)(cl_uint num_events,  const cl_event * event_list) = NULL;
cl_int clWaitForEvents(cl_uint num_events,  const cl_event * event_list) {
    if (ocl_clWaitForEvents == NULL) *(void **)&ocl_clWaitForEvents = ocl_sym("clWaitForEvents");
    if (ocl_clWaitForEvents != NULL) return ocl_clWaitForEvents(num_events, event_list);
    return (cl_int)0;
}
