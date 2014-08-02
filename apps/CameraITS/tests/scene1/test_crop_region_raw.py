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

def main():
    """Test that raw streams are not croppable.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        a = props['android.sensor.info.activeArraySize']
        ax, ay = a["left"], a["top"]
        aw, ah = a["right"] - a["left"], a["bottom"] - a["top"]
        print "Active sensor region: (%d,%d %dx%d)" % (ax, ay, aw, ah)

        # Capture without a crop region.
        e, s = its.target.get_target_exposure_combos(cam)["minSensitivity"]
        req = its.objects.manual_capture_request(s,e)
        cap1_yuv, cap1_raw = cam.do_capture(req, cam.CAP_RAW_YUV)

        # Capture with a center crop region.
        req["android.scaler.cropRegion"] = {
                "top": ay + ah/3,
                "left": ax + aw/3,
                "right": ax + 2*aw/3,
                "bottom": ay + 2*ah/3}
        cap2_raw, cap2_yuv = cam.do_capture(req, cam.CAP_RAW_YUV)

        for s,cap in [("yuv_full",cap1_yuv), ("raw_full",cap1_raw),
                ("yuv_crop",cap2_yuv), ("raw_crop",cap2_raw)]:
            img = its.image.convert_capture_to_rgb_image(cap, props=props)
            its.image.write_image(img, "%s_%s.jpg" % (NAME, s))
            r = cap["metadata"]["android.scaler.cropRegion"]
            x, y = a["left"], a["top"]
            w, h = a["right"] - a["left"], a["bottom"] - a["top"]
            print "Crop on %s:  (%d,%d %dx%d)" % (s, x,y,w,h)

        # TODO: Add pass/fail test.
        # cap1_raw should match cap2_raw (full frame)
        # cap1_raw should match cap1_yuv (full frame)
        # cap2_yuv should actually be cropped

if __name__ == '__main__':
    main()

