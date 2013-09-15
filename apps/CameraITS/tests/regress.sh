#!/bin/bash

# Copyright 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# The tests exercised in this file all assert/exit on failure, and terminate
# cleanly on success. The device is rebooted for each test, to ensure that
# a problem in one test doesn't propagate into subsequent tests. If any tests
# assert/exit (i.e. fail), this script will exit.

set -e

rm -rf out
mkdir -p out
cd out

for T in \
         test_3a.py \
         test_black_white.py \
         test_camera_properties.py \
         test_capture_result.py \
         test_exposure.py \
         test_formats.py \
         test_jpeg.py \
         test_param_color_correction.py \
         test_param_exposure_time.py \
         test_param_noise_reduction.py \
         test_param_sensitivity.py \
         test_param_tonemap_mode.py \
         \
         test_latching.py \
         test_linearity.py \
         test_param_edge_mode.py \

do
    echo ""
    echo "--------------------------------------------------------------------"
    echo "Running test: $T"
    echo "--------------------------------------------------------------------"
    python ../"$T" reboot
    echo ""
done

cd ..

echo ""
echo "####################"
echo "# All tests passed #"
echo "####################"
echo ""

