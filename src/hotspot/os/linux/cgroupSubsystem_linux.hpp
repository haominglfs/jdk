/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef CGROUP_SUBSYSTEM_LINUX_HPP
#define CGROUP_SUBSYSTEM_LINUX_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "logging/log.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "osContainer_linux.hpp"

// Shared cgroups code (used by cgroup version 1 and version 2)

/*
 * PER_CPU_SHARES has been set to 1024 because CPU shares' quota
 * is commonly used in cloud frameworks like Kubernetes[1],
 * AWS[2] and Mesos[3] in a similar way. They spawn containers with
 * --cpu-shares option values scaled by PER_CPU_SHARES. Thus, we do
 * the inverse for determining the number of possible available
 * CPUs to the JVM inside a container. See JDK-8216366.
 *
 * [1] https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/#meaning-of-cpu
 *     In particular:
 *        When using Docker:
 *          The spec.containers[].resources.requests.cpu is converted to its core value, which is potentially
 *          fractional, and multiplied by 1024. The greater of this number or 2 is used as the value of the
 *          --cpu-shares flag in the docker run command.
 * [2] https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ContainerDefinition.html
 * [3] https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/docker/docker.cpp#L648
 *     https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/slave/containerizer/mesos/isolators/cgroups/constants.hpp#L30
 */
#define PER_CPU_SHARES 1024

#define CGROUPS_V1               1
#define CGROUPS_V2               2
#define INVALID_CGROUPS_V2       3
#define INVALID_CGROUPS_V1       4
#define INVALID_CGROUPS_NO_MOUNT 5
#define INVALID_CGROUPS_GENERIC  6

// Five controllers: cpu, cpuset, cpuacct, memory, pids
#define CG_INFO_LENGTH 5
#define CPUSET_IDX     0
#define CPU_IDX        1
#define CPUACCT_IDX    2
#define MEMORY_IDX     3
#define PIDS_IDX       4

class CgroupController: public CHeapObj<mtInternal> {
  protected:
    void set_path(const char *cgroup_path);

    /* mountinfo contents */
    char *_root;
    char *_mount_point;
    char *_cgroup_path = nullptr;

    /* Constructed subsystem directory */
    char *_path = nullptr;

  public:
    CgroupController(const char *root, const char *mountpoint) : _root(os::strdup(root)), _mount_point(os::strdup(mountpoint)) {}
    ~CgroupController() {
      os::free(_root);
      os::free(_mount_point);
      os::free(_cgroup_path);
      os::free(_path);
    }

    bool trim_path(size_t dir_count);
    virtual const char *subsystem_path() { return _path; }

