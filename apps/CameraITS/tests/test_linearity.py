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
import sys
import numpy
import Image
import pprint
import math
import pylab
import os.path
import matplotlib
import matplotlib.pyplot

def main():
    """Test that device processing can be inverted to linear pixels.

    Captures a sequence of shots with the device pointed at a uniform
    target. Attempts to invert all the ISP processing to get back to
    linear R,G,B pixel data.

    TODO: Finish test_linearity.py.
    This test is still a work in progress.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # Set to true/false to enable/disable the saving of debug images.
    DEBUG_IMAGES = True

    # TODO: Get camera properties to find out max tonemap curve points;
    # hardcode to 64 here.
    gamma_lut = numpy.array(
            sum([[i/31.0, math.pow(i/31.0, 1/2.2)] for i in range(32)], []))
    inv_gamma_lut = numpy.array(
            sum([[i/31.0, math.pow(i/31.0, 2.2)] for i in range(32)], []))

    req = its.objects.capture_request( {
        "android.sensor.sensitivity": 100,
        "android.control.mode": 0,
        "android.control.aeMode": 0,

        #"android.tonemap.mode": 0,
        #"android.tonemap.curveRed": gamma_lut.tolist(),
        #"android.tonemap.curveGreen": gamma_lut.tolist(),
        #"android.tonemap.curveBlue": gamma_lut.tolist(),

        #"android.control.awbMode": 0,
        #"android.control.effectMode": 0,

        #"android.colorCorrection.mode": 0,
        #"android.colorCorrection.transform":
        #        its.objects.int_to_rational([1,0,0, 0,1,0, 0,0,1]),
        #"android.colorCorrection.gains": [1,1,1,1],

        "android.blackLevel.lock": True
        })

    exposures = range(50,500,50) # ms
    r_means = []
    g_means = []
    b_means = []

    with its.device.ItsSession() as cam:
        for e in exposures:
            # Capture a shot with the desired exposure duration.
            req["captureRequest"]["android.sensor.exposureTime"] = e*1000*1000
            fname, w, h, cap_md = cam.do_capture(req)

            # Steps to invert the ISP processing to get to linear RGB pixels
            # that are proportional to photon counts:

            # 1. YUV (gamma) -> RGB (gamma)
            img = its.image.load_yuv420_to_rgb_image(fname, w, h)
            if DEBUG_IMAGES:
                its.image.write_image(
                        img, "%s_debug=1_time=%03dms.jpg" % (NAME, e))

            # 2. RGB (gamma) -> RGB
            # Pass every second element (starting from element 1) to
            # the apply_lut_to_image function.
            img = its.image.apply_lut_to_image(img, inv_gamma_lut[1::2])
            if DEBUG_IMAGES:
                its.image.write_image(
                        img, "%s_debug=2_time=%03dms.jpg" % (NAME, e))

            # 3. Invert LSC
            # TODO: Finish this test.
            # ...

            # Get the average of R,G,B in a center patch of the image.
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            if DEBUG_IMAGES:
                its.image.write_image(
                        tile, "%s_debug=4_time=%03dms.jpg" % (NAME, e),True)
            rgb_means = its.image.compute_image_means(tile)

            # Record the patch intensities against the exposure duration
            r_means.append(rgb_means[0])
            g_means.append(rgb_means[1])
            b_means.append(rgb_means[2])

    # Draw a plot.
    pylab.plot(exposures,r_means, 'r')
    pylab.plot(exposures,g_means, 'g')
    pylab.plot(exposures,b_means, 'b')
    matplotlib.pyplot.savefig("%s_plot_means.png" % (NAME))

if __name__ == '__main__':
    main()

