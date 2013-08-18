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
    """Test that a constant exposure is seen as ISO and exposure time vary.

    Take a series of shots that have ISO and exposure time chosen to balance
    each other; result should be the same brightness, but over the sequence
    the images should get noisier.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    req = its.objects.capture_request( {
        "android.control.aeMode": 0,
        "android.sensor.frameDuration": 0
        })

    mults = range(1, 16, 2)
    r_means = []
    g_means = []
    b_means = []

    with its.device.ItsSession() as cam:
        for m in mults:
            req["captureRequest"]["android.sensor.sensitivity"] = 100*m
            req["captureRequest"]["android.sensor.exposureTime"] = 100*1000*1000/m
            fname, w, h, md_obj = cam.do_capture(req)
            img = its.image.load_yuv420_to_rgb_image(fname, w, h)
            its.image.write_image(img, "%s_mult=%02d.jpg" % (NAME, m))
            tile = its.image.get_image_patch(img, 0.45, 0.45, 0.1, 0.1)
            rgb_means = its.image.compute_image_means(tile)
            r_means.append(rgb_means[0])
            g_means.append(rgb_means[1])
            b_means.append(rgb_means[2])

    # Draw a plot.
    pylab.plot(mults, r_means, 'r')
    pylab.plot(mults, g_means, 'g')
    pylab.plot(mults, b_means, 'b')
    matplotlib.pyplot.savefig("%s_plot_means.png" % (NAME))

if __name__ == '__main__':
    main()

