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

# script to prepare pdk_rel branches for build.
# This should be run after every make clean as necessary files will be deleted
# TODO : set up source code as well

import os, re, string, sys

PDK_BIN_PREFIX = "pdk_bin_"
PDK_BIN_TOP_DIR = "/vendor/pdk/data/partner/"

def list_available_pdk_bins(path):
  """returns the list of pdk_bin_* dir under the given path"""
  pdk_bin_list = list()
  file_list = os.listdir(path)
  for file_name in file_list:
    if file_name.startswith(PDK_BIN_PREFIX) and os.path.isdir(path + "/" + file_name):
        pdk_bin_list.append(file_name)
  return pdk_bin_list

def get_target_name_from_pdk_bin(path):
  """returns the original target name from the given pdk_bin_* dir"""
  product_dir = path + "/raw_copy/target/product"
  file_list = os.listdir(product_dir)
  for file_name in file_list:
    if os.path.isdir(product_dir + "/" + file_name):
        return file_name
  assert False, "target not found from product dir"

def main(argv):
  if len(argv) != 4:
    print "Usage: setup_pdk_rel.py top_dir cpu_conf target_hw"
    print "   ex: setup_pdk_rel.py pdk_rel_source armv7-a-neon_true maguro"
    sys.exit(1)
  top_dir = argv[1]
  cpu_conf = argv[2]
  target_hw = argv[3]

  pdk_bin_dirs = list_available_pdk_bins(top_dir + PDK_BIN_TOP_DIR)
  arch_list = []
  for pdk_bin_dir in pdk_bin_dirs:
    arch_list.append(pdk_bin_dir[len(PDK_BIN_PREFIX): ])

  if not (cpu_conf in arch_list):
    print "Specified cpu_conf", cpu_conf, "not avaialble under", top_dir + PDK_BIN_TOP_DIR
    print "Avaiable configurations are ", arch_list
    sys.exit(1)

  print "copy pdk bins"
  os.system("mkdir -p " + top_dir + "/out/host")
  os.system("mkdir -p " + top_dir + "/out/target/common")
  os.system("mkdir -p " + top_dir + "/out/target/product/" + target_hw)
  pdk_bin_path = top_dir + PDK_BIN_TOP_DIR + PDK_BIN_PREFIX + cpu_conf
  pdk_bin_target_name = get_target_name_from_pdk_bin(pdk_bin_path)
  os.system("cp -a " + pdk_bin_path + "/raw_copy/host/* " + top_dir + "/out/host")
# no target/common yet
#  os.system("cp -a " + pdk_bin_path + "/raw_copy/target/common/* " + top_dir +
#            "/out/target/common")
  os.system("cp -a " + pdk_bin_path + "/raw_copy/target/product/" + pdk_bin_target_name + "/* "
            + top_dir + "/out/target/product/" + target_hw)
  os.system("touch " + top_dir + "/out/target/product/" + target_hw + "/PDK_BIN_COPIED")

if __name__ == '__main__':
  main(sys.argv)
