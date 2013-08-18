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

def main():
    """Test that the android.colorCorrection.* params are applied when set.

    Takes shots with different transform and gains values, and tests that
    they look correspondingly different.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # Capture requests:
    # 1. With unit gains, and identity transform.
    # 2. With a higher red gain, and identity transform.
    # 3. With unit gains, and a transform that boosts blue.
    reqs = its.objects.capture_request_list( [
        {
            "android.colorCorrection.mode": 0,
            "android.colorCorrection.transform":
                    its.objects.int_to_rational([1,0,0, 0,1,0, 0,0,1]),
            "android.colorCorrection.gains": [1,1,1,1]
        },
        {
            "android.colorCorrection.mode": 0,
            "android.colorCorrection.transform":
                    its.objects.int_to_rational([1,0,0, 0,1,0, 0,0,1]),
            "android.colorCorrection.gains": [2,1,1,1]
        },
        {
            "android.colorCorrection.mode": 0,
            "android.colorCorrection.transform":
                    its.objects.int_to_rational([1,0,0, 0,1,0, 0,0,2]),
            "android.colorCorrection.gains": [1,1,1,1]
        }
        ])

    with its.device.ItsSession() as cam:
        fnames, w, h, md_objs = cam.do_capture(reqs)

        # For each capture, print out the average RGB value of the center patch
        # of the image.
        for fname in fnames:
            img = its.image.load_yuv420_to_rgb_image(fname, w, h)
            img = its.image.apply_lut_to_image(img,
                    its.image.DEFAULT_INVGAMMA_LUT)
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            print rgb_means

        # TODO: Finish this test.

if __name__ == '__main__':
    main()

