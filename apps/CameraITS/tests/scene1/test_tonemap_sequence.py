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
import os.path

def main():
    """Test a sequence of shots with different tonrmap curves.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:
        sens, exp_time, _,_,_ = cam.do_3a(do_af=False)

        # Capture 3 manual shots with a linear tonemap.
        req = its.objects.manual_capture_request(sens, exp_time, True)
        for i in [0,1,2]:
            cap = cam.do_capture(req)
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_i=%d.jpg" % (NAME, i))

        # Capture 3 manual shots with the default tonemap.
        req = its.objects.manual_capture_request(sens, exp_time, False)
        for i in [3,4,5]:
            cap = cam.do_capture(req)
            img = its.image.convert_capture_to_rgb_image(cap)
            its.image.write_image(img, "%s_i=%d.jpg" % (NAME, i))

        # TODO: Add pass/fail check.

if __name__ == '__main__':
    main()