    void set_subsystem_path(const char *cgroup_path);
};

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int __cg_file_contents_impl(const char *absolute_path,
                                                  const char *scan_fmt,
                                                  T returnval) {
  FILE* fp = os::fopen(absolute_path, "r");
  if (fp == nullptr) {
    log_debug(os, container)("Open of file %s failed, %s", absolute_path, os::strerror(errno));
    return OSCONTAINER_ERROR;
  }

  const int buf_len = MAXPATHLEN+1;
  char buf[buf_len];
  char* line = fgets(buf, buf_len, fp);
  if (line == nullptr) {
    log_debug(os, container)("Empty file %s", absolute_path);
    fclose(fp);
    return OSCONTAINER_ERROR;
  }
  fclose(fp);

  int matched = sscanf(line, scan_fmt, returnval);
  if (matched == 1) {
    return 0;
  } else {
    log_debug(os, container)("Type %s not found in file %s", scan_fmt, absolute_path);
  }
  return OSCONTAINER_ERROR;
}
PRAGMA_DIAG_POP

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int cg_file_contents_ctrl(CgroupController* c,
                                                const char *filename,
                                                const char *scan_fmt,
                                                T returnval) {
  if (c == nullptr) {
    log_debug(os, container)("cg_file_contents_ctrl: CgroupController* is null");
    return OSCONTAINER_ERROR;
  }
  if (c->subsystem_path() == nullptr) {
    log_debug(os, container)("cg_file_contents_ctrl: subsystem path is null");
    return OSCONTAINER_ERROR;
  }
  if (scan_fmt == nullptr || returnval == nullptr) {
    log_debug(os, container)("cg_file_contents_ctrl: scan_fmt or return pointer is null");
    return OSCONTAINER_ERROR;
  }

  stringStream file_path;
  file_path.print_raw(c->subsystem_path());
  file_path.print_raw(filename);

  if (file_path.size() > MAXPATHLEN) {
    log_debug(os, container)("File path too long %s, %s", file_path.base(), filename);
    return OSCONTAINER_ERROR;
  }
  const char* absolute_path = file_path.freeze();
  log_trace(os, container)("Path to %s is %s", filename, absolute_path);
  return __cg_file_contents_impl<T>(absolute_path, scan_fmt, returnval);
}
PRAGMA_DIAG_POP

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int __cg_file_multi_line_impl(const char *absolute_path,
                                                    const char *key,
                                                    const char *scan_fmt,
                                                    T returnval) {
  FILE* fp = os::fopen(absolute_path, "r");
  if (fp == nullptr) {
    log_debug(os, container)("Open of file %s failed, %s", absolute_path, os::strerror(errno));
    return OSCONTAINER_ERROR;
  }

  const int buf_len = MAXPATHLEN+1;
  char buf[buf_len];
  char* line = fgets(buf, buf_len, fp);
  if (line == nullptr) {
    log_debug(os, container)("Empty file %s", absolute_path);
    fclose(fp);
    return OSCONTAINER_ERROR;
  }

  bool found_match = false;
  // File consists of multiple lines in a "key value"
  // fashion, we have to find the key.
  const int key_len = (int)strlen(key);
  for (; line != nullptr; line = fgets(buf, buf_len, fp)) {
    char* key_substr = strstr(line, key);
    char after_key = line[key_len];
    if (key_substr == line
          && isspace(after_key) != 0
          && after_key != '\n') {
      // Skip key, skip space
      const char* value_substr = line + key_len + 1;
      int matched = sscanf(value_substr, scan_fmt, returnval);
      found_match = matched == 1;
      if (found_match) {
        break;
      }
    }
  }
  fclose(fp);
  if (found_match) {
    return 0;
  }
  log_debug(os, container)("Type %s (key == %s) not found in file %s", scan_fmt,
                           key, absolute_path);
  return OSCONTAINER_ERROR;
}
PRAGMA_DIAG_POP

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int cg_file_multi_line_ctrl(CgroupController* c,
                                                  const char *filename,
                                                  const char *key,
                                                  const char *scan_fmt,
                                                  T returnval) {
  if (c == nullptr) {
    log_debug(os, container)("cg_file_multi_line_ctrl: CgroupController* is null");
    return OSCONTAINER_ERROR;
  }
  if (c->subsystem_path() == nullptr) {
    log_debug(os, container)("cg_file_multi_line_ctrl: subsystem path is null");
    return OSCONTAINER_ERROR;
  }
  if (key == nullptr || scan_fmt == nullptr || returnval == nullptr) {
    log_debug(os, container)("cg_file_multi_line_ctrl: key, scan_fmt or return pointer is null");
    return OSCONTAINER_ERROR;
  }

  stringStream file_path;
  file_path.print_raw(c->subsystem_path());
  file_path.print_raw(filename);

  if (file_path.size() > MAXPATHLEN) {
    log_debug(os, container)("File path too long %s, %s", file_path.base(), filename);
    return OSCONTAINER_ERROR;
  }
  const char* absolute_path = file_path.freeze();
  log_trace(os, container)("Path to %s is %s", filename, absolute_path);
  return __cg_file_multi_line_impl<T>(absolute_path, key, scan_fmt, returnval);
}
PRAGMA_DIAG_POP

class CachedMetric : public CHeapObj<mtInternal>{
  private:
    volatile jlong _metric;
    volatile jlong _next_check_counter;
  public:
    CachedMetric() {
      _metric = -1;
      _next_check_counter = min_jlong;
    }
    bool should_check_metric() {
      return os::elapsed_counter() > _next_check_counter;
    }
    jlong value() { return _metric; }
    void set_value(jlong value, jlong timeout) {
      _metric = value;
      // Metric is unlikely to change, but we want to remain
      // responsive to configuration changes. A very short grace time
      // between re-read avoids excessive overhead during startup without
      // significantly reducing the VMs ability to promptly react to changed
      // metric config
      _next_check_counter = os::elapsed_counter() + timeout;
    }
};

template <class T>
class CachingCgroupController : public CHeapObj<mtInternal> {
  private:
    T _controller;
    CachedMetric* _metrics_cache;

