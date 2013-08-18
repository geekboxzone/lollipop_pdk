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
import os
import sys
import numpy
import Image
import math
import time
import os.path

def main():
    """Test that the android.tonemap.mode param is applied.

    Applies three different tonemap curves (linear, and clamped to zero and
    one), and saves the resultant images.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # TODO: Query the allowable tonemap curve sizes; here, it's hardcoded to
    # a length=32 list of tuples. The max allowed length should be inside the
    # camera properties object.
    TMLEN = 32
    TMSCALE = float(TMLEN-1)

    # Tonemap curves.
    tonemap_linear = sum([[i/TMSCALE, i/TMSCALE] for i in range(TMLEN)], [])
    tonemap_zero = sum([[i/TMSCALE, 0] for i in range(TMLEN)], [])
    tonemap_one = sum([[i/TMSCALE, 1] for i in range(TMLEN)], [])
    tonemaps = [tonemap_linear, tonemap_zero, tonemap_one]

    req = its.objects.capture_request( {
        "android.control.aeMode": 0,
        "android.sensor.sensitivity": 100,
        "android.sensor.exposureTime": 100*1000*1000,
        "android.sensor.frameDuration": 0
        })

    with its.device.ItsSession() as cam:
        for mode in [0,1,2]:
            for i, t in enumerate(tonemaps):
                req["captureRequest"]["android.tonemap.mode"] = mode
                req["captureRequest"]["android.tonemap.curveRed"] = t
                req["captureRequest"]["android.tonemap.curveGreen"] = t
                req["captureRequest"]["android.tonemap.curveBlue"] = t
                fname, w, h, md = cam.do_capture(req)
                img = its.image.load_yuv420_to_rgb_image(fname, w, h)
                its.image.write_image(
                        img, "%s_mode=%d_curve=%d.jpg" %(NAME, mode,i))

if __name__ == '__main__':
    main()

