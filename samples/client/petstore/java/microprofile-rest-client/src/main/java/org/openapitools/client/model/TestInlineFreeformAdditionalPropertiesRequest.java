/**
 * OpenAPI Petstore
 * This spec is mainly for testing Petstore server and contains fake endpoints, models. Please do not use this for any other purpose. Special characters: \" \\
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package org.openapitools.client.model;

import java.util.Objects;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;
import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbCreator;


public class TestInlineFreeformAdditionalPropertiesRequest extends HashMap<String, Object> {
  
  @JsonbProperty("someProperty")
  private String someProperty;

  /**
   * Get someProperty
   * @return someProperty
   **/
  public String getSomeProperty() {
    return someProperty;
  }

  /**
   * Set someProperty
   */
  public void setSomeProperty(String someProperty) {
    this.someProperty = someProperty;
  }

  public TestInlineFreeformAdditionalPropertiesRequest someProperty(String someProperty) {
    this.someProperty = someProperty;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestInlineFreeformAdditionalPropertiesRequest testInlineFreeformAdditionalPropertiesRequest = (TestInlineFreeformAdditionalPropertiesRequest) o;
    return Objects.equals(this.someProperty, testInlineFreeformAdditionalPropertiesRequest.someProperty) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(someProperty, super.hashCode());
  }

  /**
   * Create a string representation of this pojo.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TestInlineFreeformAdditionalPropertiesRequest {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    someProperty: ").append(toIndentedString(someProperty)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private static String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

