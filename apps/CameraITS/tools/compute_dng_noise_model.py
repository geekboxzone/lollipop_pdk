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

import its.device
import its.objects
import its.image
import pprint
import pylab
import os.path
import matplotlib
import matplotlib.pyplot
import numpy
import math

def main():
    """Compute the DNG noise model from a color checker chart.

    TODO: Make this more robust; some manual futzing may be needed.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    with its.device.ItsSession() as cam:

        props = cam.get_camera_properties()

        white_level = float(props['android.sensor.info.whiteLevel'])
        black_levels = props['android.sensor.blackLevelPattern']
        idxs = its.image.get_canonical_cfa_order(props)
        black_levels = [black_levels[i] for i in idxs]

        # Expose for the scene with min sensitivity
        sens_min, sens_max = props['android.sensor.info.sensitivityRange']
        s_ae,e_ae,awb_gains,awb_ccm,_  = cam.do_3a()
        s_e_prod = s_ae * e_ae

        # Make the image brighter since the script looks at linear Bayer
        # raw patches rather than gamma-encoded YUV patches (and the AE
        # probably under-exposes a little for this use-case).
        s_e_prod *= 2

        # Capture raw frames across the full sensitivity range.
        SENSITIVITY_STEP = 400
        reqs = []
        sens = []
        for s in range(sens_min, sens_max, SENSITIVITY_STEP):
            e = int(s_e_prod / float(s))
            req = its.objects.manual_capture_request(s, e)
            req["android.colorCorrection.transform"] = \
                    its.objects.float_to_rational(awb_ccm)
            req["android.colorCorrection.gains"] = awb_gains
            reqs.append(req)
            sens.append(s)

        caps = cam.do_capture(reqs, cam.CAP_RAW)

        # A list of the (x,y) coords of the top-left pixel of a collection of
        # 64x64 pixel patches of a color checker chart. Each patch should be
        # uniform, however the actual color doesn't matter.
        img = its.image.convert_capture_to_rgb_image(caps[0], props=props)
        (x0,y0),(dxh,dyh),(dxv,dyv) = \
                its.image.get_color_checker_chart_patches(img, NAME+"_debug")
        patches = []
        for xi in range(6):
            for yi in range(4):
                xc = int(x0 + dxh*xi + dxv*yi)
                yc = int(y0 + dyh*xi + dyv*yi)
                patches.append((xc-32,yc-32))

        lines = []
        for (s,cap) in zip(sens,caps):
            # For each capture, compute the mean value in each patch, for each
            # Bayer plane; discard patches where pixels are close to clamped.
            # Also compute the variance.
            CLAMP_THRESH = 0.2
            planes = its.image.convert_capture_to_planes(cap, props)
            points = []
            for i,plane in enumerate(planes):
                plane = (plane * white_level - black_levels[i]) / (
                        white_level - black_levels[i])
                for (x,y) in patches:
                    tile = plane[y/2:y/2+32,x/2:x/2+32,:]
                    mean = its.image.compute_image_means(tile)[0]
                    var = its.image.compute_image_variances(tile)[0]
                    if (mean > CLAMP_THRESH and mean < 1.0-CLAMP_THRESH):
                        # Each point is a (mean,variance) tuple for a patch;
                        # for a given ISO, there should be a linear
                        # relationship between these values.
                        points.append((mean,var))

            # Fit a line to the points, with a line equation: y = mx + b.
            # This line is the relationship between mean and variance (i.e.)
            # between signal level and noise, for this particular sensor.
            # In the DNG noise model, the gradient (m) is "S", and the offset
            # (b) is "O".
            points.sort()
            xs = [x for (x,y) in points]
            ys = [y for (x,y) in points]
            m,b = numpy.polyfit(xs, ys, 1)
            lines.append((s,m,b))
            print s, "->", m, b

            # Some sanity checks:
            # * Noise levels should increase with brightness.
            # * Extrapolating to a black image, the noise should be positive.
            # Basically, the "b" value should correspnd to the read noise,
            # which is the noise level if the sensor was operating in zero
            # light.
            #assert(m > 0)
            #assert(b >= 0)

            # Draw a plot.
            pylab.plot(xs, ys, 'r')
            pylab.plot([0,xs[-1]],[b,m*xs[-1]+b],'b')
            matplotlib.pyplot.savefig("%s_plot_mean_vs_variance.png" % (NAME))

        # Now fit a line across the (m,b) line parameters for each sensitivity.
        # The gradient (m) params are fit to the "S" line, and the offset (b)
        # params are fit to the "O" line, both as a function of sensitivity.
        gains = [d[0] for d in lines]
        Ss = [d[1] for d in lines]
        Os = [d[2] for d in lines]
        mS,bS = numpy.polyfit(gains, Ss, 1)
        mO,bO = numpy.polyfit(gains, Os, 1)

        # Plot curve "O" as 10x, so it fits in the same scale as curve "S".
        pylab.plot(gains, [10*o for o in Os], 'r')
        pylab.plot([gains[0],gains[-1]],
                [10*mO*gains[0]+10*bO, 10*mO*gains[-1]+10*bO], 'b')
        pylab.plot(gains, Ss, 'r')
        pylab.plot([gains[0],gains[-1]], [mS*gains[0]+bS, mS*gains[-1]+bS], 'b')
        matplotlib.pyplot.savefig("%s_plot_S_O.png" % (NAME))

        print """
        /* Generated test code to dump a table of data for external validation
         * of the noise model parameters.
         */
        #include <stdio.h>
        #include <assert.h>
        void compute_noise_model_entries(int sens, double *o, double *s);
        int main(void) {
            int sens;
            double o, s;
            for (sens = %d; sens <= %d; sens += 100) {
                compute_noise_model_entries(sens, &o, &s);
                printf("%%d,%%lf,%%lf\\n", sens, o, s);
            }
            return 0;
        }

        /* Generated function to map a given sensitivity to the O and S noise
         * model parameters in the DNG noise model.
         */
        void compute_noise_model_entries(int sens, double *o, double *s) {
            assert(sens >= %d && sens <= %d && o && s);
            *s = %e * sens + %e;
            *o = %e * sens + %e;
            *s = *s < 0.0 ? 0.0 : *s;
            *o = *o < 0.0 ? 0.0 : *o;
        }
        """%(sens_min,sens_max,sens_min,sens_max,mS,bS,mO,bO)

if __name__ == '__main__':
    main()

