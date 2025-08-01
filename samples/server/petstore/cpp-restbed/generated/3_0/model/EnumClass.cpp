/**
 * OpenAPI Petstore
 * This spec is mainly for testing Petstore server and contains fake endpoints, models. Please do not use this for any other purpose. Special characters: \" \\
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI-Generator 7.15.0-SNAPSHOT.
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */



#include "EnumClass.h"

#include <string>
#include <vector>
#include <map>
#include <sstream>
#include <stdexcept>
#include <regex>
#include <boost/lexical_cast.hpp>
#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/json_parser.hpp>
#include "helpers.h"

using boost::property_tree::ptree;
using boost::property_tree::read_json;
using boost::property_tree::write_json;

namespace org {
namespace openapitools {
namespace server {
namespace model {

EnumClass::EnumClass(boost::property_tree::ptree const& pt)
{
        fromPropertyTree(pt);
}


std::string EnumClass::toJsonString(bool prettyJson /* = false */) const
{
	std::stringstream ss;
	write_json(ss, this->toPropertyTree(), prettyJson);
    // workaround inspired by: https://stackoverflow.com/a/56395440
    std::regex reg("\\\"([0-9]+\\.{0,1}[0-9]*)\\\"");
    std::string result = std::regex_replace(ss.str(), reg, "$1");
    return result;
}

void EnumClass::fromJsonString(std::string const& jsonString)
{
	std::stringstream ss(jsonString);
	ptree pt;
	read_json(ss,pt);
	this->fromPropertyTree(pt);
}

ptree EnumClass::toPropertyTree() const
{
	ptree pt;
	ptree tmp_node;
	return pt;
}

void EnumClass::fromPropertyTree(ptree const &pt)
{
	ptree tmp_node;
}

std::string EnumClass::toString() const {
    return boost::lexical_cast<std::string>(getEnumValue());
}

void EnumClass::fromString(const std::string& str) {
    setEnumValue(boost::lexical_cast<std::string>(str));
}

std::string EnumClass::getEnumValue() const {
    return m_EnumClassEnumValue;
}

void EnumClass::setEnumValue(const std::string& val) {
    static const std::array<std::string, 3> allowedValues = {
        "_abc", "-efg", "(xyz)"
    };
    if (std::find(allowedValues.begin(), allowedValues.end(), val) != allowedValues.end()) {
        m_EnumClassEnumValue = val;
    } else {
        throw std::runtime_error("Value " + boost::lexical_cast<std::string>(val) + " not allowed");
    }
}

std::vector<EnumClass> createEnumClassVectorFromJsonString(const std::string& json)
{
    std::stringstream sstream(json);
    boost::property_tree::ptree pt;
    boost::property_tree::json_parser::read_json(sstream,pt);

    auto vec = std::vector<EnumClass>();
    for (const auto& child: pt) {
        vec.emplace_back(EnumClass(child.second));
    }

    return vec;
}

}
}
}
}

