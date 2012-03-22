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

# script to clean pdk_rel source tree to allow copying new PDK bins and data
import os, sys

import pdk_utils as pu
import setup_pdk_rel as setup

def main(argv):
  if len(argv) != 2:
    print "Usage: clean_pdk_rel.py top_dir"
    print "   cleans PDK data and PDK bin copied."
    print "   Following directories are removed:"
    print "     out"
    print "     all files copied from pdk_data.zip, and PDK_DATA_COPIED"
    print "     all files under " + setup.PDK_CPU_ARCH_TOP_DIR + "/pdk_bin_XXX directories" 
    sys.exit(1)

  top_dir = argv[1]

  os.system("rm -rf " + top_dir + "/out")
  pu.remove_files_listed(top_dir,
                         pu.load_list(top_dir + "/PDK_DATA_COPIED"))
  os.system("rm -f " + top_dir + "/PDK_DATA_COPIED")
  os.system(" rm -rf " + top_dir + "/" + setup.PDK_CPU_ARCH_TOP_DIR + "/pdk_bin_*")

if __name__ == '__main__':
  main(sys.argv)
