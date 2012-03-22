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
import pdk_utils as pu

PDK_BIN_PREFIX = "pdk_bin_"
PDK_BIN_TOP_DIR = "/vendor/pdk/data/partner/bin"
PDK_CPU_ARCH_TOP_DIR = "/vendor/pdk/data/partner"
PDK_DATA_TOP_DIR = "/vendor/pdk/data/partner/data"

def list_available_pdk_bins(path):
  """returns the list of pdk_bin_* dir under the given path"""
  pdk_bins_dict = {}

  file_list = pu.list_files(os.path.abspath(path))
  for file_name in file_list:
    m = re.search(PDK_BIN_PREFIX + "(.*)\.zip$", file_name)
    if m != None:
      print " pdk_bin for arch " + m.group(1) + " @ " + file_name
      pdk_bins_dict[m.group(1)] = file_name
  return pdk_bins_dict

def main(argv):
  if len(argv) != 4:
    print "Usage: setup_pdk_rel.py top_dir cpu_conf target_hw"
    print "   ex: setup_pdk_rel.py pdk_rel_source armv7-a-neon_true maguro"
    sys.exit(1)
  top_dir = argv[1]
  cpu_conf = argv[2]
  target_hw = argv[3]

  pdk_bins_dict = list_available_pdk_bins(top_dir + PDK_BIN_TOP_DIR)

  if not (cpu_conf in pdk_bins_dict):
    print "Specified cpu_conf", cpu_conf, "not avaialble under", top_dir + PDK_BIN_TOP_DIR
    print "Avaiable configurations are ", pdk_bins_dict.keys()
    sys.exit(1)

  pdk_bin_zip = pdk_bins_dict[cpu_conf]
  pdk_data_zip = top_dir + PDK_DATA_TOP_DIR + "/pdk_data.zip"
  pdk_partner_data_cpu_path = top_dir + PDK_CPU_ARCH_TOP_DIR + "/" + PDK_BIN_PREFIX + cpu_conf
  PDK_BIN_COPIED = top_dir + "/out/target/product/" + target_hw + "/PDK_BIN_COPIED"
  PDK_DATA_COPIED = top_dir + "/PDK_DATA_COPIED"

  copy_out_dir = pu.src_newer_than_dest(pdk_bin_zip, PDK_BIN_COPIED)
  copy_partner_data_cpu = pu.src_newer_than_dest(pdk_bin_zip, pdk_partner_data_cpu_path)
  copy_pdk_data = pu.src_newer_than_dest(pdk_data_zip, PDK_DATA_COPIED)

  if copy_out_dir:
    print "copy pdk bins to out"
    # clean out as binary is changed
    pu.remove_if_exists(top_dir + "/out")
    command = "mkdir -p " + top_dir + "/out && " \
            + "cd " + top_dir + "/out && " \
            + "rm -rf raw_copy && " \
            + "unzip " + os.path.abspath(pdk_bin_zip) + " raw_copy/* && " \
            + "mv raw_copy/target/product/pdk_target raw_copy/target/product/" + target_hw + " &&" \
            + "mv -f raw_copy/* . && " \
            + "touch " + os.path.abspath(PDK_BIN_COPIED)
    os.system(command)

  if copy_partner_data_cpu:
    print "copy pdk bins to " + pdk_partner_data_cpu_path
    pu.remove_if_exists(pdk_partner_data_cpu_path)
    command = "mkdir -p " + pdk_partner_data_cpu_path + " && " \
            + "cd " + pdk_partner_data_cpu_path + " && " \
            + "unzip -o " + os.path.abspath(pdk_bin_zip) + " host/* target/* pdk_prebuilt.mk"
    os.system(command)

  if copy_pdk_data:
    print "copy pdk data"
    # first remove old files
    pu.remove_files_listed(top_dir, pu.load_list(PDK_DATA_COPIED))
    command = "cd " + top_dir + " && " \
            + "unzip -o " + os.path.abspath(pdk_data_zip)
    os.system(command)
    # recorde copied files to delete correctly.
    pu.save_list(pu.list_files_in_zip(pdk_data_zip), PDK_DATA_COPIED)


if __name__ == '__main__':
  main(sys.argv)
