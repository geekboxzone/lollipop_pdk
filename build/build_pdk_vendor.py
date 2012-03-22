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
import os, re, string, sys
import pdk_utils as pu
import setup_pdk_rel as setup

def main(argv):
  if len(argv) < 4:
    print "Usage: build_pdk_vendor.py top_dir cpu_arch target_hw [lunch_target] [-jxx] [-c]"
    print "   Runs pdk_rel build from given top_dir"
    print "   cpu_arch: cpu arch to use like armv7-a-neon_true"
    print "   target_hw: target H/W to build"
    print "   lunch_target: lunch target for build. If not specified, full_target_hw-eng"
    print "   -jxx : number of jobs for make"
    print "   -c: clean before build"
    sys.exit(1)

  top_dir = argv[1]
  cpu_arch = argv[2]
  target_hw = argv[3]
  build_j = "-j12"
  lunch_target = "full_" + target_hw + "-eng"
  clean_build = False
  argv_current = 4
  while len(argv) > argv_current:
    arg = argv[argv_current]
    if arg.startswith("-j"):  
      build_j = arg
    elif arg == "-c":
      clean_build = True
    else:
      lunch_target = arg
    argv_current += 1
  if not os.path.isfile(top_dir + "/pdk/build/pdk_vendor.mk"):
    print "WARNING: pdk/build/pdk_vendor.mk does not exist!!!!"

  if clean_build:
    command = "python " + top_dir + "/pdk/build/clean_pdk_rel.py " + top_dir
    pu.execute_command(command, "cannot clean")

  # setup binary and data
  command = "python " + top_dir + "/pdk/build/setup_pdk_rel.py " + top_dir + " " \
           + cpu_arch + " " + target_hw
  pu.execute_command(command, "cannot copy pdk bin")

  # actual build
  command = "cd " + top_dir + " && . build/envsetup.sh && lunch " + lunch_target + " && " \
          + "make " + build_j + " pdk_rel"
  pu.execute_command(command, "pdk build failed")

if __name__ == '__main__':
  main(sys.argv)
