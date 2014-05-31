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
import pylab
import matplotlib
import matplotlib.pyplot

def main():
    """Measure jitter in camera timestamps.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        # ISO 100, 1ms manual exposure, VGA resolution YUV frames.
        req = its.objects.manual_capture_request(100, 1*1000*1000)
        fmt =  {"format":"yuv", "width":640, "height":480}

        caps = cam.do_capture([req]*50, [fmt])

        # Print out the millisecond delta between the start of each exposure
        tstamps = [c['metadata']['android.sensor.timestamp'] for c in caps]
        deltas = [tstamps[i]-tstamps[i-1] for i in range(1,len(tstamps))]
        deltas_ms = [d/1000000.0 for d in deltas]
        avg = sum(deltas_ms) / len(deltas_ms)
        var = sum([d*d for d in deltas_ms]) / len(deltas_ms) - avg * avg
        print "Average:", avg
        print "Variance:", var
        print "Jitter range:", min(deltas_ms) - avg, "to", max(deltas_ms) - avg

        # Draw a plot.
        pylab.plot(range(len(deltas_ms)), deltas_ms)
        matplotlib.pyplot.savefig("%s_deltas.png" % (NAME))

if __name__ == '__main__':
    main()

