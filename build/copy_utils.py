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

# copy relared utils for all PDK scripts

import os, string, sys, shutil

def copy_dir(src_top, dest_top, dir_name):
  """copy all the files under src_top/dir_name to dest_top/dir_name."""
  src_full_path = src_top + "/" + dir_name
  # do not create the leaf dir as cp will create it
  [mid_path, leaf_path] = dir_name.rsplit("/", 1)
  dest_full_path = dest_top + "/" + mid_path
  if not os.path.isdir(dest_full_path):
    os.makedirs(dest_full_path)
  print "copy dir ", src_full_path, " to ", dest_full_path
  os.system("cp -a " + src_full_path + " " + dest_full_path)


def copy_dir_only_file(src_top, dest_top, dir_name):
  """copy only files directly under the given dir_name"""
  src_full_path = src_top + "/" + dir_name
  dest_full_path = dest_top + "/" + dir_name
  if not os.path.isdir(dest_full_path):
    os.makedirs(dest_full_path)
  children = os.listdir(src_full_path)
  for child in children:
    child_full_name = src_full_path + "/" + child
    if os.path.isfile(child_full_name):
      print "copy file ", child_full_name, " to ", dest_full_path
      os.system("cp -a " + child_full_name + " " + dest_full_path)


def copy_files(src_top, dest_top, files_name):
  """copy files from src_top to dest_top.
     Note that files_name can include directories which will be created
     under dest_top"""
  src_full_path = src_top + "/" + files_name
  # do not create the leaf dir as cp will create it
  [mid_path, leaf_path] = files_name.rsplit("/", 1)
  dest_full_path = dest_top + "/" + mid_path
  if not os.path.isdir(dest_full_path):
    os.makedirs(dest_full_path)
  print "copy files ", src_full_path, " to ", dest_full_path
  os.system("cp -a " + src_full_path + " " + dest_full_path)


def copy_file_if_exists(src_top, dest_top, file_name):
  """copy file src_top/file_name to dest_top/file_name
     returns false if such file does not exist in source."""
  src_full_name = src_top + "/" + file_name
  if not os.path.isfile(src_full_name):
    print "file " + src_full_name + " not found"
    return False
  dest_file = dest_top + "/" + file_name
  dest_dir = os.path.dirname(dest_file)
  if not os.path.isdir(dest_dir):
    os.makedirs(dest_dir)
  print "copy file ", src_full_name, " to ", dest_file
  os.system("cp -a " + src_full_name + " " +  dest_file)
  return True


def copy_file_new_name_if_exists(src_full_name, dest_dir, dest_file):
  """copy src_full_name (including dir + file name) to dest_dir/dest_file
     will be used when renaming is necessary"""
  if not os.path.isfile(src_full_name):
    print "file " + src_full_name + " not found"
    return False
  dest_full_name = dest_dir + "/" + dest_file
  if not os.path.isdir(dest_dir):
    os.makedirs(dest_dir)
  print "copy file ", src_full_name, " to ", dest_full_name
  os.system("cp -a " + src_full_name + " " + dest_full_name)
  return True
