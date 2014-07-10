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
# cleanly on success.

# Optionally, the device can be rebooted for each test, to ensure that
# a problem in one test doesn't propagate into subsequent tests; use this
# when debugging test failures, but not when running the test suite (since
# all tests should pass even when run back-to-back).
#REBOOT=reboot

echo ""
echo "--------------------------------------------------------------------"
echo "Set up camera for scene 1:"
echo "* Camera on tripod in portrait orientation"
echo "* Scene filled (or mostly filled) by grey card"
echo "* Illuminated by simple light source, for example a desk lamp"
echo "* Uniformity of lighting and target positioning need not be precise"
echo ""
echo "Use any camera app to be able to see preview while setting up the"
echo "camera, grey card, and light source; make sure that the camera app"
echo "is killed before proceeding."
echo ""
echo "Press ENTER when the camera set up is complete ..."
echo "--------------------------------------------------------------------"
read DUMMY

rm -rf out1
mkdir -p out1
cd out1

python ../config.py $REBOOT

testcount=0
failcount=0

for T in \
         test_3a.py \
         test_auto.py \
         test_black_white.py \
         test_camera_properties.py \
         test_capture_result.py \
         test_exposure.py \
         test_formats.py \
         test_format_combos.py \
         test_jitter.py \
         test_jpeg.py \
         test_latching.py \
         test_linearity.py \
         test_param_color_correction.py \
         test_param_exposure_time.py \
         test_param_flash_mode.py \
         test_param_noise_reduction.py \
         test_param_sensitivity.py \
         test_param_sensitivity_burst.py \
         test_param_tonemap_mode.py \
         test_yuv_plus_dng.py \
         test_yuv_plus_jpeg.py \
         test_yuv_plus_raw.py \

do
    let testcount=testcount+1
    echo ""
    echo "--------------------------------------------------------------------"
    echo "Running test: $T"
    echo "--------------------------------------------------------------------"
    python ../"$T" $REBOOT
    code=$?
    if [ $code -ne 0 ]; then
        let failcount=failcount+1
        echo ""
        echo "###############"
        echo "# Test failed #"
        echo "###############"
    fi
    echo ""
done

echo ""
echo "$failcount out of $testcount tests failed"
echo ""

cd ..

