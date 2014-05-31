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
import its.dng
import its.objects

import numpy
import os.path

def main():
    """Test that the DNG tags are consistent.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # Capture two shots, one of a grey card illuminated by A, and the other D65.
    # In each case, wait for AE+AWB to converge first. Get the WB gains and ccm
    # reported by the HAL for each shot.
    #   gains <- android.colorCorrection.gains
    #   ccm <- android.colorCorrection.transform

    with its.device.ItsSession() as cam:
        print ""
        raw_input("[Point camera at a grey card illuminated by Standard A]")
        cam.do_3a(do_af=False)
        cap = cam.do_capture(its.objects.auto_capture_request())
        gains = cap["metadata"]["android.colorCorrection.gains"]
        ccm = its.objects.rational_to_float(
                cap["metadata"]["android.colorCorrection.transform"])
        cal = [1,0,0,0,1,0,0,0,1] # TODO: Use real values when plumbed.
        print "HAL reported gains:\n", numpy.array(gains)
        print "HAL reported ccm:\n", numpy.array([ccm[0:3],ccm[3:6],ccm[6:9]])

        cm_A, fm_A = its.dng.compute_cm_fm(its.dng.A, gains, ccm, cal)
        asn_A = its.dng.compute_asn(its.dng.A, cal, cm_A)
        print ""
        print "Expected values, based on provided gains and ccm for A:"
        print "A ColorMatrix:\n", cm_A
        print "A ForwardMatrix:\n", fm_A
        print "A AsShotNeutral:\n", asn_A

        print ""
        raw_input("[Point camera at a grey card illuminated by D65]")
        cam.do_3a(do_af=False)
        cap = cam.do_capture(its.objects.auto_capture_request())
        gains = cap["metadata"]["android.colorCorrection.gains"]
        ccm = its.objects.rational_to_float(
                cap["metadata"]["android.colorCorrection.transform"])
        cal = [1,0,0,0,1,0,0,0,1] # TODO: Use real values when plumbed.
        print "HAL reported gains:\n", numpy.array(gains)
        print "HAL reported ccm:\n", numpy.array([ccm[0:3],ccm[3:6],ccm[6:9]])

        cm_D65, fm_D65 = its.dng.compute_cm_fm(its.dng.D65, gains, ccm, cal)
        asn_D65 = its.dng.compute_asn(its.dng.D65, cal, cm_D65)
        print "\nExpected values, based on provided gains and ccm for D65:"
        print "D65 ColorMatrix:\n", cm_D65
        print "D65 ForwardMatrix:\n", fm_D65
        print "D65 AsShotNeutral:\n", asn_D65, "\n"

        # TODO: Query DNG matrices from HAL and compare to expected matrices.

if __name__ == '__main__':
    main()

