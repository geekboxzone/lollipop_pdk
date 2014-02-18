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
    """Test that the android.flash.mode parameter is applied.

    For this test to work correctly, the camera must be setup with a fixed
    test scene that meets the following requirements:
      - Fixed lighting.
      - Test scene will be illuminated by flash (i.e. it is not too distant).
      - Test scene is not too bright initially.
      - The flash unit on the camera is present and uncovered.

    This tests covers the following cases:
      - Checks that the camera captures an image in each flash mode.
      - Checks that the flash mode reported for each capture matches the flash
        mode used.
      - Checks that the image brightness increased for the modes where the
        flash is on.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # TODO: Get rid of hardcoded constants.
    OFF = 0
    SINGLE = 1
    TORCH = 2
    FLASH_MODES = [OFF, SINGLE, TORCH]

    # Minimum difference between flash on/off image means
    FLASH_MEAN_TOLERANCE = 0.2
    # Maximum initial mean for the flash off image
    FLASH_MAX_OFF_LEVEL = 0.8

    req = its.objects.auto_capture_request()

    flash_modes_reported = []
    flash_states_reported = []

    image_means = {}

    def list_gt(l1, l2):
        return not any(x <= y for x, y in zip(l1, l2))

    with its.device.ItsSession() as cam:
        for f in FLASH_MODES:
            req["android.flash.mode"] = f
            cap = cam.do_capture(req)
            flash_modes_reported.append(cap["metadata"]["android.flash.mode"])
            flash_states_reported.append(cap["metadata"]["android.flash.state"])
            img = its.image.convert_capture_to_rgb_image(cap)

            image_means[f] = its.image.compute_image_means(img)

            its.image.write_image(img, "%s_mode=%d.jpg" % (NAME, f))

    assert flash_modes_reported == FLASH_MODES, "Failed to capture for all" \
         " required flash modes."
    assert list_gt([FLASH_MAX_OFF_LEVEL] * len(FLASH_MODES), \
        image_means.get(OFF)), "OFF mode image is too bright (%f, %f, %f)," \
        " please use a darker scene." % tuple(image_means.get(OFF))
    off_means_with_tolerance = \
        [x + FLASH_MEAN_TOLERANCE for x in image_means.get(OFF)]
    assert list_gt(image_means.get(SINGLE), off_means_with_tolerance), \
            "Flash not on for SINGLE mode capture."
    assert list_gt(image_means.get(TORCH), off_means_with_tolerance), \
            "Flash not on for TORCH mode capture. TORCH means (%f, %f, %f)" \
            " too close to OFF means (%f, %f, %f)" \
            % tuple(image_means.get(TORCH) + image_means.get(OFF))

    # TODO: Add check on flash_states_reported values.

if __name__ == '__main__':
    main()

