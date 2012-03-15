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

# script to create minimal source tree for pdk_eng / pdk_rel build
# pdk/build/pdk.mk file will be checked to detect necessary files
# together with additional addition in this script for build dependency

import os, string, sys, shutil
import copy_utils as cu


def get_one_var_occurrence(f, dir_list_var):
  dir_list = []
  for line in f:
    start = line.find(dir_list_var)
    if (start != -1) and (line[0] != "#"): # found the pattern
      break
  # it can be eof, but the next for-loop will filter it out
  for line in f:
    words = line.split()
    #print words
    if len(words) > 0:
      dir_list.append(words[0])
    if len(words) != 2:
      break
  return dir_list

def extract_build_dir(makefile, dir_list_var):
  f = open(makefile, "r")
  dir_list = []
  while 1:
    list_found = get_one_var_occurrence(f, dir_list_var)
    if len(list_found) == 0:
      break;
    dir_list += list_found
  f.close();
  return dir_list

def create_symbolic_link(src_top, dest_top, dir_name):
  src_full = src_top + "/" + dir_name
  dest_full = dest_top + "/" + dir_name
  print "create symbolic link from " + dest_full + " to " + src_full
  # remove existing link first to prevent recursive loop
  os.system("rm -rf " + dest_full)
  os.system("ln -s " + src_full + " " + dest_full)


# when these dirs are copied as a whlole, only symbolic link will be created
# instead of full copying. These dirs should not be overwritten or replaced by copy
symbolic_link_list = [
  "bionic",
  "build",
  "dalvik",
  "development",
  "external",
  "external/clang",
  "external/llvm",
  "libcore",
  "pdk",
  "prebuilt",
  "prebuilts",
  "sdk",
  "system",
  "frameworks/base/data"
]

# the whole dir copied
additional_dir_list = [
  "pdk"
  ]

# these dirs will be direcly pulled as the whole git.
# so these files will not go under vendor/pdk_data
additional_dir_pdk_rel_list_git = [
  "external/libnl-headers",
  "external/proguard",
]

additional_dir_pdk_rel_list = [
  "frameworks/base/build",
  "frameworks/base/cmds/dumpstate",
  "frameworks/base/include/androidfw",
  "frameworks/base/include/android_runtime",
  "frameworks/base/native/include",
  "dalvik/libnativehelper/include",
  "external/v8/include",
  "external/safe-iop/include",
  "system/media/audio_effects/include", # should be removed after refactoring
  "frameworks/base/include/drm", # for building legacy HAL, not in PDK release?
  "frameworks/base/include/media", # for building legacy HAL, not in PDK release?
  "frameworks/base/libs/rs/scriptc" # may remove after refactoring RS
  ]

# only files under the dir is copied, not subdirs
dir_copy_only_files_list = [
  ]

copy_files_list = [
  "Makefile"
  ]

copy_files_pdk_rel_list = [
  "frameworks/base/media/libeffects/data/audio_effects.conf",
  "development/data/etc/apns-conf_sdk.xml",
  "development/data/etc/vold.conf"
  ]

prev_copy_dir_list = [
  "frameworks/base/data"
  ]

# for PDK_ENG build only, use old version
prev_copy_dir_pdk_eng_list = [
  "packages/apps/Bluetooth",
  "packages/inputmethods/LatinIME",
  "packages/providers/ApplicationsProvider",
  "packages/providers/CalendarProvider",
  #"packages/providers/DownloadProvider", old version does not build
  "packages/providers/GoogleContactsProvider",
  "packages/providers/TelephonyProvider",
  "packages/providers/ContactsProvider",
  "packages/providers/DrmProvider",
  "packages/providers/MediaProvider",
  "packages/providers/UserDictionaryProvider"
  ]

# not necessary although copied due to the dir list from pdk.mk
files_to_remove = [
  "vendor/moto/olympus",
  "vendor/samsung/manta",
  "vendor/samsung/mysidspr",
  "vendor/samsung/toro",
  "vendor/samsung/crespo", # should be removed when crespo is supproted
  "vendor/nvidia/proprietary-tegra3",
  "packages/providers/BrowserProvider",
  "hardware/ti/omap4xxx/test/CameraHal" # cannot build with PDK source
  ]

def main(argv):
  if len(argv) < 5:
    print "Usage: create_source_tree.py pdk_type(eng or rel) current_src_top_dir prev_src_top_tree dest_top_dir"
    print "   ex: create_source_tree.py eng ../jb_master ../ics_master /pdk_eng_source"
    sys.exit(1)
  pdk_eng = (argv[1] == "eng")
  src_top_dir = os.path.abspath(argv[2])
  prev_src_top_dir = os.path.abspath(argv[3])
  dest_top_dir = os.path.abspath(argv[4])

  full_copy = True
  # hidden command for initial testing of manually added parts
  if len(argv) == 6:
    if argv[5] == "0":
      full_copy = False
  dir_list = []
  if full_copy:
    dir_list += extract_build_dir(src_top_dir + "/pdk/build/pdk.mk", "BUILD_PDK_SUBDIRS")
    dir_list += extract_build_dir(src_top_dir + "/pdk/build/pdk_google.mk", "BUILD_PDK_SUBDIRS")
    if pdk_eng:
      dir_list += extract_build_dir(src_top_dir + "/pdk/build/pdk.mk", "BUILD_PDK_ENG_SUBDIRS")
    else:
      dir_list += extract_build_dir(src_top_dir + "/pdk/build/pdk.mk", "BUILD_PDK_REL_SUBDIRS")

  dir_list += additional_dir_list
  if not pdk_eng:
    dir_list += additional_dir_pdk_rel_list_git
    dir_list += additional_dir_pdk_rel_list
  for dir_prev_version in prev_copy_dir_list:
    if dir_prev_version in dir_list:
      dir_list.remove(dir_prev_version)
  print "copy list", dir_list

  os.system("mkdir -p " + dest_top_dir)
  for dir_name in dir_list:
    if dir_name in symbolic_link_list:
      create_symbolic_link(src_top_dir, dest_top_dir, dir_name)
    else:
      cu.copy_dir(src_top_dir, dest_top_dir, "/" + dir_name)

  for dir_name in dir_copy_only_files_list:
    cu.copy_dir_only_file(src_top_dir, dest_top_dir, "/" + dir_name)

  copy_files_list_ = copy_files_list
  if not pdk_eng:
    copy_files_list_ += copy_files_pdk_rel_list
  for file_name in copy_files_list_:
    cu.copy_files(src_top_dir, dest_top_dir, "/" + file_name)

  # overwrite files
  cu.copy_files(src_top_dir + "/vendor/pdk/data/google/overwrite", dest_top_dir, "/*")

  for file_name in files_to_remove:
    os.system("rm -rf " + dest_top_dir + "/" + file_name)

  prev_copy_dir_list_ = []
  prev_copy_dir_list_ += prev_copy_dir_list
  if pdk_eng:
    prev_copy_dir_list_ += prev_copy_dir_pdk_eng_list
  print "use ICS version for ", prev_copy_dir_list_
  for dir_name in prev_copy_dir_list_:
    os.system("rm -rf " + dest_top_dir + "/" + dir_name)
    if dir_name in symbolic_link_list:
      create_symbolic_link(prev_src_top_dir, dest_top_dir, dir_name)
    else:
      cu.copy_dir(prev_src_top_dir, dest_top_dir, "/" + dir_name)

if __name__ == '__main__':
  main(sys.argv)