  public:
    CachingCgroupController(T cont) {
      _controller = cont;
      _metrics_cache = new CachedMetric();
    }

    CachedMetric* metrics_cache() { return _metrics_cache; }
    T controller() { return _controller; }
};

class CgroupCpuController {
  public:
    virtual int cpu_quota() = 0;
    virtual int cpu_period() = 0;
    virtual int cpu_shares() = 0;
    virtual const char *subsystem_path() = 0;
};

class CgroupMemoryController : virtual public CgroupController {
  public:
    virtual jlong read_memory_limit_in_bytes(julong upper_bound) = 0;
    virtual jlong memory_usage_in_bytes() = 0;
    virtual jlong memory_and_swap_limit_in_bytes(julong host_mem, julong host_swap) = 0;
    virtual jlong memory_and_swap_usage_in_bytes(julong host_mem, julong host_swap) = 0;
    virtual jlong memory_soft_limit_in_bytes(julong upper_bound) = 0;
    virtual jlong memory_max_usage_in_bytes() = 0;
    virtual jlong rss_usage_in_bytes() = 0;
    virtual jlong cache_usage_in_bytes() = 0;
    virtual const char *subsystem_path() = 0;
};


class CgroupSubsystem: public CHeapObj<mtInternal> {
  protected:
    void initialize_hierarchy();
  public:
    jlong memory_limit_in_bytes();
    int active_processor_count();

    virtual jlong pids_max() = 0;
    virtual jlong pids_current() = 0;

    int cpu_quota();
    int cpu_period();
    int cpu_shares();
    virtual char * cpu_cpuset_cpus() = 0;
    virtual char * cpu_cpuset_memory_nodes() = 0;
    virtual const char * container_type() = 0;
    virtual CachingCgroupController<CgroupMemoryController*>* memory_controller() = 0;
    virtual CachingCgroupController<CgroupCpuController*>* cpu_controller() = 0;

    jlong memory_usage_in_bytes();
    jlong memory_and_swap_limit_in_bytes();
    jlong memory_and_swap_usage_in_bytes();
    jlong memory_soft_limit_in_bytes();
    jlong memory_max_usage_in_bytes();
    jlong rss_usage_in_bytes();
    jlong cache_usage_in_bytes();
    virtual void print_version_specific_info(outputStream* st) = 0;
};

// Utility class for storing info retrieved from /proc/cgroups,
// /proc/self/cgroup and /proc/self/mountinfo
// For reference see man 7 cgroups and CgroupSubsystemFactory
class CgroupInfo : public StackObj {
  friend class CgroupSubsystemFactory;
  friend class WhiteBox;

  private:
    char* _name;
    int _hierarchy_id;
    bool _enabled;
    bool _data_complete;    // indicating cgroup v1 data is complete for this controller
    char* _cgroup_path;     // cgroup controller path from /proc/self/cgroup
    char* _root_mount_path; // root mount path from /proc/self/mountinfo. Unused for cgroup v2
    char* _mount_path;      // mount path from /proc/self/mountinfo.

  public:
    CgroupInfo() {
      _name = nullptr;
      _hierarchy_id = -1;
      _enabled = false;
      _data_complete = false;
      _cgroup_path = nullptr;
      _root_mount_path = nullptr;
      _mount_path = nullptr;
    }

};

class CgroupSubsystemFactory: AllStatic {
  friend class WhiteBox;

  public:
    static CgroupSubsystem* create();
  private:
    static inline bool is_cgroup_v2(u1* flags) {
       return *flags == CGROUPS_V2;
    }

#ifdef ASSERT
    static inline bool is_valid_cgroup(u1* flags) {
       return *flags == CGROUPS_V1 || *flags == CGROUPS_V2;
    }
    static inline bool is_cgroup_v1(u1* flags) {
       return *flags == CGROUPS_V1;
    }
#endif

    static void set_controller_paths(CgroupInfo* cg_infos,
                                     int controller,
                                     const char* name,
                                     char* mount_path,
                                     char* root_path);
    // Determine the cgroup type (version 1 or version 2), given
    // relevant paths to files. Sets 'flags' accordingly.
    static bool determine_type(CgroupInfo* cg_infos,
                               const char* proc_cgroups,
                               const char* proc_self_cgroup,
                               const char* proc_self_mountinfo,
                               u1* flags);
    static void cleanup(CgroupInfo* cg_infos);
};

#endif // CGROUP_SUBSYSTEM_LINUX_HPP
