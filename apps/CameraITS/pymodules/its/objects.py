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

import os
import os.path
import sys
import re
import json
import tempfile
import time
import unittest
import subprocess

def int_to_rational(i):
    """Function to convert Python integers to Camera2 rationals.

    Args:
        i: Python integer or list of integers.

    Returns:
        Python dictionary or list of dictionary representing the given int(s)
        as rationals with denominator=1.
    """
    if isinstance(i, list):
        return [{"numerator":val, "denominator":1} for val in i]
    else:
        return {"numerator":i, "denominator":1}

def capture_request(obj):
    """Function to wrap an object inside a captureRequest object.

    Args:
        obj: The Python dictionary object to wrap.

    Returns:
        The dictionary: {"captureRequest": obj}
    """
    return {"captureRequest": obj}

def capture_request_list(obj_list):
    """Function to wrap an object list inside a captureRequestList object.

    Args:
        obj_list: The list of Python dictionary objects to wrap.

    Returns:
        The dictionary: {"captureRequestList": obj_list}
    """
    return {"captureRequestList": obj_list}

class __UnitTest(unittest.TestCase):
    """Run a suite of unit tests on this module.
    """

    # TODO: Add more unit tests.

    def test_int_to_rational(self):
        """Unit test for int_to_rational.
        """
        self.assertEqual(int_to_rational(10),
                         {"numerator":10,"denominator":1})
        self.assertEqual(int_to_rational([1,2]),
                         [{"numerator":1,"denominator":1},
                          {"numerator":2,"denominator":1}])

if __name__ == '__main__':
    unittest.main()

