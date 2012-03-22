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
import pdk_utils as pu
import create_source_tree as tree

def clean_dest_dirs(dest_vendor_data):
  dir_to_clean = []
  dir_to_clean += tree.prev_copy_dir_list
  dir_to_clean += tree.additional_dir_pdk_rel_list
  print dir_to_clean
  for file_name in tree.copy_files_pdk_rel_list:
    [path, name] = file_name.rsplit("/", 1)
    print path
    dir_to_clean.append(path)

  for dir_name in dir_to_clean:
    dir_path = dest_vendor_data + "/" + dir_name
    print "deleting all files under " + dir_path
    if os.path.isfile(dir_path):
      # this is wrong, just remove the file
      os.system("rm " + dir_path)
    if os.path.isdir(dir_path):
      file_list = pu.list_files(dir_path, ".git")
      print file_list
      for file_name in file_list:
        os.system("rm " + file_name)

def main(argv):
  if len(argv) < 4:
    print "Usage: copy_pdk_data.py current previous dest_dir [-c]"
    print "   ex: copy_pdk_data.py ../jb_master ../ics_master ./out/target/pdk_data"
    print "   -c to clean dest_dir"
    sys.exit(1)
  current_branch = os.path.abspath(argv[1])
  previous_branch = os.path.abspath(argv[2])
  dest_dir = os.path.abspath(argv[3])

  cp_option = ""
  if len(argv) == 5 and argv[4] == "-c":
    clean_dest_dirs(dest_dir)
    cp_option = "-n" # do not overwrite

  for dir_name in tree.prev_copy_dir_list:
    pu.copy_dir(previous_branch, dest_dir, dir_name, cp_option)

  for dir_name in tree.additional_dir_pdk_rel_list:
    pu.copy_dir(current_branch, dest_dir, dir_name, cp_option)

  for file_name in tree.copy_files_pdk_rel_list:
    pu.copy_files(current_branch, dest_dir, file_name)

if __name__ == '__main__':
  main(sys.argv)
