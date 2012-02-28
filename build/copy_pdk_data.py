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

# script to copy necessary data / source for PDK build from
# the current and the previous branch

import os, string, sys, shutil
import copy_utils as cu
import create_source_tree as tree


def main(argv):
  if len(argv) != 4:
    print "Usage: copy_pdk_data.py current previous dest_top_dir"
    print "   ex: copy_pdk_data.py ../jb_master ../ics_master ."
    sys.exit(1)
  current_branch = argv[1]
  previous_branch = argv[2]
  dest_top = argv[3]

  for dir_name in tree.prev_copy_dir_list:
    cu.copy_dir(previous_branch, dest_top + "/vendor/pdk_data", dir_name)

  for dir_name in tree.prev_copy_dir_pdk1_list:
    cu.copy_dir(previous_branch, dest_top + "/vendor/pdk_data_internal", dir_name)

  for dir_name in tree.additional_dir_pdk2_list:
    cu.copy_dir(current_branch, dest_top + "/vendor/pdk_data", dir_name)

  for file_name in tree.copy_files_pdk2_list:
    cu.copy_files(current_branch, dest_top + "/vendor/pdk_data", file_name)

if __name__ == '__main__':
  main(sys.argv)
