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
    """Test capturing a single frame as both YUV and JPEG outputs.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    req = its.objects.auto_capture_request()

    # Hard-code a preview (VGA) size for the YUV image.
    # TODO: Replace this with code to select sizes from what's available.
    fmt_yuv =  {"format":"yuv", "width":640, "height":480}
    fmt_jpeg = {"format":"jpeg"}

    with its.device.ItsSession() as cam:
        cam.do_3a();
        cap_yuv, cap_jpeg = cam.do_capture(req, [fmt_yuv, fmt_jpeg])

        img = its.image.convert_capture_to_rgb_image(cap_yuv)
        its.image.write_image(img, "%s_yuv.jpg" % (NAME))

        img = its.image.convert_capture_to_rgb_image(cap_jpeg)
        its.image.write_image(img, "%s_jpeg.jpg" % (NAME))

if __name__ == '__main__':
    main()

