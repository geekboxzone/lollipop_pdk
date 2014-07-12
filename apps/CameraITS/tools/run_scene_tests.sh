#!/bin/bash

# Copyright 2014 The Android Open Source Project
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

# -----------------------------------------------------------------------------
# The tests exercised in this file all assert/exit on failure, and terminate
# cleanly on success.

# -----------------------------------------------------------------------------
# Optionally, the device can be rebooted for each test, to ensure that
# a problem in one test doesn't propagate into subsequent tests; use this
# when debugging test failures, but not when running the test suite (since
# all tests should pass even when run back-to-back).
#REBOOT=reboot

# -----------------------------------------------------------------------------
# This script should be run from inside a tests/scene<N> directory.

if [ ! -f ../../tools/config.py ]
then
    echo "This script must be run from inside a tests/scene<N> directory."
    exit
fi

rm -rf out
mkdir -p out

cd out
python ../../../tools/config.py $REBOOT
cd ..

testcount=0
failcount=0

for T in *.py
do
    cd out
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
    cd ..
done

echo ""
echo "$failcount out of $testcount tests failed"
echo ""

