/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.hpp"
#include "JVMTITools.hpp"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = nullptr;
static jvmtiCapabilities caps;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_setbrk002(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_setbrk002(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_setbrk002(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res;
    jvmtiError err;

    if (options != nullptr && strcmp(options, "printdump") == 0) {
        printdump = JNI_TRUE;
    }

    res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == nullptr) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }
    err = jvmti->GetPotentialCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetPotentialCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(AddCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->GetCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    if (!caps.can_generate_breakpoint_events) {
        printf("Warning: Breakpoint is not implemented\n");
    }

    return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_SetBreakpoint_setbrk002_check(JNIEnv *env, jclass cls) {
    jvmtiError err;
    jmethodID mid;
    jlocation start;
    jlocation end;

    if (jvmti == nullptr) {
        printf("JVMTI client was not properly loaded!\n");
        return STATUS_FAILED;
    }

    if (!caps.can_generate_breakpoint_events) {
        return result;
    }

    mid = env->GetStaticMethodID(cls, "run", "([Ljava/lang/String;Ljava/io/PrintStream;)I");
    if (mid == nullptr) {
        printf("Cannot find method run\n");
        return STATUS_FAILED;
    }

    err=jvmti->GetMethodLocation(mid, &start, &end);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetMethodLocation) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> location less then starting location check ...\n");
    }
    err = jvmti->SetBreakpoint(mid, start - 1);
    if (err != JVMTI_ERROR_INVALID_LOCATION) {
        printf("Error expected: JVMTI_ERROR_INVALID_LOCATION,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> location greater then ending location check ...\n");
    }
    err = jvmti->SetBreakpoint(mid, end + 1);
    if (err != JVMTI_ERROR_INVALID_LOCATION) {
        printf("Error expected: JVMTI_ERROR_INVALID_LOCATION,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    return result;
}

}
