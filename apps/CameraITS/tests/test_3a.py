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

import its.device

def main():
    """Basic test for bring-up of 3A.

    Will be updated or removed once 3A is working. Simply calls the function to
    initiate the 3A intent, and exits. Watch logcat (once the script exits) to
    see how the 3A operation fared.
    """

    # TODO: Finish this test

    with its.device.ItsSession() as cam:
        full_rect = [0,0,1,1]
        cam.do_3a(full_rect, full_rect, full_rect)

if __name__ == '__main__':
    main()

