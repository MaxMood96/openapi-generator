# coding: utf-8

"""
    Echo Server API

    Echo Server API

    The version of the OpenAPI document: 0.1.0
    Contact: team@openapitools.org
    Generated by OpenAPI Generator (https://openapi-generator.tech)

    Do not edit the class manually.
"""  # noqa: E501


import unittest

from openapi_client.api.header_api import HeaderApi  # noqa: E501


class TestHeaderApi(unittest.TestCase):
    """HeaderApi unit test stubs"""

    def setUp(self) -> None:
        self.api = HeaderApi()

    def tearDown(self) -> None:
        self.api.api_client.close()

    def test_test_header_integer_boolean_string_enums(self) -> None:
        """Test case for test_header_integer_boolean_string_enums

        Test header parameter(s)  # noqa: E501
        """
        pass


if __name__ == '__main__':
    unittest.main()
