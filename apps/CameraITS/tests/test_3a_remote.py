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
import os.path
import pprint
import math
import numpy
import matplotlib.pyplot
import mpl_toolkits.mplot3d

def main():
    """Run 3A remotely (from this script).
    """
    NAME = os.path.basename(__file__).split(".")[0]

    auto_req = its.objects.capture_request( {
        "android.control.mode": 1,
        "android.control.aeMode": 1,
        "android.control.awbMode": 1,
        "android.control.afMode": 1,
        "android.colorCorrection.mode": 1,
        "android.tonemap.mode": 1,
        "android.statistics.lensShadingMapMode":1
        })

    def r2f(r):
        return float(r["numerator"]) / float(r["denominator"])

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        w_map = props["android.lens.info.shadingMapSize"]["width"]
        h_map = props["android.lens.info.shadingMapSize"]["height"]

        # TODO: Test for 3A convergence, and exit this test once converged.

        while True:
            fname, w, h, md_obj = cam.do_capture(auto_req)
            cap_res = md_obj["captureResult"]

            ae_state = cap_res["android.control.aeState"]
            awb_state = cap_res["android.control.awbState"]
            af_state = cap_res["android.control.afState"]
            gains = cap_res["android.colorCorrection.gains"]
            transform = cap_res["android.colorCorrection.transform"]
            exp_time = cap_res['android.sensor.exposureTime']
            lsc_map = cap_res["android.statistics.lensShadingMap"]

            print "States (AE,AWB,AF):", ae_state, awb_state, af_state
            print "Gains:", gains
            print "Transform:", [r2f(t) for t in transform]
            print "AE region:", cap_res['android.control.aeRegions']
            print "AF region:", cap_res['android.control.afRegions']
            print "AWB region:", cap_res['android.control.awbRegions']
            print "LSC map:", w_map, h_map, lsc_map[:8]
            print ""

if __name__ == '__main__':
    main()

