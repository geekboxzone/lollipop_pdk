#
# Copyright (C) 2012 The Android Open Source Project
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
#

# build list for PDK1
# This build filters out most applications
# The outcome of this build is to create a binary release to allow chipset vendors
# to create a minimum UI image to bring up their H/W

# This file under pdk/build is the template for the same file under vendor/pdk_XYZ
# The file under vendor is automatically generated, and do not edit.

LOCAL_PATH := $(call my-dir)

