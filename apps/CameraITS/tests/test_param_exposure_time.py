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

def main():
    """Test that the android.sensor.exposureTime parameter is applied.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    req = its.objects.capture_request( {
        "android.control.mode": 0,
        "android.control.aeMode": 0,
        "android.control.aeLock": False,
        "android.sensor.frameDuration": 0,
        "android.sensor.sensitivity": 100
        })

    exposures = range(20,120,20) # ms
    r_means = []
    g_means = []
    b_means = []

    with its.device.ItsSession() as cam:
        for e in exposures:
            req["captureRequest"]["android.sensor.exposureTime"] = e*1000*1000
            fname, w, h, cap_md = cam.do_capture(req)
            img = its.image.load_yuv420_to_rgb_image(fname, w, h)
            its.image.write_image(
                    img, "%s_time=%03dms.jpg" % (NAME, e))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            r_means.append(rgb_means[0])
            g_means.append(rgb_means[1])
            b_means.append(rgb_means[2])

    # Draw a plot.
    pylab.plot(exposures, r_means, 'r')
    pylab.plot(exposures, g_means, 'g')
    pylab.plot(exposures, b_means, 'b')
    matplotlib.pyplot.savefig("%s_plot_means.png" % (NAME))

if __name__ == '__main__':
    main()

