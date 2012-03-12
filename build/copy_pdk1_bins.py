#!/usr/bin/env python
#
# Copyright (C) 2012 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# script to copy PDK1 build result into a dir for PDK2 build

import os, string, sys, shutil
import copy_utils as cu


# currently javalib.jar is not used and target jar is just copied in raw copy process
def copy_jar(src_jar_dir, dest_dir, jar_name):
  """copy classes.jar and javalib.jar to dest_dir/jar_name dir"""
  cu.copy_file_if_exists(src_jar_dir, dest_dir + "/" + jar_name, "classes.jar")
  cu.copy_file_if_exists(src_jar_dir, dest_dir + "/" + jar_name, "javalib.jar")

def copy_classes_or_javalib(src_jar_dir, dest_dir, jar_name):
  """For host, either classes.jar or javalib.jar is enough.
     Use javalib only when there is no classes.jar"""
  if cu.copy_file_new_name_if_exists(src_jar_dir + "/classes.jar", dest_dir + "/" + jar_name,
                                     jar_name + ".jar"):
    return
  cu.copy_file_new_name_if_exists(src_jar_dir + "/javalib.jar", dest_dir + "/" + jar_name,
                                  jar_name + ".jar")

class DependencyMk(object):
  def __init__(self, file_full_name):
    self.f = open(file_full_name, "a+")

  def __del__(self):
    self.f.flush()
    self.f.close()

  def addHostA(self, a_name):
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_PREBUILT_LIBS := " + "host/lib/" + a_name + ".a\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("include $(BUILD_HOST_PREBUILT)\n")

  def addHostSo(self, so_name):
    # same as .a in makefile, distinguished by extension in build/core/multi_prebuilt.mk
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_PREBUILT_LIBS := " + "host/lib/" + so_name + ".so\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("include $(BUILD_HOST_PREBUILT)\n")

  def addHostJar(self, jar_name):
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("LOCAL_PREBUILT_JAVA_LIBRARIES := " + "host/java_lib/" + jar_name
                 + "/" + jar_name + ".jar\n")
    self.f.write("include $(BUILD_HOST_PREBUILT)\n")

  def addTargetA(self, a_name):
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_MODULE := " + a_name + "\n")
    self.f.write("LOCAL_SRC_FILES := target/lib/" + a_name + ".a\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("LOCAL_MODULE_SUFFIX := .a\n")
    self.f.write("LOCAL_MODULE_CLASS := STATIC_LIBRARIES\n")
    self.f.write("include $(BUILD_PREBUILT)\n")

  def addTargetSo(self, so_name):
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_MODULE := " + so_name + "\n")
    self.f.write("LOCAL_SRC_FILES := target/lib/" + so_name + ".so\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("LOCAL_MODULE_SUFFIX := .so\n")
    self.f.write("LOCAL_MODULE_CLASS := SHARED_LIBRARIES\n")
    self.f.write("LOCAL_MODULE_PATH := $(TARGET_OUT)/lib\n")
    self.f.write("OVERRIDE_BUILT_MODULE_PATH := $(TARGET_OUT_INTERMEDIATE_LIBRARIES)\n")
    self.f.write("include $(BUILD_PREBUILT)\n")

  def addTargetJar(self, jar_name):
    self.f.write("include $(CLEAR_VARS)\n")
    self.f.write("LOCAL_MODULE_TAGS := optional\n")
    self.f.write("LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := " + jar_name + ":target/java_lib/" +
                 jar_name + "/classes.jar\n")
    self.f.write("include $(BUILD_MULTI_PREBUILT)\n")

  def addString(self, message):
    self.f.write(message)

# individual files top copy as it is
# product/maguro will be substituted into product/target_name later
raw_file_list = [
# "target/common/obj/PACKAGING/public_api.txt",
  "host/linux-x86/bin/dx",
  "host/linux-x86/bin/aapt",
  # necessary for bootstrapping in host as the path is always assumed
  "host/common/obj/JAVA_LIBRARIES/core-hostdex_intermediates/classes.jar",
  "host/common/obj/JAVA_LIBRARIES/core-hostdex_intermediates/javalib.jar",
  "host/common/obj/JAVA_LIBRARIES/core-junit-hostdex_intermediates/classes.jar",
  "host/common/obj/JAVA_LIBRARIES/core-junit-hostdex_intermediates/javalib.jar",
  # these permission stuffs are not copied as frameworks is not built
  "target/product/maguro/system/etc/permissions/com.android.location.provider.xml",
  "target/product/maguro/system/etc/permissions/platform.xml",
  ]

# the whole dir to copy as it is
raw_dir_list = [
  "target/product/maguro/system/etc/dhcpcd",
  "target/product/maguro/system/etc/ppp",
  "target/product/maguro/system/app",
  "target/product/maguro/system/bin",
  "target/product/maguro/system/etc/security",
  "target/product/maguro/system/framework",
  "target/product/maguro/system/lib",
  # tools for debugging, not all of them are built in pdk2 build
  "target/product/maguro/system/xbin"
  ]

# from host/linux-x86/obj/STATIC_LIBRARIES/XYZ_intermediates
host_a_list = [
  "libandroidfw"
  ]

# from host/linux-x86/obj/lib
host_so_list = [
  "libbcc"
  ]

# from host/commom/JAVA_LIBRARIES/XYZ_intermediates
host_jar_list = [
  "core-hostdex",
  "dx",
  "core-junit-hostdex"
  ]

# from target/product/product_name/obj/STATIC_LIBRARIES/XYZ_intermediates
target_a_list = [
  "libdrmframeworkcommon",
  "libcpustats",
  "libv8",
  "libmedia_helper"
  ]

# from target/product/product_name/obj/lib
target_so_list = [
  "libandroid",
  "libandroidfw",
  "libandroid_runtime",
  "libandroid_servers",
  "libjnigraphics",
  "libnativehelper",
  "libemoji",
  "libdrmframework",
  "libbcc",
  "libdvm",
  "libchromium_net",
  "libcamera_client",
  "libmedia",
  "libstagefright",
  "libstagefright_foundation"
  ]


# from target/common/obj/JAVA_LIBRARIES
target_jar_list = [
  "core",
  "core-junit",
  "ext",
  "framework",
  "android.test.runner",
  "android_stubs_current",
  "filterfw"
  ]

# files unnecessarily built for PDK. remove after copy
raw_target_files_to_remove = [
  # redundant
  "target/product/maguro/system/app/Home.apk",
  # stingray build included due to wrong mk file
  "target/product/maguro/system/app/StingrayProgramMenu*.apk",
  "target/product/maguro/system/app/SprintMenu.apk",
  "target/product/maguro/system/app/WiMAX*.apk",
  # wallpaper, not necessary
  "target/product/maguro/system/app/Microbes.apk",
  # H/W depedent anyway
  "target/procuct/maguro/system/hw",
  "target/product/maguro/system/lib/hw",
  "target/product/maguro/system/lib/libOMX.SEC.*.so",
  "target/product/maguro/system/lib/libSEC_OMX*.so",
  "target/product/maguro/system/lib/libWiMAX*.so",
  "target/product/maguro/system/lib/libOMX.TI.*.so",
  "target/product/maguro/system/lib/libOMX_Core.so",
  ]

def main(argv):
  if len(argv) != 4:
    print "Usage: copy_pdk1_bins.py src_top_dir dest_top_dir src_target_device"
    print "   ex: copy_pdk1_bins.py ../master vendor/pdk_bin_j_arm maguro"
    sys.exit(1)
  src_top_dir = argv[1]
  src_out_dir = argv[1] + "/out/"
  dest_top_dir = argv[2]
  target_name = argv[3]

  # now replace product/maguro to product/target_name for all lists
  replacement_list = [
    raw_file_list,
    raw_dir_list,
    raw_target_files_to_remove
    ]
  for dir_list in replacement_list:
    for i in xrange(len(dir_list)):
      replacement = dir_list[i].replace("product/maguro", "product/" + target_name)
      dir_list[i] = replacement

  src_target_top_dir = src_out_dir + "/target/product/" + target_name + "/"
  # delete existing binaries
  os.system("rm -rf " + dest_top_dir + "/host")
  os.system("rm -rf " + dest_top_dir + "/raw_copy")
  os.system("rm -rf " + dest_top_dir + "/target")
  # copy template for mk
  cu.copy_file_if_exists(src_top_dir + "/pdk/build", dest_top_dir, "pdk_prebuilt.mk")
  mkFile = DependencyMk(dest_top_dir + "/pdk_prebuilt.mk")
  mkFile.addString("\n\n\n")
  mkFile.addString("PDK_BIN_ORIGINAL_TARGET := " + target_name + "\n")

  for file_name in raw_file_list:
    cu.copy_file_if_exists(src_out_dir, dest_top_dir + "/raw_copy", file_name)

  for raw_dir in raw_dir_list:
    cu.copy_dir(src_out_dir, dest_top_dir + "/raw_copy", raw_dir)

  for host_a in host_a_list:
    cu.copy_file_if_exists(src_out_dir + "/host/linux-x86/obj/STATIC_LIBRARIES/" + host_a +
                           "_intermediates", dest_top_dir + "/host/lib", host_a + ".a")
    mkFile.addHostA(host_a)

  for host_so in host_so_list:
    cu.copy_file_if_exists(src_out_dir + "/host/linux-x86/obj/lib/",
                           dest_top_dir + "/host/lib", host_so + ".so")
    mkFile.addHostSo(host_so)

  src_host_jar_top = src_out_dir + "/host/common/obj/JAVA_LIBRARIES/"
  for host_jar in host_jar_list:
    src_host_jar_dir = src_host_jar_top + host_jar + "_intermediates/"
    copy_classes_or_javalib(src_host_jar_dir, dest_top_dir + "/host/java_lib/", host_jar)
    mkFile.addHostJar(host_jar)

  for target_a in target_a_list:
    cu.copy_file_if_exists(src_target_top_dir + "obj/STATIC_LIBRARIES/" + target_a +
                           "_intermediates", dest_top_dir + "/target/lib", target_a + ".a")
    mkFile.addTargetA(target_a)

  for target_so in target_so_list:
    cu.copy_file_if_exists(src_target_top_dir + "obj/lib/",
                           dest_top_dir + "/target/lib", target_so + ".so")
    mkFile.addTargetSo(target_so)

  src_target_jar_top = src_out_dir + "/target/common/obj/JAVA_LIBRARIES/"
  for target_jar in target_jar_list:
    src_target_jar_dir = src_target_jar_top + target_jar + "_intermediates/"
    copy_jar(src_target_jar_dir, dest_top_dir + "/target/java_lib", target_jar)
    mkFile.addTargetJar(target_jar)

  for file_to_remove in raw_target_files_to_remove:
    os.system("rm -rf " + dest_top_dir + "/raw_copy/" + file_to_remove)

if __name__ == '__main__':
  main(sys.argv)
