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
import its.target
import os.path
import math

def main():
    """Test manual WB and CCM both in isolation and together."
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()

        sens, exp, gains, xform, focus = cam.do_3a()
        xform_rat = its.objects.float_to_rational(xform)
        print "AE: sensitivity %d, exposure %dms" % (sens, exp/1000000.0)
        print "AWB: gains", gains, "transform", xform
        print "AF: distance", focus

        req = its.objects.manual_capture_request(sens, exp)
        cap = cam.do_capture(req)
        img = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(img, "%s_1.jpg" % (NAME))

        req = its.objects.manual_capture_request(sens, exp)
        req["android.colorCorrection.transform"] = xform_rat
        cap = cam.do_capture(req)
        img = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(img, "%s_2_awb_ccm.jpg" % (NAME))

        req = its.objects.manual_capture_request(sens, exp)
        xform_rat = its.objects.float_to_rational(xform)
        req["android.colorCorrection.gains"] = gains
        cap = cam.do_capture(req)
        img = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(img, "%s_3_awb_gains.jpg" % (NAME))

        req = its.objects.manual_capture_request(sens, exp)
        xform_rat = its.objects.float_to_rational(xform)
        req["android.colorCorrection.transform"] = xform_rat
        req["android.colorCorrection.gains"] = gains
        cap = cam.do_capture(req)
        img = its.image.convert_capture_to_rgb_image(cap)
        its.image.write_image(img, "%s_4_awb_gains_and_ccm.jpg" % (NAME))

        # Check that the gains+ccm returned by 3A match up with the values
        # returned by the capture result in the final shot (since these are
        # the values that were manually specified).
        xform_cr = its.objects.rational_to_float(
                cap["metadata"]["android.colorCorrection.transform"])
        gains_cr = cap["metadata"]["android.colorCorrection.gains"]
        assert(all([abs(xform[i]-xform_cr[i])<0.05 for i in range(9)]))
        assert(all([abs(gains[i]-gains_cr[i])<0.05 for i in range(4)]))

if __name__ == '__main__':
    main()

