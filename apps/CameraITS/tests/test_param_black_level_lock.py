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

import its.image
import its.device
import its.objects
import pylab
import os.path
import matplotlib
import matplotlib.pyplot
import numpy

def main():
    """Test that the android.blackLevel.lock parameter has an effect.

    Take shots with varying sensor sensitivity, with and without the black
    level lock being set. Plot the resultant luma histogram of each shot.

    Shoot with the camera covered (i.e.) dark/black.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    req = its.objects.capture_request( {
        "android.control.aeMode": 0,
        "android.sensor.frameDuration": 0,
        "android.sensor.exposureTime": 10*1000*1000
        })

    with its.device.ItsSession() as cam:
        for b in [0,1]:
            for si, s in enumerate([3200, 800, 100]):
                req["captureRequest"]["android.blackLevel.lock"] = (b==1)
                req["captureRequest"]["android.sensor.sensitivity"] = s
                fname, w, h, cap_md = cam.do_capture(req)
                yimg,_,_ = its.image.load_yuv420_to_yuv_planes(fname, w, h)
                hist,_ = numpy.histogram(yimg*255, 256, (0,256))

                # Add this histogram to a plot; solid for shots without BL
                # lock, dashes for shots with BL lock
                pylab.plot(range(16), hist.tolist()[:16],
                           ['rgb'[si], 'k--'][b])

    pylab.xlabel("Luma DN, showing [0:16] out of full [0:256] range")
    pylab.ylabel("Pixel count")
    pylab.title("Histogram for different BL mode and sensitivity")
    matplotlib.pyplot.savefig("%s_plot_histograms.png" % (NAME))

if __name__ == '__main__':
    main()

