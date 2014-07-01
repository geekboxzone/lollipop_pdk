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

import its.device
import pprint
import time

def main():
    """Basic test to query and print out sensor events.

    Test will only work if the screen is on (i.e.) the device isn't in standby.
    """

    with its.device.ItsSession() as cam:

        cam.start_sensor_events()

        print "Sleeping for 3s; move the camera to generate some events"
        time.sleep(3)

        events = cam.get_sensor_events()
        pprint.pprint(events)

if __name__ == '__main__':
    main()

