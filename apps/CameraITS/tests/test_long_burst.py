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

import its.image
import its.device
import its.objects
import os.path

def main():
    """Test that a long burst of images can be captured.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # The ItsService code can't buffer shots indefinitely since it captures
    # much more quickly than the shots are uploaded over the TCP connection.
    # Limit a "long" burst to 12 shots, here.
    NUM_BURST_SHOTS = 12

    with its.device.ItsSession() as cam:
        # Capture shots at ISO 100 and exposure time 33.3333ms
        req = its.objects.manual_capture_request(100, 33333333)
        caps = cam.do_capture([req]*NUM_BURST_SHOTS)

        # Print out the millisecond delta between the start of each exposure
        tstamps = [c['metadata']['android.sensor.timestamp'] for c in caps]
        deltas = [tstamps[i]-tstamps[i-1] for i in range(1,len(tstamps))]
        deltas_ms = [d/1000000.0 for d in deltas]
        print deltas_ms

if __name__ == '__main__':
    main()

