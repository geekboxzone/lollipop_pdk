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
    """Test that the device will produce full black+white images.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    req = its.objects.capture_request( {
        "android.sensor.exposureTime": 10*1000, # 0.01ms
        "android.sensor.sensitivity": 100,
        "android.sensor.frameDuration": 0,
        "android.control.mode": 0,
        "android.control.aeMode": 0,
        "android.control.awbMode": 0,
        "android.control.afMode": 0,
        "android.control.effectMode": 0,
        })

    with its.device.ItsSession() as cam:
        # Take a shot with very low ISO and exposure time. Expect it to
        # be black.
        fname, w, h, cap_md = cam.do_capture(req)
        img = its.image.load_yuv420_to_rgb_image(fname, w, h)
        its.image.write_image(img, "%s_black.jpg" % (NAME))
        tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
        black_means = its.image.compute_image_means(tile)
        print "Dark pixel means:", black_means

        # Take a shot with very high ISO and exposure time. Expect it to
        # be black.
        req["captureRequest"]["android.sensor.sensitivity"] = 10000
        req["captureRequest"]["android.sensor.exposureTime"] = 1000*1000*1000
        fname, w, h, cap_md = cam.do_capture(req)
        img = its.image.load_yuv420_to_rgb_image(fname, w, h)
        its.image.write_image(img, "%s_white.jpg" % (NAME))
        tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
        white_means = its.image.compute_image_means(tile)
        print "Bright pixel means:", white_means

        for val in black_means:
            assert(val < 0.025)
        for val in white_means:
            assert(val > 0.975)

if __name__ == '__main__':
    main()

